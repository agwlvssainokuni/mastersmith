# Logical Components — user-management (U4)

user-managementを構成する、論理的な部品の一覧と、非機能の設計(NFR Design)が、どの部品に適用されるかを示す。本プロジェクトは、単一の実行可能WAR(1プロセス)であり、AWSなどのクラウドのインフラは設計の対象外である(Infrastructure Designの対象外)。そのため、ここでのコンポーネントは、プロセス内の論理的な部品である。

## コンポーネント一覧

| 部品 | 役割 | 適用するNFRの設計 |
|---|---|---|
| `UserController` | `/api/users`系のREST(一覧・招待・更新・無効化) | 認可(NFR2.1)、ボディ上限(NFR2.3) |
| `InvitationAcceptController` | 招待受諾API(認証不要) | ハッシュ計算の制御(NFR1.3)、トークンの露出抑止(NFR2.10) |
| `MePreferencesController` | `/api/me/preferences` | 操作者のuserIdのみで認可(NFR2.1) |
| `UserApplicationService` | 招待・更新・無効化の業務処理。認可・昇格防止・roleIdの実在検証を、入口で行う | NFR2.1・NFR2.5、NFR4.2 |
| `InvitationAcceptService` | 招待受諾(条件付き更新、UserPreference作成) | NFR4.1、NFR2.3 |
| `UserAccountLookupService` | C11の実装(`findByEmail`・`verifyPasswordHash`・`isDisabled`)。ログイン成功時のハッシュ更新を含む | NFR2.2、NFR1.3 |
| `PasswordHasher` | Argon2idのハッシュ計算・検証・パラメータの比較。`PasswordPolicy`(長さの検証)を含む | NFR1.2、NFR2.3 |
| `HashConcurrencyLimiter` | ハッシュ計算の同時実行数の制御(セマフォ、待機2秒) | NFR1.3、NFR3.3 |
| `InvitationFacade` | 招待(W1)の外側の流れ。トランザクションを張らず、排他・許可の取得と返却を行い、`TransactionTemplate`で内側の処理を実行する | NFR4.2 |
| `InvitationAdmission` | 招待の同時実行数の許可(上限5の`Semaphore`、待たない`tryAcquire`) | NFR4.2、NFR3.4 |
| `InvitationMailer` | 招待メールの組み立てと送信。`InvitationMailExecutor`(スレッド数5、待ち行列なし)で実行し、呼び出し側が10秒で打ち切る | NFR1.4、NFR4.2 |
| `MailTemplateRenderer` | 自作mustacheエンジンへのアダプタ(HTMLの生成) | NFR2.7、NFR7.1 |
| `SubjectExtractor` | HTMLの`<title>`から件名を取り出す(文字参照のデコード、改行・長さの検査) | NFR2.7 |
| `MailTemplateValidator` | 起動時に、全言語(ja・en)のテンプレートを、サンプルの値でレンダリングし、`SubjectExtractor`で件名を取り出せることを確認する。取り出せなければ起動を失敗させる | NFR2.7、NFR4.4 |
| `EmailLockRegistry` | 正規化後email単位の排他(プロセス内)。参照数つきのロックの表で、利用者がいなくなったエントリは取り除く。ストライプ方式は採らない | NFR4.2、NFR3.4 |
| `InitialAdminBootstrap` / `InitialAdminProperties` | 初期管理者の自動作成と、設定の検証(起動時) | NFR2.11、NFR4.4 |
| `CurrentOperatorProvider` | セキュリティコンテキストから操作者のuserId・activeRoleIdを読む | NFR2.1 |
| `UserChangedEventPublisher` | コミット後に、独立した新しいトランザクション(`REQUIRES_NEW`)の中でUserChangedEventを発行し、例外を捕捉して警告ログとメトリクスに記録する | NFR4.3 |
| `UserRepository` / `UserPreferenceRepository` | 内部設定DBへの永続化(インデックス付きテーブル) | NFR3.1 |
| `RequestSizeLimitFilter`(U4が所有) | U4のURLパターンに限った、リクエストボディの64KiB上限(413)。認証フィルタより前に置く | NFR2.3 |
| `UserApiExceptionAdvice`(U4が所有、`@RestControllerAdvice`) | U4固有の例外(ハッシュ計算・招待の同時実行数・メール送信・ボディの超過・トークン不一致・検証エラー)を、RFC 9457のProblemDetails(503・413・404・422)に変換する。対象はU4のコントローラに限定し、`@Order`を明記する | NFR2.9、NFR7.2 |
| `RequestLogFilter` / 観測の規約 / セキュリティヘッダー | アプリケーション全体に効く横断的な部品。**共通基盤が所有する**(下記「横断的な部品の所有」)。U4は、ルートのテンプレートの使用などを要求する | NFR2.10、NFR5.2・NFR5.3 |

## 外部との境界

| 相手 | 方向 | 契約 | 同期・非同期 |
|---|---|---|---|
| PermissionEngine(U3) | user-management → permission-engine | C10(`canAccessScreen`、roleIdの実在検証(追補))・Group経由のロール(`getGroupDerivedRoleIds`) | 同期(同一プロセス) |
| AuditLogging(U7) | user-management → audit-logging | UserChangedEvent(コミット後・fire-and-forget) | 同期の`@EventListener`(受信側でtry-catch) |
| AuthenticationService(U5) | authentication-service → user-management | C11 | 同期(同一プロセス) |
| SMTPサーバー | user-management → 外部 | SMTP | 同期(タイムアウト10秒) |
| フロントエンド(U12) | ブラウザ → user-management | C5(REST) | 同期(HTTP) |

## 障害の領域と影響の範囲(failure domain)

| 障害の領域 | 影響を受ける機能 | 影響を受けない機能 |
|---|---|---|
| SMTP(`InvitationMailExecutor`のプール) | 招待・再招待。最大5件の招待が、約12秒ずつ、内部設定DBの接続を保持する | 一覧・更新・無効化・受諾・ログインは、接続プールに余裕がある限り影響を受けない(接続プールの最大サイズの要求は、下記の共通基盤への要求) |
| `HashConcurrencyLimiter` | 招待受諾・ログイン(C11)・初期管理者の作成 | 招待・一覧・更新・無効化(ハッシュ計算の許可は、DB接続を保持しない) |
| `EmailLockRegistry` | 同一emailの招待だけが、順番待ち・503になる | 他のemailの招待(排他を先に取り、許可を占有させない)、その他の機能 |
| `InvitationAdmission` | 満杯のとき、新しい招待が待たずに503になる | 招待以外のすべて |
| 内部設定DB | user-managementのすべて(アプリケーション全体の障害) | なし |

## 共有するリソース

- 内部設定DBの接続プール(他ユニットと共有)。招待のトランザクションがSMTP応答待ちの間、接続を保持するため、同時実行数を5に制限している(reliability-design.md NFR4.2)。接続プールの最大サイズの要求は、下記の共通基盤への要求に記録する。
- JVMヒープ(ハッシュ計算が、許可数×約19MiBを使う、scalability-design.md NFR3.3)。

## 横断的な部品の所有

アプリケーション全体に効く部品は、user-managementが単独で決めず、次のとおり所有を分ける。

| 部品 | 所有者 | user-managementの関わり |
|---|---|---|
| 汎用の例外ハンドラの基底(RFC 9457への変換) | 共通基盤 | U4固有の例外の変換は、U4が、対象を限定した`UserApiExceptionAdvice`で登録する。共通基盤がU4の例外の型に依存しない(依存方向を保つ) |
| 観測の規約(`ServerRequestObservationConvention`)・リクエストログ | 共通基盤 | ルートのテンプレートの使用を要求する |
| セキュリティヘッダー(`Referrer-Policy`)・認証フィルタチェーン | 共通基盤(認証を担うauthentication-serviceを含む全体の構成) | 付与を要求する |
| `RequestSizeLimitFilter` | U4(U4のURLパターンに限定) | 認証フィルタより前に置くよう、フィルタチェーンの順序を、共通基盤と調整する |
| `UserApiExceptionAdvice` | U4(U4のコントローラに限定、`@Order`を明記) | 共通基盤の基底のハンドラと衝突しないよう、優先度を明記する |

## 共通基盤・他ユニットへの要求と契約追補(保留)

NFR Designで判明した、共通基盤・他ユニット・契約への要求を、次のとおり記録する。いずれも、Code Generationの計画承認までに、対象の設計に反映するか、ユーザーに確認する保留事項である(番号は、`nfr-requirements/tech-stack-decisions.md`の「契約追補(保留事項の一覧)」の1〜7の続きである)。

| 番号 | 対象 | 内容 | 出典 |
|---|---|---|---|
| 8 | U12(受諾画面)・招待メール | 招待リンクの形式(ルート`/invitations/accept`・フラグメントのキー`token`)。U12への要求: フラグメントの読み取りと`history.replaceState`による消去、トークンを画面の状態・ログ・外部送信に含めないこと、認証なしで開けるルート、SPAの直接アクセスのフォールバック | security-design.md NFR2.10 |
| 9 | C11 UserAccountLookupApi | `verifyPasswordHash`が、ログイン成功時のハッシュ更新という書き込みを伴う副作用を持つこと。呼び出し元のトランザクション属性(読み取り専用を含む)に依存しないよう、`REQUIRES_NEW`で行うこと | security-design.md NFR2.2 |
| 10 | audit-logging(U7) | UserChangedEventの受信が、同期の`@EventListener`で、呼び出し元(発行側が開く新しい独立したトランザクション)に参加して書き込むこと。受信側のトランザクション属性の定め | reliability-design.md NFR4.3 |
| 11 | 共通基盤 | 観測の規約・リクエストログでルートのテンプレートを使うこと(生のパスを出さない、ハンドラ到達前のフォールバックを含む)。ProblemDetailsの`instance`にトークンを含む生のパスを入れないこと。本番相当の環境で、フレームワークのロガー(`org.springframework.web`・Tomcat)をINFO以上にすること | security-design.md NFR2.9・NFR2.10 |
| 12 | 共通基盤(認証・セキュリティヘッダー) | `Referrer-Policy: no-referrer`を、全レスポンスに付与すること。`RequestSizeLimitFilter`を認証フィルタより前に置けること | security-design.md NFR2.3・NFR2.10 |
| 13 | 共通基盤(内部設定DB) | 接続プールの最大サイズを、通常の同時処理数(同時アクセス最大50ユーザー)に、招待の上限5を加えた値以上にすること | reliability-design.md NFR4.2 |
| 14 | 機能設計 BR4.15・C5 | nameの最大長(100文字、[assumption])と、制御文字(CR/LFなど)の禁止を、フィールド単位の422として検証すること(既存の保留7番を拡張する) | security-design.md NFR2.7 |
| 15 | 共通基盤(トレース・メトリクス) | トレースの実装(Micrometer Tracingか、OTELのJavaエージェントか)と、メトリクスのエクスポート形式(PrometheusかOTLPか)の確定。トークンの露出抑止の方式は、選ばれた実装に合わせて検証する | security-design.md NFR2.10、observability-design.md NFR5.1 |

## NFR7.1: 招待メールの言語

- `MailTemplateRenderer`は、言語(ja/en)ごとのHTMLテンプレートを持つ。招待の操作(`POST /api/users`)で指定された言語(省略時はja)のテンプレートを選ぶ。再招待でも、その時点で指定された言語で再送する。
- 言語ごとのテンプレートの配置と、自作mustacheエンジンの取り込み・構文の範囲は、Code Generationの計画承認までに確認する(`nfr-requirements/tech-stack-decisions.md`の確認事項)。
- 招待APIへの`locale`項目の追加は、契約追補(保留)の1番として記録済みである。

## NFR7.2: バリデーションエラーメッセージのi18n

- `UserApiExceptionAdvice`が、フィールド単位のバリデーションエラー(422のProblemDetailsの`errors[]`)を、フィールド名と、**i18nキー**(メッセージのキー)で返す。文言への変換(翻訳)は、フロントエンド(U12)が、config-engineが管理する翻訳リソースを用いて行う([assumption]、`nfr-requirements/tech-stack-decisions.md` NFR7.2。config-engineの翻訳リソースの扱いは、実装時に、config-engineの設計を確認する)。
- キーの命名と、パラメータ(文字数の上限など)の持たせ方は、契約追補(保留)の6番として記録済みである。
- 入力値そのもの(パスワードなど)は、エラーに含めない(security-design.md NFR2.9)。

## NFR8.1: 保守性(業務固有情報のハードコード禁止)

- 上記のコンポーネントは、User・UserPreference・UserChangedEventのみを扱い、業務固有のテーブル名・カラム名・業務ルールを持たない。
- メールの文面は、コードに埋め込まず、外部のテンプレート(言語ごとのHTMLファイル)として持つ。
- 設定値(ハッシュのパラメータ・同時実行数・SMTP・ベースURL・初期管理者)は、`application.yml`(環境変数等から注入)に置き、コードに埋め込まない。

## NFR8.2: テストの設計

`nfr-requirements/tech-stack-decisions.md` NFR8.2のテストを、次の部品ごとの単位で用意する。

| 部品 | 主なテスト |
|---|---|
| `UserApplicationService` | 認可の拒否(401・403)、自己のroleIds変更の拒否、roleIdの実在検証、テーブル駆動の認可テスト、同一Userの同時更新で、beforeValueが直前の確定した値になること(行ロック) |
| `InvitationAcceptService` | 並行受諾(1件のみ成功)、404の一本化、未知のトークンでハッシュを計算しないこと、UserPreferenceの作成 |
| `InvitationFacade`・`InvitationAdmission`・`EmailLockRegistry` | 同一emailの同時招待の直列化、同一emailの連打が他のemailの許可を占有しないこと、許可の満杯で待たずに503、排他・許可がコミット後に返されること、再招待と受諾・取消の競合(422・503) |
| `InvitationMailer`・`SubjectExtractor`・`MailTemplateValidator` | HTMLエスケープ、件名の文字参照デコード後の改行の拒否、200文字の上限、メール送信失敗・打ち切りでのロールバックと503、全言語のテンプレートの`<title>`の起動時検証 |
| `UserChangedEventPublisher` | コミット後の発行で、監査ログの行が実際に永続化されること(統合テスト)。発行の例外が、HTTPの結果に影響しないこと |
| `HashConcurrencyLimiter`・`PasswordHasher` | 同時実行数の上限と待機2秒の503、パスワード長の境界値(7・8・128・129、サロゲートペア)、ハッシュ計測の単体ベンチマーク(p95が300ms以下)、パラメータが古いハッシュの更新 |
| `RequestSizeLimitFilter`・`UserApiExceptionAdvice` | `Content-Length`のある場合・チャンク転送の場合の413、認証前のボディの拒否 |
| ログ・観測の規約(共通基盤への要求) | ログ(ハンドラ到達前の失敗を含む)・スパン・メトリクスのラベル・ProblemDetailsの`instance`に、招待トークンの文字列が現れないこと(選ばれたトレースの実装で検証) |
| `InitialAdminBootstrap`・設定 | 設定の不備(未設定・長さ・email形式・SMTP・ベースURL・テンプレートの`<title>`)で起動が失敗すること(安全失敗のテスト) |
| `RequestSizeLimitFilter` | 64KiBを超えるボディが413になること |

80%行カバレッジは、CIでのマージ前に確認する(team.md)。

## 根拠

- 要件: `nfr-requirements/`の各成果物、`inception/requirements-analysis/requirements.md`のNFR6〜NFR8
- 機能設計: `functional-design/functional-spec.md`(W1〜W8)、`rules.md`
- 契約: `inception/contract-design/contract-summary.md`のC5・C10・C11
