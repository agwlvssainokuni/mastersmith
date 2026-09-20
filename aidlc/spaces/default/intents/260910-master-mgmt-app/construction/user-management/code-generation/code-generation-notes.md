<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Code Generation 作業メモ — user-management (U4)、Step 1〜11

最終の`code-summary.md`(オーケストレーターが作成)へ反映するための、実物で確認した事実の記録。Step 12以降の作業で追記される。

## 自作mustacheエンジンの取り込み(Step 2、実物で確認)

- リポジトリ: `https://github.com/agwlvssainokuni/java-mustache-processor`。サブモジュールのパスは`external/java-mustache-processor`、固定したコミットは`8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`(`.gitmodules`とgitlinkで固定)。
- 座標: group `cherry.mustache`、artifact `cherry-mustache-core`、version `0.1.0`(ルートの`build.gradle.kts`の`allprojects`で指定)。Java 25のツールチェーン。実行時の依存は`org.slf4j:slf4j-api:2.0.16`のみ(このプロジェクトでは`2.0.18`に解決される)。ライセンスはApache-2.0(`LICENSE`)。
- 参照方法: `settings.gradle.kts`の`includeBuild("external/java-mustache-processor")`と、`backend/build.gradle.kts`の`implementation("cherry.mustache:cherry-mustache-core")`。`:backend:dependencies`で`project ':java-mustache-processor:cherry-mustache-core'`に解決されることを確認した。エンジン側のOWASP依存関係チェックのプラグイン(`org.owasp.dependencycheck` 10.0.4)は、エンジンの`cherry-mustache-core`のビルド設定にあり、`includeBuild`ではエンジン側の設定のまま使われ、このプロジェクトのビルド設定には混ざらない(このプロジェクトの`compileJava`・`test`は、プラグインのタスクを実行しない)。`cherry-mustache-cli`(shadowJar)は、参照していないためビルドされない。
- 公開API: `Mustache.compile(String)`・`Mustache.compile(Reader, PartialResolver)`、`Template.render(Object data)`(`Map`またはPOJO)。公式のMustache仕様にフル準拠。
- **HTMLエスケープの既定動作(Step 9で確認済み)**: `{{変数}}`の値は、既定でHTMLエスケープされる(`ast/HtmlEscaper`のソースで`& < > "`を確認し、`MailTemplateRendererTest`で、氏名の`<script>alert(1)</script> & "q"`が`&lt;script&gt;…&amp; &quot;q&quot;`になり、二重エスケープされないことを確認した)。**シングルクォート`'`はエスケープされない**ため、テンプレートの属性値はダブルクォートで囲み、エスケープなしの展開(`{{{ }}}`・`{{& }}`)を使わない(テストで確認)。したがって`MailTemplateRenderer`は、差し込む値を事前にエスケープしていない。
- テンプレートのライセンスヘッダーは、レンダリング結果(メール本文)に出さないよう、Mustacheのコメント(`{{! … }}`)で書いた。

## 依存のバージョン管理(Step 2、実物で確認)

- `org.springframework.security:spring-security-crypto`: Spring Boot BOM(4.1.1、spring-security-bomを取り込み)で管理される(`7.1.1`)。バージョンは指定していない。
- `org.bouncycastle:bcprov-jdk18on`: BOMの管理対象外。Maven Centralのメタデータの最新の安定版`1.86`を固定した。
- `spring-boot-starter-mail`: BOMで管理(`4.1.1`)。実行時に`org.eclipse.angus:angus-mail`が解決される。

## Argon2PasswordEncoderの挙動(Step 2・6、実物のソースで確認)

- コンストラクタ: `Argon2PasswordEncoder(saltLength, hashLength, parallelism, memory(KiB), iterations)`。
- `upgradeEncoding(String)`(`upgradeEncodingNonNull`)は、保存済みのハッシュから`Argon2EncodingUtils.decode`でパラメータを取り出し、**メモリまたは反復回数が現在の設定より小さい場合にtrue**を返す。**並列度は比較しない**。不正な形式では`IllegalArgumentException`を投げる。したがって`needsUpgrade`は、この判定に従い、`$argon2id$`で始まらない値・不正な形式はfalseとした(自前の解析は不要だった)。現在の設定より強い(メモリ・反復が大きい)ハッシュは、更新の対象としない。
- `matches`は、不正な形式のハッシュに対して、警告ログ(`Malformed password hash`、例外つき)を出してfalseを返す(ハッシュ値・平文はメッセージに含まれない)。
- 既定値: メモリ19456KiB・反復2・並列度1・ソルト16バイト・ハッシュ32バイト(`mastersmith.users.hash.*`)。

## ハッシュのベンチマーク(NFR1.2、`PasswordHasherBenchmarkTest`)

- 環境: CPUコア数8、最大ヒープ512MiB、macOS(aarch64)、JDK 25.0.4。Argon2id(m=19456KiB, t=2, p=1)、ウォームアップ10回、計測40回、並列度1・同時実行なし。
- 結果: hash p95 = 31.1ms、verify p95 = 34.3ms(目標300ms以下を満たす)。基準は緩めていない。実行環境により揺れうる。

## 行ロックのタイムアウト(Step 4・5、実物で確認)

- `UserRepositoryCustomImpl#findByIdForUpdate`は、`jakarta.persistence.lock.timeout`ヒント(設定値`mastersmith.users.db-lock-timeout`)を渡す。H2でも有効であることを、`UserRepositoryLockTest`(設定を1秒にして、約1.03秒で`PessimisticLockingFailureException`)で確認した。設定がない場合の既定は15秒。
- `User.roleIds`は`@ElementCollection(EAGER)`に`@Fetch(FetchMode.SELECT)`を付け、`SELECT … FOR UPDATE`が結合を含まないようにした。

## 計画からの差異・追加(意図的)

- `Locale`の列挙は、`java.util.Locale`との混同を避けるため`UiLocale`とした。
- `mastersmith.mail.from`(送信元アドレス、必須)を追加した(MimeMessageのFromに必要。計画の設定の一覧にはなかった)。`spring.mail.host`・`port`は環境変数から必須で注入する(既定値なし)。
- `UserRepository`に、Step 12で使う`updatePasswordHashIfUnchanged`(ログイン成功時のハッシュ更新の条件付き更新)を、Step 4で追加してテストした。
- 招待メールの失敗の分類: 件名の拒否(およびテンプレートのレンダリング失敗)は`user.invitation.subject.rejected`だけを数え、SMTPの不調(送信失敗・打ち切り・プール満杯・中断)は`user.invitation.mail.failed`だけを数える(observability-design.mdの表の「件名の拒否」を失敗のカウンタにも含める記述と、NFR5.1の「別に数える」記述が食い違うため、後者に従った)。
- `HashCapacityExceededException`は、C11の追補に合わせ、`com.mastersmith.usermanagement`直下に置いた。他の例外(`InvitationMailException`・`SubjectRejectedException`・`EmailLockTimeoutException`・`InvitationCapacityExceededException`)は`exception`パッケージ。Step 14の`UserApiExceptionAdvice`は、`InvitationMailException`(件名の拒否を含む)・`EmailLockTimeoutException`・`InvitationCapacityExceededException`・`HashCapacityExceededException`・行ロックの取得失敗(`PessimisticLockingFailureException`)を503に変換する想定。
- `UserChangedEventPublisher`は、`@Qualifier("transactionManager")`で、内部設定DBのJPAのトランザクションマネージャを指定した(業務データ用の`businessTransactionManager`が有効な場合の曖昧さを避けるため)。
- ロールIDの重複指定は、重複を除いて扱う(rules.mdのBR4.5の追補に記載)。`name`は前後の空白を除去して保持する(BR4.15の追補の「空白を除去した結果が空でないこと」に対応。Step 12で実装)。

## 整形・検査の運用(Step 2以降)

- 他ユニットのファイルを変更しないため、`spotlessApply`(プロジェクト全体)は使わず、`google-java-format`(1.30.0、Spotlessが用いるものと同じ)をU4のJavaファイルにだけ適用した。ライセンスヘッダーは、U4のファイルの先頭に手で付与した。
- 既存の`spotlessCheck`は、他ユニット(`dataio`など)で、HEADの時点から不合格だった(`spotlessApply`を全体に適用すると、他ユニットの34ファイルに整形のみの差分が出た)。U4のファイルは、`google-java-format`の適用済みで、`checkstyleMain`・`checkstyleTest`も合格している。

## Step 12〜13(ビジネスロジック層の実装とテスト)で判明した事実・計画との差異

### 実装の構成

- 操作者: `security/Operator`(userId・activeRoleId、未解決の項目はnull)、`CurrentOperatorProvider`(`HttpServletRequest`から解決)、暫定実装`HeaderCurrentOperatorProvider`(`X-User-Id`・`X-Active-Role-Id`。JWTの検証はしない)、`UserAuthorizer`(`requireUserAdmin`: 未解決は401、`canAccessScreen(activeRoleId, "user-management")`がfalseなら403。`requireOperatorUserId`: `/api/me/preferences`用、userIdのみ)。サービスは`Operator`を引数に受ける(Web層のStep 14が`CurrentOperatorProvider`で解決して渡す)。
- 業務処理: `UserApplicationService`(一覧・更新・無効化)、`InvitationFacade`(招待)、`InvitationAcceptService`(受諾)、`UserPreferenceService`、`UserAccountLookupService`(C11)、`InitialAdminBootstrap`。いずれも`@Transactional`を使わず、`TransactionTemplate`(`@Qualifier("transactionManager")`)を使う。
- 入力の検証: `service/UserInputValidator`(状態を持たない静的ユーティリティ。計画にはなかった部品)。エラーは`UserValidationException`(`UserFieldError(field, message=i18nキー, params)`のリスト)。キーは`user.validation.<field>.<rule>`(`email.required|invalid|tooLong|duplicate`、`name.required|tooLong|controlCharacter`、`roleIds.required|blank|tooLong|unknown|selfChange`、`password.required|length`、`theme|fontSize|locale.required|invalid`、`field.unsupported`、`invitation.notInvited`、`self.disable`)。`params`は`tooLong`(`max`)・`password.length`(`min`・`max`)・`roleIds.unknown`(`roleId`)にだけ持たせた。
- DTO(`dto/`): `UserResponse`・`InviteUserRequest`・`UpdateUserRequest`・`AcceptInvitationRequest`・`UserPreferenceDto`は、サービスのシグネチャに必要なため、Step 12で作成した(計画ではStep 14)。`UpdateUserRequest`は、更新できない項目(email・statusなど)の名前を運ぶ`unsupportedFields`を持ち、JSONからの組み立て(未知のプロパティの収集)はStep 14のWeb層が行う。`AcceptInvitationRequest#toString`は、パスワード・氏名を出力しない。
- 例外(`exception/`)の追加: `OperatorUnresolvedException`(401)、`UserAccessDeniedException`(403)、`UserNotFoundException`(404)、`InvitationTokenNotFoundException`(404)、`UserValidationException`(422)。Step 14の`UserApiExceptionAdvice`は、これらに加えて、`InvitationMailException`(件名の拒否を含む)・`EmailLockTimeoutException`・`InvitationCapacityExceededException`・`HashCapacityExceededException`・行ロックの取得失敗(`PessimisticLockingFailureException`)を503に変換する想定。
- C11: `UserAccountLookupApi`・`UserAccount`(`status`は`UserStatus`の列挙、`passwordHash`は常にnull、`roleIds`はソート済みの和集合)は`com.mastersmith.usermanagement`直下。`revokeRefreshTokensOnDisable`は、計画どおり定義していない。`isDisabled`用に、`UserRepository#findStatusById`(列の値だけを問い合わせる射影)を追加した(呼び出し元のトランザクションの永続化コンテキストに残った古いエンティティではなく、常にDBの最新の値を返すため)。
- 起動時: `InitialAdminProperties`(`mastersmith.users.initial-admin.*`。record。コンストラクタでemail・パスワードの長さ・氏名を検証して、バインド時に起動を失敗させる。メッセージにパスワードを含めない)。任意項目`name`(既定`Administrator`)を追加した(計画にはなかった)。`InitialAdminBootstrap`(`ApplicationRunner`)は、`@SpringBootTest`の起動でも実行される(テスト用`application.yml`のダミー値で、各テストコンテキストが初期管理者を作成する。U4以外の既存テスト580件は影響を受けず、全体で緑)。
- `PermissionEngineApi#roleExists`(C10追補)と`PermissionEngineApiImpl`の実装(`roleId`がnull・空白なら`false`、それ以外は`RoleRepository#existsById`)。

### 設計上の判断(計画の解釈)

- **roleIdの実在検証の対象**: 更新(W3)では、**新たに付与するroleIdだけ**を`roleExists`で検証する(すでに付与済みのroleIdが、後からpermission-engine側で削除されても、name等の更新を妨げないため。functional-spec.mdのOpen Question「ダングリング参照」への対処)。招待(W1)は、指定されたすべてのroleIdを検証する。
- **自己のroleIds変更の判定**: 現在値と指定値を、順序と重複を無視した集合で比較する(付与の追加も削除も、変更として拒否し、`user.role.escalation.denied`を数え、警告ログにはuserIdだけを出す)。
- **メトリクス**: `user.role.escalation.denied`(`UserApplicationService`)と`user.invitation.accept.not_found`(`InvitationAcceptService`)は、テスト(Step 13)が確認するため、Step 12で実装した(Step 17の対象のうち、残りは`ObservationRegistry`によるスパンと`UserManagementNoLeakTest`)。
- **招待(W1)の順序**: 認可・入力の検証・正規化 → 排他(`EmailLockRegistry`) → 許可(`InvitationAdmission`) → `TransactionTemplate`{既存の検索 → 重複(422) → roleIdの実在検証(422) → 作成または再招待の条件付き更新 → メール送信} → 許可・排他の返却(try-with-resourcesの終了) → コミット後のイベント発行。計画の「既存検索・作成/再招待・roleExists・メール送信」のうち、roleExistsをDBの書き込みより前に置いた(結果は同じ、ロールバックの回数が減る)。一意制約違反(`DataIntegrityViolationException`)は、`email.duplicate`の422にする。ロック待ちタイムアウト(`PessimisticLockingFailureException`)は、そのまま伝播する(Step 14で503)。
- **再招待**: `findByEmail`で既存を検索 → `UserRepository#reinvite`(status=invitedが条件、0件なら`invitation.notInvited`の422) → 永続化コンテキストを破棄した後で再読み込みし、roleIdsをエンティティで置き換える。
- **招待受諾(W2)**: トークン検索 → 検証 → ハッシュ計算(許可の中、トランザクションの外) → `TransactionTemplate`{`activateInvitation`(0件なら404、ロールバック) → `UserPreference`の作成} → コミット後にACTIVATEDイベント。トークンの検索でstatusがinvitedでない場合も404。404の応答メッセージにトークンを含めない。
- **C11の`verifyPasswordHash`**: `findById`(activeかつ`passwordHash`が非nullのときだけ進む) → `PasswordHasher#verifyAndUpgrade`(検証と再計算を1回の許可の中で行う) → 更新が必要なら`REQUIRES_NEW`の`TransactionTemplate`で`updatePasswordHashIfUnchanged`(失敗は、警告ログ(userIdと例外の型名のみ)に記録して握りつぶし、戻り値に影響させない)。
- **初期管理者(W5)**: 存在確認 → ハッシュ計算(許可の中、トランザクションの外) → 1つのトランザクションでUserとUserPreference(既定値)を作成 → コミット後にBOOTSTRAPPED(actorは`system`)。一意制約違反は「既に存在した」として、何もしなかったことにする。起動時にハッシュの許可を取れない場合(`HashCapacityExceededException`)は、例外を握りつぶさず、起動を失敗させる。ログは「作成した」「既に存在したため何もしなかった」の事実のみ。

### テスト(Step 13)

- U4のテストは、全体で358件(Step 1〜11の174件に、Step 12〜13の184件を追加)。他ユニットを含む全体は767件で、失敗0。U4のパッケージの行カバレッジは96.9%(JaCoCo、全体91.2%)。
- 追加したテスト: `UserAuthorizerTest`・`HeaderCurrentOperatorProviderTest`・`UserInputValidatorTest`(テーブル駆動)、`UserApplicationServiceTest`(テーブル駆動の認可拒否専用テスト: 操作(一覧・更新・無効化)×操作者(未解決4種・権限なし)、自己のroleIds変更・無効化の拒否、roleExistsがfalse、更新不可項目の指定、冪等な再DELETE、招待の取消)、`UserApplicationServiceConcurrencyTest`(同一Userへの同時更新のbeforeValueの連鎖)、`InvitationFacadeTest`(18件: 作成・再招待・重複・ロールバック・排他/許可の返却の時機(コミット後・トランザクション外)・許可の満杯・排他の待機超過・同一emailの直列化・他のemailの非干渉・同一emailの待機者が許可を占有しないこと・認可/検証が排他・許可より前・再招待と受諾の競合・ロック待ちタイムアウト・一意制約違反)、`InvitationAcceptServiceTest`(並行受諾・404の一本化・未知のトークンでハッシュを計算しないこと・検証・原子性・許可待ちの失敗)、`UserAccountLookupServiceTest`(和集合・正規化・active以外はfalse・129文字以上は計算しない・ハッシュ更新(古いパラメータ・競合・更新の失敗・読み取り専用のトランザクションの内側)・isDisabledの最新性)、`InitialAdminBootstrapTest`・`InitialAdminPropertiesTest`(安全失敗)、`UserPreferenceServiceTest`、`UserManagementStartupTest`(`@SpringBootTest`: 起動時の初期管理者の作成とC11のBean結線)、`PermissionEngineApiImplTest`へのroleExists 3ケース。
- 並行・排他のテストは、固定の`sleep`に頼らず、ラッチと、スレッドの状態の待ち合わせ(条件が成り立つまでの短い間隔のポーリング)で組み立てた。

### 他ユニットへの変更(計画で許可された最小限)

- `permission/PermissionEngineApi.java`(`roleExists`の追加)、`permission/service/PermissionEngineApiImpl.java`(実装)、`permission/service/PermissionEngineApiImplTest.java`(3ケース)。指示に従い、変更したこの3ファイルに、google-java-formatを直接適用した。`PermissionEngineApiImpl.java`は、HEADの時点で未整形だった箇所(長い文字列リテラル・Javadocの折り返しの約20行)も整形された(意味の変更はない)。

## Step 14〜15(API層の実装とテスト)で判明した事実・計画との差異

### 実装の構成(`com.mastersmith.usermanagement.web`)

- `UserController`(`GET/POST /api/users`、`PUT/DELETE /api/users/{userId}`)、`InvitationAcceptController`(`POST /api/users/invitations/{token}/accept`、認証不要、操作者は解決しない)、`MePreferencesController`(`GET/PUT /api/me/preferences`)。コントローラは、`CurrentOperatorProvider`で`Operator`を解決してサービスへ渡すだけで、認可はサービスの入口で行う。応答は`UserResponse`・`UserPreferenceDto`のみ(`passwordHash`・`invitationToken`を持たない型)。POSTの成功は201(本文は`UserResponse`)、DELETEは204、それ以外は200。
- `UpdateUserRequestFactory`(パッケージ内部): `PUT /api/users/{userId}`のボディを`Map<String,Object>`として受け、`name`・`roleIds`以外のキーを`UpdateUserRequest#unsupportedFields`へ集める(未知のプロパティの収集)。型が違う値(`name`が文字列でない、`roleIds`が配列でない、配列の要素が文字列でない)は、nullとして扱い、サービスの検証が`name.required`・`roleIds.required`・`roleIds.blank`の422にする(認可の401・403より前に、型の不備で422にならないようにするため)。
- `RequestSizeLimitFilter`(`@Component`、`@Order(Ordered.HIGHEST_PRECEDENCE)`、`OncePerRequestFilter`): 対象は`/api/users`・`/api/users/**`・`/api/me/preferences`のみ。パスの照合は、Spring MVCと同じ`PathPattern`(`ServletRequestPathUtils.parse`)で行い、セミコロン・符号化された文字の扱いを揃えた(`UrlPathHelper`はSpring 7で非推奨のため使わない)。`Content-Length`が64KiB(65536バイト)を超えれば、内容を読まずにフィルタ内で413(`application/problem+json`を手で書く。リクエストのパスは含めない)。それ以外は、`HttpServletRequestWrapper`で入力ストリーム・リーダーを、読み込み量を数えるストリームで包み、65536バイトを超えた時点で`RequestBodyTooLargeException`(`IOException`)を投げる(ちょうど65536バイトは通す)。`Content-Length`の宣言より多く送られた場合も同じ。
- `RequestBodyTooLargeException`(`exception/`、`IOException`): JSONの読み取りの中で`HttpMessageNotReadableException`に包まれる。
- `UserApiExceptionAdvice`(`@RestControllerAdvice(assignableTypes = {UserController, InvitationAcceptController, MePreferencesController})`、`@Order(Ordered.HIGHEST_PRECEDENCE)`)。対応: `OperatorUnresolvedException`→401、`UserAccessDeniedException`→403、`UserNotFoundException`・`InvitationTokenNotFoundException`→404(後者は、トークンによらず同一の応答)、`UserValidationException`→422(`errors[]`に`field`・`message`(i18nキー)、`params`は空でなければ)、`HashCapacityExceededException`・`InvitationCapacityExceededException`・`EmailLockTimeoutException`・`InvitationMailException`(`SubjectRejectedException`を含む)・`PessimisticLockingFailureException`(`CannotAcquireLockException`を含む)・`CannotCreateTransactionException`→503、`RequestBodyTooLargeException`と、原因の連鎖に`RequestBodyTooLargeException`を含む`HttpMessageNotReadableException`→413、その他の`HttpMessageNotReadableException`→400(読み取れないJSON。詳細は含めない)。ステータス・タイトル・詳細は固定の文言で、入力値・例外のメッセージ・型名・スタックトレースを含めない(型名だけを、503のときに警告ログへ出す)。汎用の`Exception`のハンドラは置いていない(他のエラーは、共通基盤の担当)。
- `CannotCreateTransactionException`(接続プールの枯渇など)を503にするのは、C5の追補には明記していない、実装上の追加。

### `instance`(ProblemDetails)

- `instance`は、リクエストの生のパスではなく、マッチしたルートのテンプレート(`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`)を用いる。ハンドラに到達する前の失敗などで得られない場合は、招待受諾のパスならテンプレート、それ以外はパス。Spring MVCは、`instance`がnullだと生のパスを入れるため、必ず設定している。
- **URIとして`{}`は符号化されるため、招待受諾APIの`instance`は`/api/users/invitations/%7Btoken%7D/accept`(テンプレートの符号化された形)になる**(設計・C5追補の`/api/users/invitations/{token}/accept`の、JSON上の表現)。`PUT /api/users/{userId}`なら`/api/users/%7BuserId%7D`。フィルタが手で書く413には、`instance`を含めない。

### 他ユニットへの影響の確認

- `RequestSizeLimitFilter`は`Filter`の`@Component`、`UserApiExceptionAdvice`は`@RestControllerAdvice`のため、他ユニットの`@WebMvcTest`のスライスにも読み込まれるが、フィルタは対象外のURLを素通しし(`shouldNotFilter`)、アドバイスは`assignableTypes`でU4のコントローラに限られ、依存する部品もない。他ユニットの`@WebMvcTest`・`@SpringBootTest`を含む全体テスト(851件)が緑であることを確認した。テストで、U4以外のコントローラの例外がアドバイスに変換されないこと、他ユニットのURLが64KiBを超えても413にならないことも確認している。
- `@WebMvcTest`のスライスは`@Component`(`HeaderCurrentOperatorProvider`)を読み込まないため、U4のコントローラのテストは`@Import(HeaderCurrentOperatorProvider.class)`で読み込む。

### テスト(Step 15)

- 追加したテスト(`web/`の84件と、テスト支援2クラス`ChunkedRequests`・`JsonBodies`): `UserControllerTest`(22件: 200/201/204、**全エンドポイントの401・403**、404、422(フィールド単位・i18nキー・入力値を含まない)、503(6種)、413(`Content-Length`超過・チャンク転送・ちょうど64KiB)、400、`instance`のテンプレート化、応答に`passwordHash`・`invitationToken`が含まれないこと、未知のプロパティの収集)、`InvitationAcceptControllerTest`(8件)、`MePreferencesControllerTest`(6件: 他人の設定を操作できないこと(ボディ・クエリの`userId`は無視)、activeRoleIdに依存しないこと)、`RequestSizeLimitFilterTest`(22件)、`UserApiExceptionAdviceTest`(20件: 例外とステータスの対応のテーブル駆動、原因の連鎖の判別、`instance`のフォールバック、対象の限定、`@Order`)。
- 追加(計画外): `UserApiIntegrationTest`(6件、`@SpringBootTest`+`@AutoConfigureMockMvc`): 実際のフィルタチェーン・例外変換・サービス・H2で、未認証の401・権限なしの403を全エンドポイントで確認し、招待→受諾(認証不要)→表示設定→更新(更新不可項目は422)→無効化の流れで、応答・ProblemDetailsに`passwordHash`・招待トークンが現れないこと、認証前のボディが413になること(フィルタの順序)を確認する。`PermissionEngineApi`と`InvitationMailer`はモック。
- チャンク転送のMockMvc上の再現には、`Content-Length`を返さない`MockHttpServletRequest`の派生を返す`RequestPostProcessor`(`ChunkedRequests.chunked()`)を用いた。
- U4のテストは合計442件(Step 12〜13までの358件に84件を追加)、全体は851件で失敗0。U4の行カバレッジは96.7%(`web`パッケージは114/120行)、全体91.4%。

## Step 16以降への引き継ぎ

- 未着手: audit-loggingへの`UserChangedEventListener`と`AuditLogEventMapper.fromUserChangedEvent`の追加、`UserChangedEventAuditIntegrationTest`(Step 16)、可観測性のスパン(`ObservationRegistry`)と`UserManagementNoLeakTest`(ログ・メトリクスのラベル・ProblemDetails・イベントのスナップショットに、パスワード・トークン・`passwordHash`・メールアドレス・氏名が現れないこと。ProblemDetailsとイベントのスナップショットは、Step 15までのテストで既に確認済み)(Step 17)、環境・ビルドの確認(Step 18)、ドキュメント(Step 19)。
- Step 17で、コントローラ・サービス・メール送信・ハッシュ計算・C11に、`ObservationRegistry`による観測(スパン)を付ける。スパンの属性にメールアドレス・氏名・件名・トークンを含めない。招待受諾APIのスパン名・URL属性はルートのテンプレートにする(共通基盤への要求)。
- U5(authentication-service)の実装時に、`HeaderCurrentOperatorProvider`を、検証済みトークンのクレームから読む実装へ差し替える(`CurrentOperatorProvider`を実装する)。共通基盤の汎用ハンドラ・認証フィルタチェーンの導入時に、`RequestSizeLimitFilter`が認証フィルタより前に置かれていること(`Ordered.HIGHEST_PRECEDENCE`)を、フィルタの順序として再確認する。
