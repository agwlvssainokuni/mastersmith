## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-20T10:32:47Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | backend/src/main/java/com/mastersmith/usermanagement/security/HeaderCurrentOperatorProvider.java > class(無条件の`@Component`)、およびaidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/code-generation/code-generation-plan.md > 前提事項2 | 暫定の`HeaderCurrentOperatorProvider`は、無条件の`@Component`として本番の起動にも登録される。現時点でアプリケーションには認証フィルタがなく、クライアントが`X-User-Id`と`X-Active-Role-Id`に任意の値を送れば、`UserAuthorizer.requireUserAdmin`が評価する`canAccessScreen(activeRoleId, "user-management")`をそのまま通せる。管理者ロールを名乗るだけで、招待・任意ロールの付与・他者のroleIds変更・無効化ができ、監査ログの`actorUserId`も偽装できる。`UserAuthorizer`は、`activeRoleId`が`userId`の保有ロールであるかを確認しない(招待者のロール確認をC10/C11で行う処理がない)。前提事項2の「暫定実装であっても実効権限の再検証は弱まらない」は、ロールがクライアント申告のため成立しない。既知の意図的な差異であることは承知しているが、差し替えの担保(起動時の停止・明示的なオプトイン・U5側の必須の義務化)が成果物のどこにもなく、project.mdのForbidden(権限昇格の禁止)とMandated(サーバー側での実効権限の再検証)との関係でリスクが残る | (a)暫定プロバイダを、明示的なオプトインの設定(例: `mastersmith.users.operator.header-provider.enabled`、既定false)または開発・テスト用プロファイルに限り登録し、有効化されていない状態で`CurrentOperatorProvider`のBeanが存在しなければ起動を失敗させる。(b)`UserAuthorizer`で、操作者のuserIdが`activeRoleId`を実際に保有すること(直接付与分+Group経由分)を、U4内部で確認する。(c)U5の実装で本クラスを撤去することを、C5/C11の追補または`code-summary.md`の「既知の課題」に、U5側の必須の受け入れ条件として明記する | New |
| R-02 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/code-generation/traceability.json > coverage[id=FR2.3]、coverage[id=NFR2.10]、coverage[id=NFR1.1] | FR2.3は「無効化と同時にリフレッシュトークンを即時失効」を含むが、`revokeRefreshTokensOnDisable`は未実装で、U4には失効のフックも、U5が`isDisabled`を再認証時に必ず呼ぶことを保証・検証する手段もない。にもかかわらず`OK`(target=`UserAccountLookupService.java`)としており、過大申告になる。NFR2.10のtarget`MailTemplateRenderer.java`は、リンクの形式(`#token=`)を持たない(実際は`InvitationMailer.ACCEPT_PATH`・`inviteLink`)ため、対応が一致しない。NFR1.1のtarget`UserRepositoryCustomImpl.java`(行ロックの実装)は、「認可1回・一覧の列限定」に対応しない(対応は`UserRepository.findAllListRowsOrderByEmail`・`UserApplicationService`) | FR2.3は`OK`ではなく、部分達成(U5への依存の残り)であることが分かるステータスまたは注記にし、targetにU5側の残作業を書く。NFR2.10のtargetを`InvitationMailer.java`(`inviteLink`)へ、NFR1.1のtargetを一覧の射影クエリを持つ`UserRepository.java`へ直す | New |
| R-03 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/code-generation/code-summary.md > 「ビルド・設定(Step 2・3)」 | 計画のStep 2は、mustacheエンジンの座標・依存・ライセンス・APIを実物で再確認し、結果を`code-summary.md`へ記録することを求めるが、`code-summary.md`にはコミットの固定値と`HTMLエスケープの既定`の記述しかなく、詳細は作業メモ(`code-generation-notes.md`)への参照だけである。あわせて、`settings.gradle.kts`の`includeBuild("external/java-mustache-processor")`により、サブモジュールを初期化していないクローンでは、他ユニットを含む全てのGradle実行が失敗するが、その前提(`git clone --recurse-submodules`または`git submodule update --init`)がドキュメントにない | 再確認した座標・依存・ライセンス・APIの結果を`code-summary.md`(または成果物として残る文書)へ記載する。サブモジュールの初期化手順を、ビルド手順(`unit-test-instructions.md`のコマンドの前提)へ追記する | New |
| R-04 | Minor | backend/src/main/java/com/mastersmith/usermanagement/service/UserInputValidator.java > `normalizeRoleIds`、およびbackend/src/main/java/com/mastersmith/usermanagement/service/InvitationFacade.java > `validateRolesExist` | `roleIds`の件数に上限がなく、上限は`RequestSizeLimitFilter`の64KiBのみである。招待では、件数分の`roleExists`(`existsById`)が、同一emailの排他・招待の許可(上限5)・DB接続を保持したトランザクションの内側で直列に実行され、更新でも行ロックを保持したまま実行される。数千件のroleIdで、許可と接続を長時間占有できる(NFR4.2の資源占有の想定外)。設計(nfr-design)にも件数の上限がない | `roleIds`の件数の上限(例: 100件、設定値)を`UserInputValidator`で検証して422にする。または、実在検証を`findAllById`の一括問い合わせにする | New |
| R-05 | Minor | backend/src/main/java/com/mastersmith/usermanagement/config/UserManagementProperties.java > `Hash`のコンパクトコンストラクタ | Argon2idのパラメータの下限が`memoryKib>=8`・`iterations>=1`と極めて低く、本番の設定ミスで、パスワードのハッシュが、OWASPの推奨(m=19456KiB、t=2)を大きく下回っても、起動が通る。「設定定義自体の誤りは起動時にfail fastする」(project.md Mandated)の趣旨と、`needsUpgrade`が弱いハッシュを検出するだけで設定の弱さは検出しない点に対して、防御が薄い | 本番の既定の下限(例: m>=19456・t>=2)を強制し、テストだけが、テスト用の設定で下限を下げられるようにする。または、下限を下回る場合に起動時のWARNを出す | New |
| R-06 | Minor | backend/src/main/java/com/mastersmith/usermanagement/mail/InvitationMailer.java > `awaitResult`(タイムアウト時の`future.cancel(true)`) | 10秒の打ち切りで失敗として扱いロールバックしても、JavaMailのソケット入出力は中断できないため、送信スレッドは継続し、メールが実際に配送されうる。その場合、DBには存在しないトークン(新規招待)、または、旧トークンが有効なまま届く新トークン(再招待)を含むメールが届く。影響は招待リンクが404になる程度で軽微だが、設計・`code-summary.md`の残余リスクのどちらにも記録がない | この「打ち切り後の配送」を残余リスクとして`code-summary.md`に記録する(管理者は再招待すれば足りる旨を含む) | New |
| R-07 | Minor | backend/src/main/java/com/mastersmith/permission/service/PermissionEngineApiImpl.java > ログ文字列(2箇所)・Javadoc(`afterAssignment`)、backend/src/test/java/com/mastersmith/audit/web/AuditLogControllerTest.java > `excludeTheRowRecordedAtStartup` | (1)`roleExists`の追加と無関係な、ログ文字列・Javadocの再整形が、他ユニットのファイルに混ざっている(挙動の変更はなく、`code-summary.md`にも記載あり。ただし計画の「既存メソッドは変更しない」・差分の最小化の趣旨に反する)。(2)初期管理者の自動作成(`BOOTSTRAPPED`)が、`@SpringBootTest`の全コンテキストの起動時に監査ログへ1行を確定するようになり、今後、他ユニットのテストで「空の監査ログ」を前提にするたびに、同種の除外が必要になる。専用の無効化の設定がない | (1)`PermissionEngineApiImpl.java`の整形のみの差分を、別コミットへ分けるか、次の整形のコミットへ移す。(2)テスト用に、初期管理者の作成を無効化する設定(例: `mastersmith.users.initial-admin.enabled`)を検討するか、テストの共通の注意事項として`unit-test-instructions.md`に記載する | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `./gradlew :backend:test --rerun --tests "com.mastersmith.usermanagement.*" --tests "...UserChangedEventListenerTest" --tests "...AuditLogEventMapperTest" --tests "...UserChangedEventAuditIntegrationTest" --tests "...PermissionEngineApiImplTest"` | BUILD SUCCESSFUL | U4と追加分のテストが通ることを再現した |
| `./gradlew :backend:test --rerun`(全体、テスト結果のXMLを集計) | 880件・失敗0・エラー0・スキップ0 | `code-summary.md`の「全体880件、失敗0」と一致。他ユニットのテストを壊していない |
| `./gradlew :backend:checkstyleMain :backend:checkstyleTest` | 合格(UP-TO-DATE) | 静的検査に問題なし |
| JaCoCo(`backend/build/reports/jacoco/test/jacocoTestReport.xml`) | U4と監査イベントのパッケージの行カバレッジは1217/1253(約97.1%)、閾値80%は緩められていない(`backend/build.gradle.kts`) | `code-summary.md`の96.9%とほぼ一致。未カバー行は`CountingServletInputStream`の一部・`EmailLockRegistry`の割り込み分岐などで、重大な欠落はない |
| `source-manifest.json`と`git diff --name-only`(基準: `5cbaa61`)の突き合わせ | 申告と実際の変更が完全に一致(申告漏れ・不要な申告なし) | 本ユニットと無関係なパスはない |
| `git submodule status` | `external/java-mustache-processor`が、記録のコミット(`8d44c36b`、0.1.0)で固定されている | 固定は確認できた(初期化手順の記載はR-03) |
| リポジトリ内の認証情報の確認(`application.yml`(main・test)) | 実値・既定値なし(環境変数のプレースホルダ、テストは実在しないダミー値のみ) | NFR2.8に適合 |

### Summary

資源の取得順序(排他→許可→DB接続、ハッシュ計算はトランザクションの外)、コミット後の`REQUIRES_NEW`のイベント発行(失敗をHTTPの結果に影響させない)、トークン・パスワードの非露出、競合の扱い(条件付き更新・行ロック)、C5/C10/C11への適合は、コードとテストで検証でき、反証できなかった。Criticalはなく、Majorは暫定の操作者プロバイダの担保がない1件(R-01)のみで、他はMinorのため、READYとする。ただしR-01は、U5の結線までの間、`/api/users`系が事実上無認証になる点で、ゲートで人が重みを判断すべき事項である。
