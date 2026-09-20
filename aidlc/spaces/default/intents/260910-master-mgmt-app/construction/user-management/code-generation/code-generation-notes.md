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

## Step 12以降への引き継ぎ

- 実装済みで、Step 12以降が使う部品: `PasswordHasher`(`hash`・`verify`・`needsUpgrade`・`verifyAndUpgrade`)、`PasswordPolicy`、`HashConcurrencyLimiter`、`EmailLockRegistry`(`acquire`→`Held`)、`InvitationAdmission`(`acquire`→`Permit`)、`InvitationMailer#send`、`UserChangedEventPublisher#publish`、`UserRepository`(`findByEmail`・`findByInvitationToken`・`findAllSummariesOrderByEmail`・`findByIdForUpdate`・`activateInvitation`・`reinvite`・`updatePasswordHashIfUnchanged`)、`UserManagementProperties`・`InvitationMailProperties`。
- 未着手: `InitialAdminProperties`(`mastersmith.users.initial-admin.*`のバインド。`application.yml`にはプレースホルダを置いた)、`UserAccountLookupApi`、`PermissionEngineApi.roleExists`、各サービス・コントローラ、`RequestSizeLimitFilter`、`UserApiExceptionAdvice`、audit-loggingへの追加、可観測性(スパン)。
