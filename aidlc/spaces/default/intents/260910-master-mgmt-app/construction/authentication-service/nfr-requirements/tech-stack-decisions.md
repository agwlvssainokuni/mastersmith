# Tech Stack Decisions — authentication-service (U5)

基本の技術スタックはFeasibilityステージ(2026-09-10)・practices-discoveryで確定済み(`team.md`参照)であり、本ユニット固有の選定は次の表のとおりである。他ユニット(user-management・permission-engine・audit-loggingなど)が採用済みの技術(Spring Data、Flyway、HikariCP、Micrometer、Caffeine)は、そのまま踏襲する。

| 領域 | 選択 | 根拠 |
|---|---|---|
| 言語・フレームワーク・ビルド | Java 25 + Spring Boot + Gradle | プロジェクト全体で確定済み |
| 認証の基盤 | Spring Securityのフィルタチェーン。JWTの署名・検証は、Spring Securityが用いるNimbus JOSE + JWTを想定する。許可する署名方式をHS256だけに限定する | Q1=A。他ユニット(schema-introspectorのNFR設計など)が、「Spring SecurityのフィルタチェーンでBearer JWTを検証する」前提で設計済みである。ライブラリの最終確認はCode Generationで行う([assumption]) |
| JWTの署名方式・鍵 | HS256(共通鍵)。256ビット以上の鍵を環境変数などから注入する。鍵の入れ替えは、差し替えて再起動する | Q1=A(NFR2.2) |
| リフレッシュトークン | JDK標準の`SecureRandom`で256ビットの乱数を生成し、Base64URLにする。保存は、JDK標準の`MessageDigest`によるSHA-256 | Q2=A(NFR2.3)。追加の依存は要らない |
| パスワードの検証 | user-managementのC11(`verifyPasswordHash`・`dummyVerify`)に委ねる。本ユニットは、ハッシュの実装(Argon2id)を持たない | user-managementで確定済み(Argon2idの`Argon2PasswordEncoder`)。ハッシュ計算の同時実行の上限も、そちらのものを共有する(NFR1.3) |
| データストア | 内部設定DB(組込みDB、例: H2)、業務データ用RDBMSとは別接続。Session・AccountLoginStateのテーブルを、Flywayの移行スクリプトで作成し、索引を明示的に定義する | team.md Q12b。他ユニットと同じ方式(Flywayは、audit-loggingで導入済み) |
| データアクセス | Spring DataのJPAまたはJDBC(パラメータバインディングのみ。文字列結合によるクエリを作らない)。予約・補償・リフレッシュ・失効の各更新は、条件付きの更新(更新の件数で成否を判定)で書く | 他ユニットと同じ方式。原子性は、NFR4.1に従う。具体的な実装(JPA・JDBCの使い分け)は、Code Generationで確定する |
| `AccountLoginState`の作成(なければ作成する更新) | 移植性のある、条件付きの更新と、一意制約(`userId`)による作成を組み合わせる。同時の作成で一意制約に違反した側は、更新をやり直す | 内部設定DBは組込みDB(例: H2)であり、DB固有のupsert構文に依存しないため([assumption]。具体的な方式はNFR Design・Code Generationで確定する) |
| Sessionのキャッシュ | プロセス内キャッシュ(Caffeineを想定)。追加のミドルウェア(Redis等)は導入しない | permission-engineが、Caffeineによる短いTTLのキャッシュを採用している。有効期間・容量・無効化は、NFR Design(3.3)で確定する(NFR3.2・NFR4.3) |
| 定期削除 | Springの標準の定期実行の仕組み(`@Scheduled`) | Q4=A(NFR4.5)。追加のミドルウェアは要らない |
| 設定の読み込みと検証 | Spring Bootの設定のバインドと、Bean Validation。不備は起動時に失敗させる | project.md Mandated(NFR4.4) |
| 時刻 | `java.time.Clock`を注入し、テストで差し替える | NFR4.6 |
| メトリクス・トレーシング | Micrometer(Spring Boot標準)で計装し、NFR5.1・NFR5.3に従いOTEL基盤へエクスポートする(具体的なエクスポートの設定は、CI PipelineおよびNFR設計で、他ユニットと共通に確定) | プロジェクト全体の可観測性方針に従う |

## 決定: 追加ミドルウェアを導入しない

Sessionの保持・キャッシュ・定期削除は、内部設定DBとJVM内のキャッシュ・Springの標準の仕組みで行う。外部のセッションストア・分散キャッシュ(Redis等)・メッセージキューは、本MVPスコープ(数十名規模、単一プロセス)では導入しない。複数プロセス構成への拡張は、NFR3.5のとおり、全体設計に委ねる。

## 確認事項(Code Generationの計画承認までに確認する)

本ステージでは確定しておらず、Code Generationの計画承認までに、ユーザーへ確認する事項を、次のとおり記録する。

1. **初回ログイン後のパスワード変更の手段**: user-managementのNFRから、authentication-serviceの機能設計での確認事項として引き継がれたが、要件(FR2〜FR4)に、パスワードの変更・再設定の記述がなく、機能設計でも扱っていない。MVPでは、招待受諾でパスワードを設定したあと、利用者が自分のパスワードを変更する手段がない(初期管理者も同様)。パスワードを変更できる機能を要件に加えるか、MVPの対象外と明示するかの、判断が必要である(スコープの判断であり、非機能要件の範囲を超える)。
2. **セキュリティヘッダー(`Referrer-Policy`・`Content-Security-Policy`など)の担い手**: user-managementのNFR Design(security-design.md)は、共通基盤(認証フィルタチェーンを含む、全体の構成)が所有するとして、要求を記録している。本ユニットのCode Generationが、Spring Securityのフィルタチェーンの設定を実装するため、そこに含めるか、別の共通基盤の作業とするかを、計画承認で確認する(NFR2.9・NFR2.11の緩和策がこれに依存する)。
3. **認証の基盤のライブラリ**: Spring SecurityのOAuth2 Resource Server(Nimbus JOSE + JWT)を用いるか、認証フィルタを自前で実装して、Nimbus JOSE + JWTを直接使うかを、Code Generationの計画で確定する。どちらの場合も、署名方式をHS256だけに限定し、`sub`とSessionの`userId`の照合(NFR2.2)を行えること。
4. **内部設定DBの永続の設定**: 内部設定DBが、再起動で内容が失われる構成(メモリのみ)か、ファイルに永続化する構成かを、確認する(NFR4.7)。メモリのみの場合は、再起動のたびに、すべての利用者が再ログインになる。
5. **環境側の前提**(運用フェーズが本MVPスコープ外のため、担当が定まるまでは、環境側の前提として記録しておく): (a)本番相当の環境で、HTTPSを必須とする(NFR2.9)。(b)サーバーの時刻の同期(NTPなど)が取られている(NFR4.6)。(c)内部設定DBを過去の時点から復元したときは、Sessionをすべて削除する(NFR4.7)。(d)リバースプロキシを使う場合の、`Authorization`ヘッダーとリクエストボディをログに出さない設定(NFR2.7)。

## NFR7.1: 認証エラーの文言のi18n

要件のFR10.1(表示名・バリデーションメッセージをi18nキーで多言語対応可能な構造にする)に従い、本ユニットのAPIが返すエラー(401・403・413・503)は、次のとおりに扱う([assumption]。user-managementのNFR7.2と同じ方針であり、要件に明示はない)。

- サーバーは、エラーの種別を、安定したi18nキーで表す。文言への変換(翻訳)は、フロントエンド(frontend-ui, U12)が、config-engineが管理する翻訳リソースを用いて行う。
- ログイン失敗の401は、原因を区別しない、単一のキーとする(rules.md BR5.2)。原因ごとの文言に分けない。
- キーの命名は、契約追補(保留)として、下記の一覧に記録する。
- サーバーの応答には、パスワード・トークンなどの入力値そのものを含めない(NFR2.7)。

## 契約追補(保留事項の一覧)

本ステージ(NFR Requirements)で判明した、契約(C4・C11)・機能設計・他ユニットへの追補を、次のとおり一覧にする。機能設計(functional-spec.md)の追補一覧(1〜12番)とは別の、本ステージで加わる追補である。機能設計・契約設計は完了済みのため、いずれも、Code Generationの計画承認までに反映する保留の追補として扱う(機能設計の見直しか、追補で済ませるかは、その時点で判断する)。

| 番号 | 対象 | 内容 | 出典 |
|---|---|---|---|
| 1 | C4 の全エンドポイントと、認証フィルタ(認証を必要とするすべてのAPI) | 内部設定DBの障害を表す503(RFC 9457のProblemDetails)の追加。`contract-summary.md`の「前提(全契約に共通)」の認証の記述にも、認証フィルタが503を返すことを、加法的に追記する(他ユニットのREST契約C1〜C3・C5〜C8のレスポンスに、個別に追加はしない) | Q5=A、NFR4.2 |
| 2 | C4 ログイン・リフレッシュ | 応答に`Cache-Control: no-store`を付ける(加法的な追補) | NFR2.9([assumption]) |
| 3 | C4 の全エンドポイント | 413(リクエストボディが64KiBを超えた場合)の追加 | NFR2.10([assumption]) |
| 4 | C11 `dummyVerify` | 129文字以上のパスワードは、ハッシュ計算をせず、`verifyPasswordHash`と同じ扱いにする(未登録のメールアドレスにだけ、長いパスワードで応答時間の差が生じないようにする)。user-management(U4)のコードへの、加法的な追補であり、機能設計の追補11番のとおり、本ユニットのCode Generationの範囲に含めて実装する | NFR2.4([assumption]) |
| 5 | 機能設計 BR5.11(認証フィルタ) | 認証フィルタが、トークンの`sub`と、`sid`のSessionの`userId`の一致を確認し、一致しなければ401を返す | NFR2.2([assumption]) |
| 6 | 機能設計 W1・BR5.3(ログインの成功) | `AccountLoginState`のリセットとSessionの作成を、同一の短いトランザクションで行う | NFR4.1([assumption]) |
| 7 | C4 ProblemDetails | エラーの種別を表すi18nキーの命名 | NFR7.1 |
| 8 | frontend-ui(U12)への要求 | 401でリフレッシュして元のリクエストを再試行する(鍵の入れ替えからの復旧の前提)。503を認証の失敗と区別し、セッションを終了せずに再試行を促す。トークンをURL・ログ・外部へ送る情報に含めない。アクセストークンはメモリに保持する。機能設計の追補7の(a)〜(e)に加える | NFR2.2・NFR2.11・Q5=A |
| 9 | 共通基盤(セキュリティヘッダー) | `Referrer-Policy`・`Content-Security-Policy`などの担い手の確認(上記の確認事項2) | NFR2.9 |

## NFR8.1: 保守性(業務固有情報のハードコード禁止)

authentication-serviceは、Session・AccountLoginState・AccessTokenClaims・Operatorのみを扱い、業務固有のテーブル名・カラム名・業務ルールをコードに持たない(要件のNFR8・FR1.6、project.md Mandated)。`Operator`と`OperatorContext`(C15)は、authentication-serviceの業務ロジックに依存しない、中立の共有契約とする(rules.md BR5.12)。ロックのしきい値・ロック時間・有効期限・猶予・削除の保持日数は、コードに埋め込まず、`application.yml`で外部に持つ(NFR4.4)。

## NFR8.2: テスト要件

- team.mdの既定に従い、**80%行カバレッジ**をCIでのマージ前実行で確保する。
- **権限判定の追加条件**: 本ユニットは、permission-engine(C10)の、`activeRoleId`がnullまたは空のときの扱い(fail closedでNONE、RBAC設定が空の間の`config-import-export`の例外は`activeRoleId`にかかわらず適用)を変更する(rules.md BR5.12)。権限判定ロジックの変更にあたるため、team.mdの追加の合格条件に従い、`canAccessScreen`・`resolveEffectivePermission`の、`activeRoleId`(null・空・実在するロール)×RBAC設定の有無×画面の組み合わせを網羅する、テーブル駆動テストを、既存の権限マトリクスのテストに加えて用意する。権限判定の実装に先立って、組み合わせを洗い出す(team.mdのTesting Posture)。
- **認可拒否(negative-authorization)専用テスト**(team.mdの必須テスト種別(c))を、次の観点で用意する。
  - トークンなし・有効期限切れ・署名の不正・`alg: none`・`alg`がHS256以外・必須の値が欠けたトークン・`sub`とSessionの`userId`が一致しないトークンが、いずれも、同一の401になること(NFR2.2・NFR2.8)。
  - 失効したSession(ログアウト・盗用の検知・ユーザーの無効化の検知)のアクセストークンが401になること(NFR2.5)。
  - 保持していないロールの選択が403になり、Sessionが変わらないこと(NFR2.6)。
  - アクティブロールが未選択(null)の操作者が、401ではなく、権限なし(403)として扱われること(NFR2.1)。
  - リクエストヘッダー(`X-User-Id`・`X-Active-Role-Id`)を付けても、操作者が変わらない(無視される)こと。ヘッダーを信頼する暫定の実装が、コードのどこにも残っていないこと(機能設計Q9=A、W7)。
- **安全失敗のテスト**(team.mdの必須テスト種別(a)): NFR4.4の表の各設定の不備(鍵の未設定・256ビット未満、有効期限・ロックのしきい値・ロック時間・削除の設定の不正、リフレッシュトークンの有効期限がアクセストークンより短い)が、起動時にfail fastすること。エラーのメッセージに、鍵の値が含まれないこと。
- **並行実行・境界のテスト**(時計を差し替えて行う。NFR4.6):
  - ロックの予約: 同時の誤ったパスワードの試行が何件あっても、検証できる試行がしきい値を超えないこと。しきい値に達する予約と同時にロックが有効になること。ロックの自動解除のあとの最初の予約で、回数が0に戻ること。正しいパスワードの成功で、しきい値に達した試行でも、ロックが解けること(BR5.3)。
  - 補償の更新: ハッシュ計算の上限超過の503で、枠が返ること。`generation`が変わっていれば、何もしないこと。
  - リフレッシュ: 同一のリフレッシュトークンの同時の更新で、1件だけが成功し、負けた側はSessionを失効させないこと。猶予内の再使用は401だけで、猶予を超えた再使用はSessionを失効させること。ローテーション後の旧トークンが、猶予を超えたあとに、盗用として扱われること(BR5.6)。
  - ログアウトが、該当のSessionだけを失効させ、同一ユーザーの他のSessionに影響しないこと(BR5.8)。
  - 成功の更新(`AccountLoginState`のリセット)とSessionの作成が、一体で反映されること(NFR4.1)。
- **内部設定DBの障害のテスト**(Q5=A): DBが使えないとき、認証フィルタ(キャッシュミス)・ログイン・リフレッシュ・ログアウト・ロール選択が、401ではなく503になること。キャッシュに有効なSessionがあるときは、認証フィルタが判定できること。リフレッシュで、更新が完了しなかったとき、トークンが更新されないこと(NFR4.2)。
- **セキュリティ要件を検証するテスト**(team.mdの必須テスト種別(c)の趣旨を、認可以外にも広げたもの)。
  - パスワード・アクセストークン・リフレッシュトークン(平文とハッシュ)・署名の鍵が、レスポンス(必要なもの以外)・ログ・トレースのスパン・メトリクスのラベルに現れないこと(NFR2.7)。
  - ログイン失敗が、原因(未登録・パスワードの誤り・ロック中・無効化済み・招待中)にかかわらず、同一の401の内容になること。未登録・招待中・無効化済み・ロック中で、`dummyVerify`が呼ばれること(BR5.2)。応答時間の差の検証は、環境に依存して不安定になりやすいため、`dummyVerify`が呼ばれることの確認にとどめる([assumption])。
  - ハッシュ計算の上限超過が、実際の検証・ダミーの検証のどちらでも、同じ503になること(BR5.15)。
  - 64KiBを超えるリクエストボディが413になること(NFR2.10)。
  - 鍵の入れ替え(異なる鍵で署名されたトークン)が、401になり、リフレッシュで復旧すること(NFR2.2)。
- **Sessionの定期削除のテスト**: 有効期限から保持日数を過ぎたSession(revokedを含む)だけが削除され、有効なSessionは、削除されないこと。1回の削除の行数の上限のもとで、複数回に分けて削除されること。削除の失敗が、認証に影響しないこと(NFR4.5)。
- **統合テスト・画面操作を通したE2Eテスト**(team.mdのTest Strategy「Comprehensive」): ログイン → ロール選択(複数ロールのユーザー)→ 一覧・編集画面での権限制御 → ログアウトの一連の流れを、認証フィルタを通して確認する。**複数プロファイル横断E2Eテスト**(team.mdの必須テスト種別(b))のうち、認証の部分は、業務ドメインの設定プロファイルに依存しないため、本ユニットのコードは、どのプロファイルでも同じ動作をすることを、E2Eの共通のシナリオの一部として確認する(シナリオの実装は、frontend-ui(U12)・統合の担当。[assumption])。
- **契約テスト**(team.mdのTest Strategy「Comprehensive」): C4(REST)・C14(`getActiveRoleId`、未選択のnull・例外)・C15(`Operator`・`OperatorContext`)の契約に沿った応答・振る舞いを確認する。
- NFR1.1・NFR1.2は、team.mdが負荷・性能テストを既定に含めないため、自動の負荷試験はしない。統合テストで、認証フィルタの処理時間(ヒット・ミス)と、各APIの応答時間を計測し、Build and Testの結果に記録して確認する(performance-requirements.md)。
- 監査ログの完全性の網羅的検証テストは、本MVPスコープでは必須としない(team.md確定事項)。

## 根拠

- 要件: `inception/requirements-analysis/requirements.md` のNFR2・NFR7・NFR8、FR10.1
- 機能設計: `construction/authentication-service/functional-design/functional-spec.md`(W1〜W7、追補一覧)、`rules.md`(BR5.1〜BR5.16)
- 契約: `inception/contract-design/contract-summary.md` のC4・C10・C11・C14
- 質問回答: `nfr-requirements-questions.md` Q1・Q2・Q4・Q5
- 他ユニットの要件: `construction/user-management/nfr-requirements/tech-stack-decisions.md`、`construction/permission-engine/nfr-requirements/tech-stack-decisions.md`
