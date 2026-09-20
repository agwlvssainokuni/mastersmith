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

## Step 14以降への引き継ぎ

- Step 14(API層)が使う部品: `CurrentOperatorProvider`(暫定実装`HeaderCurrentOperatorProvider`)でリクエストから`Operator`を解決し、`UserApplicationService`・`InvitationFacade`・`InvitationAcceptService`・`UserPreferenceService`へ渡す。応答のDTOは`dto/`。例外の型と、対応するHTTPステータスは、上の「例外」の項を参照。
- 未着手: コントローラ(`UserController`・`InvitationAcceptController`・`MePreferencesController`)、`RequestSizeLimitFilter`、`UserApiExceptionAdvice`、audit-loggingへの`UserChangedEventListener`とマッパーの追加(Step 16)、可観測性のスパンと`UserManagementNoLeakTest`(Step 17)、環境・ビルドの確認(Step 18)、ドキュメント(Step 19)。
- Step 18の注意: 他ユニットの既存ファイルが`spotlessCheck`で不合格のため(前項)、`spotlessCheck`の判定は、U4のファイルに限って行う必要がある。
