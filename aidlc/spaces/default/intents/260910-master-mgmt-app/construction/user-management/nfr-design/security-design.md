# Security Design — user-management (U4)

`nfr-requirements/security-requirements.md`の各要件を、設計に落とし込む。user-managementは、認証情報(パスワードのハッシュ・招待トークン)と個人情報(氏名・メールアドレス)を扱い、ロールの付与も担うため、多層で防御する。

## 全体の構成

```
ブラウザ ─(Bearer)→ RequestSizeLimitFilter → 認証フィルタ → Controller
                                                          → UserApplicationService ─→ PermissionEngineApi(C10)
                                                                 │  ├→ UserRepository(内部設定DB)
                                                                 │  ├→ PasswordHasher(Argon2id)
                                                                 │  ├→ InvitationMailer(SMTP)
                                                                 │  └→ UserChangedEventPublisher(コミット後)
招待受諾(認証不要)────────────────────→ Controller ─→ InvitationAcceptService
```

- 認可の判定は、必ずサーバー側の`UserApplicationService`の入口で行う(NFR2.1)。
- 資格情報(パスワード・招待トークン)は、`PasswordHasher`(ハッシュ)と、URL・ログの扱い(NFR2.10)以外の場所に、平文のまま置かない。

## NFR2.1: 認可(サーバー側での再検証)

- **操作者の取得**: `CurrentOperatorProvider`(user-management側のインタフェース)が、リクエストのセキュリティコンテキスト(認証フィルタが検証済みのBearerトークンから設定したもの)から、操作者のuserIdとactiveRoleIdを読む。**AuthenticationServiceコンポーネントは呼び出さない**(U5→U4の依存方向を保ち、循環を避けるため)。トークンがuserIdとactiveRoleIdを含むことは、U5の機能設計で確定する(`functional-spec.md`のOpen Questions)。
- **`/api/users`系**: `UserApplicationService`の各操作の先頭で、`canAccessScreen(activeRoleId, "user-management")`を評価する。操作者が解決できなければ401、権限がなければ403(RFC 9457のProblemDetails)。
- **`/api/me/preferences`**: 操作者のuserIdが解決できれば許可し、対象userIdは常に操作者のuserIdとする(リクエストからは受け取らない)。activeRoleIdには依存しない。
- **招待受諾API**: 認証は不要(C5)。トークンの保持が根拠となる(NFR2.4)。

## NFR2.2: 認証情報の保護

- **ハッシュ化**: `PasswordHasher`(Argon2id、パラメータは`application.yml`)。平文のパスワードは、メソッドの引数として渡すだけで、フィールドに保持せず、ログにも出さない。
- **応答・イベントへの非出力**: `UserResponse`(一覧の要素)は、`userId`・`name`・`email`・`status`・`roleIds`のみを持つ。`passwordHash`と`invitationToken`を持たない型として定義することで、誤って出力される経路を作らない。UserChangedEventのスナップショットも、`{name, email, status, roleIds}`のみを持つ専用の型とする。
- **C11の`findByEmail`**: `UserAccount`の`passwordHash`にはnullを入れる。
- **ログイン成功時のハッシュ更新(Q3=A)**: `verifyPasswordHash`が成功したとき、`PasswordHasher.needsUpgrade(保存済みハッシュ)`が真なら、入力されたパスワードで新しいパラメータのハッシュを作り(検証と同じ1回の許可の中で連続して計算し、許可を返す)、そのあと、**独立した新しいトランザクション(`REQUIRES_NEW`)**で、`passwordHash`が検証時と同じ値の場合に限って更新する(条件付き更新。パスワードの変更などと競合した場合は、更新しない)。独立したトランザクションで行うため、呼び出し元(authentication-service)が、読み取り専用のトランザクションの中で`verifyPasswordHash`を呼んでも、更新が失われない。更新に失敗しても、ログインの成否には影響させず、警告ログ(ユーザーIDのみ。ハッシュ値・パスワードは含めない)に記録する。**`verifyPasswordHash`が、書き込みを伴う副作用を持つこと**は、C11の契約追補(保留)として、logical-components.mdの一覧に記録する。この更新は、UserChangedEventの対象(氏名・メール・状態・ロール)を変えないため、イベントは発行しない。`needsUpgrade`は、保存済みのハッシュ文字列に含まれるパラメータ(`$argon2id$v=19$m=…,t=…,p=…$`の部分)を、現在の設定と比較して判定する。Spring Securityの`Argon2PasswordEncoder`が、パラメータの比較を行うかどうかは、実装時に確認し、行わない場合は本設計のとおり自前で解析して比較する。

## NFR2.3: パスワードポリシーの実装

- 長さの検証(8〜128、Unicodeのコードポイント数で数える)は、`PasswordPolicy`という1つの部品に集約し、招待受諾・初期管理者の作成・`verifyPasswordHash`から共通に使う。
- `verifyPasswordHash`では、129文字以上は、ハッシュ計算の前にfalseを返す。
- **リクエストボディの最大サイズ(64KiB)**: `RequestSizeLimitFilter`(U4が所有するサーブレットフィルタ)が、次のとおり413で拒否する。対象は、U4のURLパターン(`/api/users/**`・`/api/me/preferences`)に限る。
  - `Content-Length`が64KiBを超える場合は、内容を読まずに、フィルタの中で413を返す。
  - `Content-Length`がない(チャンク転送)場合は、リクエストの入力ストリームを、読み込み量を数えるストリームで包み、64KiBを超えた時点で、専用の例外(`RequestBodyTooLargeException`)を投げる。この例外は、JSONの読み取りの中で、`HttpMessageNotReadableException`(400)に包まれるため、U4の例外変換(`UserApiExceptionAdvice`)が、原因の例外を判別して、413に変換する。
  - **フィルタの位置**: 認証フィルタより前に置く(認証前の巨大なボディを読み込ませないため)。フィルタチェーンの中の順序は、共通基盤の設計と調整する(logical-components.mdの「共通基盤への要求」)。

## NFR2.4: 招待トークン

- 生成: `java.util.UUID.randomUUID()`(暗号論的乱数)。
- 状態: 受諾(`InvitationAcceptService`)または取消の時点でnullにする。再招待では新しい値に置き換える。
- 応答: 未知・使用済み・取消済みは、区別せず404。
- 招待受諾APIに専用のレート制限は設けない([assumption]、`security-requirements.md` NFR2.4)。404の回数を、カウンタで監視する(observability-design.md)。

## NFR2.5: 権限昇格の防止

`UserApplicationService`の更新処理(`PUT /api/users/{userId}`)で、次の順序で検証する。

1. `canAccessScreen(activeRoleId, "user-management")`(NFR2.1)。
2. 対象userIdが操作者自身で、かつ`roleIds`が現在値と異なる場合は、422(フィールド単位のエラー)。この拒否の回数を、カウンタに記録する(observability-design.md NFR5.1)。
3. 指定された各`roleId`の実在を、C10の存在検証メソッド(契約追補、保留)で確認する(初期管理者の自動作成を除く)。

## NFR2.6: 個人情報の保護と保持

- 氏名・メールアドレスは、`UserResponse`(認可済みの`/api/users`系のみ)と、UserChangedEventのスナップショット(監査ログ経由、C6)以外へは出さない。ログ・トレース・メトリクスには、userIdのみを用いる。
- 無効化(disabled)したユーザーは、物理削除・匿名化をせず、そのまま保持する(Q4=A)。監査ログへの複製は、追記専用のため、後から匿名化できない(残余リスク5)。

## NFR2.7: 招待メール送信の安全性

- **SMTPの接続情報**: `application.yml`(環境変数等から注入)の設定値で与える。ソースコード・リポジトリには置かない。設定の不備(未設定など)は、起動時に検知して失敗させる。
- **暗号化(Q2=B)**: SMTPの暗号化(STARTTLSまたはSMTPS)の要否は、環境ごとの設定(ホスト・ポート・暗号化の有無)に任せる。**アプリケーションでは、暗号化の有無を検査しない**。**この決定は、`nfr-requirements/security-requirements.md` NFR2.7の[assumption]「TLSの要否は環境ごとに切り替え、本番相当の環境では必須」のうち「必須」の部分を置き換える**: 暗号化の要否は環境の設定であり、アプリケーションは強制しない(NFR Requirementsの該当の記述は、承認済みの成果物のため書き換えず、本設計の記述を優先する)。暗号化なしに誤設定された場合、招待メール(招待リンク)と、SMTPの認証情報(SMTP AUTH)が、平文で流れる恐れがある。これは残余リスク6として、下記に記録する。
- **入力起因の拒否と、テンプレート起因の拒否の分離**: 氏名の入力の不備(最大長100文字を超える、制御文字(CR/LFなど)を含む)は、入力の時点で、フィールド単位の422として拒否する(BR4.15の追補として、契約追補・機能設計への追補の保留一覧に記録する。最大長100文字は[assumption])。したがって、件名の拒否(下記)は、テンプレートの不備や設定の誤りなど、**テンプレート起因**の場合に限られ、これだけを、招待全体の失敗(503・SMTP不調用のカウンタとは別のカウンタ)として扱う。
- **メールの生成**: `MailTemplateRenderer`(自作mustacheエンジンへのアダプタ)が、HTMLテンプレートに値を差し込んで、HTML本文を得る。差し込む値(氏名など)は、エンジンのHTMLエスケープを通す。エンジンの仕様は、Code Generationの計画承認までに確認する(`tech-stack-decisions.md`の確認事項)。
- **件名の抽出**: `SubjectExtractor`が、レンダリング後のHTMLの`<title>`要素の値を取り出し、文字参照をデコードしたうえで、改行(CR/LF)を含む場合と200文字を超える場合は**拒否**する(除去はしない)。拒否は、メールを送らずに失敗として扱う(BR4.16、招待全体を巻き戻して503)。件名の拒否は、氏名の入力起因の場合を、入力の検証で先に排除するため、テンプレート起因に限られる。SMTPの不調のカウンタ(`user.invitation.mail.failed`)とは別に、テンプレート起因の拒否を数える(observability-design.md NFR5.1の`user.invitation.subject.rejected`)。非ASCII文字は、メールヘッダーとして符号化して送る。
- **ヘッダーの組み立て**: 宛先には、BR4.15で形式を検証し、正規化したemailだけを用いる。件名以外のヘッダーに、利用者の入力値を差し込まない。
- **招待リンクのベースURL**: `application.yml`(環境変数等から注入)の設定値から作る。リクエストのHostなどのヘッダーからは導出しない。**HTTPSを必須とする**。`http`のURLは、設定`mastersmith.mail.allow-insecure-link`(既定値はfalse)がtrueの場合(開発のMailpit環境向け)に限って許可し、それ以外は、起動時に失敗させる。すなわち、「本番相当」かどうかは、アプリケーションが環境を推測するのではなく、この設定値(既定でfalse)で決まる。この検査はリンクのURLの形式に対するもので、SMTP接続の暗号化(Q2=B)の検査とは別である。

## NFR2.8: セキュリティ関連CIゲート

team.mdの既定(SAST・シークレットスキャン。依存関係の脆弱性スキャンは対象外)を、そのまま適用する。追加で、リポジトリに初期管理者・SMTPの実際の値が入らないことを、シークレットスキャンで確認する(NFR2.11)。

## NFR2.9: エラーレスポンスの情報開示制御

エラーの変換は、次の2層に分けて所有する(logical-components.mdの「横断的な部品の所有」)。

- **共通基盤(アプリケーション全体)**: 汎用の例外(認証・認可・入力形式の不備など)を、RFC 9457のProblemDetailsに変換する基底の例外ハンドラ。スタックトレース・SQL・内部の型名を含めない。
- **U4(`UserApiExceptionAdvice`)**: U4固有の例外だけを扱う、対象を限定した`@RestControllerAdvice`(U4のコントローラのパッケージに限定し、`@Order`は共通基盤のハンドラより優先するよう明記する)。`HashCapacityExceededException`・`InvitationCapacityExceededException`・ロック待ちタイムアウト・メール送信失敗は503、リクエストボディが大きすぎる場合(`RequestBodyTooLargeException`)は413、招待トークンの不一致は404、フィールド単位の検証エラーは422に変換する。検証エラーの`errors[]`には、フィールド名とi18nキー(logical-components.md NFR7.2)を入れる。
- **`instance`の値**: Spring MVCが返すProblemDetailは、`instance`に既定でリクエストのパスを入れるため、招待受諾APIでは、トークンを含む生のパスが入る。U4の変換では、`instance`を、ルートのテンプレート(`/api/users/invitations/{token}/accept`)にする。共通基盤の変換にも、同じ扱いを要求する(logical-components.mdの一覧)。

## NFR2.10: 招待トークンのURL露出の抑止

招待リンクは、**トークンをフラグメント(`#`より後ろ)に入れる**(Q1=A): `<ベースURL>/invitations/accept#token=<トークン>`。

- **リンクを開いたとき**: フラグメントはブラウザがサーバーに送らないため、受諾画面を開くリクエストのアクセスログ・リバースプロキシのログ・Refererに、トークンは現れない。
- **受諾画面(フロントエンド、U12の責務)**: フラグメントからトークンを読み取ったら、ただちに`history.replaceState`でURLからフラグメントを消し、トークンを、画面の状態・ログ・エラー報告・外部への送信に含めない。読み取ったトークンは、受諾APIの呼び出し(パス`/api/users/invitations/{token}/accept`)にだけ使う。
- **受諾API(パスにトークンを含む)の呼び出し時**: 次のとおりトークンを出さない。
  - リクエストログ: サーブレットフィルタが、処理の後で、パスの記録に、マッチしたルートのテンプレートを使う。**フォールバック**: ハンドラに到達する前に失敗した場合(413、認証の失敗など)で、テンプレートが得られないときは、パスが招待受諾APIのパターン(`/api/users/invitations/*/accept`)に一致すれば、常にテンプレート表記(`/api/users/invitations/{token}/accept`)に置き換える。それ以外で得られない場合は、固定の文字列に置き換え、生のパスは記録しない。
  - **フレームワークの標準のロガー**: `org.springframework.web`・Tomcat(`org.apache.catalina`・`org.apache.coyote`)など、生のリクエストのパスを出しうるロガーは、本番相当の環境でINFO以上に制限し、DEBUG・TRACEを使わない(ログの設定の制約として、共通基盤への要求に記録する)。
  - アクセスログ: Tomcatのアクセスログは無効(既定)のままとし、アクセスの記録は、上記のリクエストログで行う。リバースプロキシを使う環境では、プロキシ側で、このパスのトークン部分をマスクする(環境側の前提として、`tech-stack-decisions.md`に記録済み)。
  - トレース・メトリクス: Spring Bootの観測(Observation)の規約(`ServerRequestObservationConvention`)を差し替え、URLのパス・全体を表す属性に、ルートのテンプレートを入れる。メトリクスのURIラベルも、ルートのテンプレートにする。**前提**: この方法は、Micrometer ObservationとMicrometer Tracingのブリッジでトレースを出す場合にだけ有効である。トレースの実装(Micrometer Tracingか、OTELのJavaエージェントか)は、tech-stack-decisions.mdのとおり、未確定(CI Pipelineで確定)である。OTELのJavaエージェントを使う場合は、スパンのURL属性を書き換える、エージェント側の設定が必要になる。確定するまでは、この前提を置き、確定後に、NFR8.2のテスト(トークンの文字列がスパン・ラベルに現れないこと)で、選ばれた方式を検証する。
- **多層防御**: 受諾画面を返すレスポンスに、`Referrer-Policy: no-referrer`を付ける。これは、認証フィルタチェーンを含む、アプリケーション全体のセキュリティヘッダーの設定であり、**共通基盤(認証を担うauthentication-serviceを含む、全体の構成)が所有する**。U4は、この付与を、共通基盤への要求として記録する(logical-components.mdの一覧)。
- **U4とU12の間のインタフェース**: 招待リンクの形式(ルート`/invitations/accept`・フラグメントのキー`token`)と、U12(受諾画面)への要求事項(フラグメントの読み取り、`history.replaceState`による消去、トークンを画面の状態・ログ・外部送信に含めないこと、認証なしで開けるルートであること、SPAの直接アクセスのフォールバック)は、契約追補(保留)として、logical-components.mdの一覧に記録する。
- **検証**: ログ・スパン・メトリクスのラベルに、トークンの文字列が現れないことを確認するテストを用意する(`tech-stack-decisions.md` NFR8.2)。

## NFR2.11: 初期管理者の資格情報の供給

- `InitialAdminProperties`(設定プロパティのクラス)が、`application.yml`(環境変数等から注入)の、初期管理者のemail・パスワード・任意の`role-ids`を受ける。リポジトリには、既定値も実値も置かない。
- 起動時の検証(未設定・パスワードの長さの違反・emailの形式不正)は、プロパティのバインド時に行い、不備があれば起動を失敗させる。
- `InitialAdminBootstrap`(`ApplicationRunner`)が、対応するUserが(statusを問わず)存在しない場合に限り、作成する。パスワードは、ログ・例外メッセージに含めない。ログには、「作成した」「既に存在したため何もしなかった」の事実だけを記録する。
- 初回ログイン後のパスワード変更の要否は、authentication-service(U5)の機能設計で確認する。

## 脅威と設計の対応(STRIDE)

| 分類 | 対応する設計 |
|---|---|
| なりすまし | UUIDv4のトークン、受諾・取消でのnull化、404の一本化、フラグメントによるリンク(NFR2.4・NFR2.10) |
| 改ざん | `UserApplicationService`入口での認可再検証、PUTの更新可能項目の限定(NFR2.1) |
| 否認 | すべての変更でUserChangedEventを発行(コミット後、reliability-design.md NFR4.3) |
| 情報漏えい | 専用の応答型による機微項目の非出力、ログ・トレースのトークンのマスク、Referrer-Policy(NFR2.2・NFR2.10) |
| サービス拒否 | 128文字の上限、ボディの64KiB上限、同時ハッシュ計算の制御(performance-design.md NFR1.3) |
| 権限昇格 | 自己のroleIds変更の拒否、権限管理者の明示的操作に限定(NFR2.5) |

## 残余リスク(設計上の追記)

`nfr-requirements/security-requirements.md`の残余リスク(1〜5)について、本設計での扱いを次のとおり記録し、あわせて新しい残余リスクを追加する。

| 番号 | リスク | 内容 | 判断 |
|---|---|---|---|
| 1(追記) | 無効化・ロール変更後の既発行アクセストークン | 無効化に加えて、PUTでroleIdsが外されたユーザーも、既発行のアクセストークン(有効期限10分)に含まれるactiveRoleIdの権限で、最大10分間は、旧ロールの権限のまま操作できる | 受容(要件のFR2.3・FR3.1に沿う既定動作) |
| 3(再確認) | 招待トークンの平文・無期限保存 | 見直し先がNFR Designだった。招待トークンのハッシュ保存や有効期限は、今回も採用しない。受諾時に、URLで受け取ったトークンで、そのまま検索する単純さを優先する。DBが漏えいする場合は、内部設定DB全体(ハッシュ済みのパスワード・氏名・メールアドレスを含む)の漏えいであり、その保護は、内部設定DB全体の方針に従う | 受容(有効期限を導入する場合(Q2=Aの見直し)に、ハッシュ保存とあわせて再検討する) |
| 6 | SMTP接続の平文送信 | SMTPの暗号化を環境の設定に任せる(Q2=B)ため、暗号化なしに誤設定された場合、招待リンク(トークンを含む)と、SMTPの認証情報(SMTP AUTH)が、平文で流れる | 受容(環境側の責任。運用フェーズが本MVPスコープ外のため、担当が定まるまでの前提として記録) |
| 7 | 打ち切り後のメール送信 | メール送信を10秒で打ち切っても、送信のスレッドは、しばらく動き続け、その間に送信が完了すると、ロールバック済みで存在しないトークンのリンクを含むメールが届きうる。そのリンクは、受諾時に404になる。この間、プールが占有され、新しい招待が503になりうる | 受容(SMTPの遅延が続く間だけの、まれなケース。管理者が再招待して解消する) |

## 根拠

- 要件: `nfr-requirements/security-requirements.md`(NFR2.1〜NFR2.11)
- 機能設計: `functional-design/functional-spec.md`、`rules.md`(BR4.2・BR4.4・BR4.5・BR4.8〜BR4.16)
- 契約: `inception/contract-design/contract-summary.md`のC5・C6・C10・C11
- 設計質問: `nfr-design-questions.md` Q1・Q2・Q3
