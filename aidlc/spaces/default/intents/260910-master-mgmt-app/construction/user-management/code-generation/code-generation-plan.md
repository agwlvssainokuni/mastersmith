# Code Generation Plan — user-management (U4)

user-managementは、ユーザー(`User`)の招待・招待受諾・更新・無効化・初期管理者の自動作成、ユーザー単位の表示設定(`UserPreference`)、およびauthentication-service(U5)向けの内部インタフェース(C11 `UserAccountLookupApi`)を担う(FR2.1〜FR2.6・FR2.8、FR9.1、FR10.1、FR1.6の本ユニット分)。本Boltで、REST API(C5: `/api/users`系・招待受諾・`/api/me/preferences`)、C11の実装、招待メール(自作mustacheエンジンによるHTMLメール・SMTP送信)、`UserChangedEvent`の発行(コミット後・独立トランザクション)、Argon2idのパスワードハッシュと同時計算の制御、起動時の初期管理者作成を実装する。

権限判定そのものは自前で持たず、`PermissionEngineApi`(C10)へ委譲する。他ユニット(permission-engine・audit-logging)には、下記「前提事項」に挙げた最小限の追加のみを行う。

## 前提事項(Plan Approval時に確認いただきたい設計判断)

1. **契約追補・機能設計への追補を、最初の作業(Step 1)として反映する**(ユーザー決定済み。Contract Designの再実施はしない)。`inception/contract-design/contract-summary.md`のC5・C10・C11と、`construction/user-management/functional-design/`(`functional-spec.md`・`rules.md`)へ、次を追補する。
   - C5: `POST /api/users`に任意項目`locale`(ja/en、省略時ja)、招待受諾APIに任意項目`theme`/`fontSize`/`locale`、レスポンスコード(401・404・PUT/DELETE/受諾の422、招待の503・受諾の503・413)、`PUT /api/users/{userId}`のrequestBody(`name`・`roleIds`)、`errors[].message`をi18nキーとし、必要なときだけ`errors[].params`(例: `{"min":8,"max":128}`)を持たせる方針。
   - C10: `roleExists(String roleId): boolean`の追加(Q6、BR4.5)。
   - C11: `UserAccount.roleIds`は直接付与分とGroup経由分の和集合、`passwordHash`はnull。`verifyPasswordHash`はログイン成功時のハッシュ更新(書き込み)を伴う副作用があり、`REQUIRES_NEW`で行うこと。ハッシュ計算の待機超過は`HashCapacityExceededException`で表す(HTTPへの変換は呼び出し元)。呼び出し元(U5)は、C11を、トランザクションの外で呼ぶこと(NFR Designの保留9番。R-11の既定の解決)。
   - 機能設計: W1・BR4.1に`locale`、BR4.15に`name`の最大長100文字(`[assumption]`)と制御文字(CR/LFなど)の禁止、BR4.5に`roleExists`の使用。
   - 追補の記録先は、`contract-summary.md`のC5・C10・C11の各節と、`functional-spec.md`・`rules.md`の末尾の「Code Generation着手時の追補」節とする。既存の記述は書き換えず、追補として追記する(audit-loggingの503追補と同じ流儀)。
2. **操作者の取得(`CurrentOperatorProvider`)は暫定実装とする**。authentication-service(U5)は未実装で、Spring Securityのセキュリティコンテキストも存在しない。schema-introspectorの`ActiveRoleResolver`/`HeaderActiveRoleResolver`と同じ流儀で、`CurrentOperatorProvider`(インタフェース)と、暫定実装`HeaderCurrentOperatorProvider`(リクエストヘッダー`X-User-Id`・`X-Active-Role-Id`から読む。JWTの検証は行わない)を置く。U5の実装時に、検証済みトークンのクレームから読む実装へ差し替える。認可判定自体(`canAccessScreen`)は必ずサーバー側で行うため、暫定実装であっても実効権限の再検証(project.md Mandated)は弱まらない。ヘッダーがない場合は401とする。
3. **audit-logging(U7)に、`UserChangedEvent`の購読を追加する**(NFR Designの保留10番の具体化)。`audit`パッケージに`UserChangedEventListener`(既存3リスナーと同じ同期`@EventListener`+全体try-catch)を追加し、`AuditLogEventMapper`に`fromUserChangedEvent`を追加する。対応付けは、`targetType`=`User`、`targetId`=userId、`operationType`=`operation`名(INVITED/ACTIVATED/UPDATED/DISABLED/BOOTSTRAPPED)、`beforeValue`/`afterValue`=スナップショット(`name`・`email`・`status`・`roleIds`)。`actor`は、INVITED・ACTIVATED・UPDATED・DISABLEDではuserIdとして`actorUserId`へ、BOOTSTRAPPEDでは`system`を`actorRaw`へ入れる。リスナーは`repository.save`のみで、呼び出し元(発行側が開く`REQUIRES_NEW`)のトランザクションに参加する。既存の3リスナーとマッパーの既存メソッドは変更しない。
4. **permission-engine(U3)に、`roleExists`を追加する**(C10追補)。`PermissionEngineApi`へ`boolean roleExists(String roleId)`を追加し、`PermissionEngineApiImpl`が`RoleRepository.existsById`で実装する。既存メソッドは変更しない。
5. **自作mustacheエンジンは、Gitサブモジュール+`includeBuild`で取り込む**(ユーザー決定済み)。リポジトリ`https://github.com/agwlvssainokuni/java-mustache-processor`を`external/java-mustache-processor`にサブモジュールとして追加し、コミットを固定する。`settings.gradle.kts`に`includeBuild("external/java-mustache-processor")`を追加し、`backend/build.gradle.kts`から`implementation("cherry.mustache:cherry-mustache-core")`で参照する。エンジン側のビルド設定(OWASP依存関係チェックのプラグインを含む)は、エンジン側の設定のまま使い、このプロジェクトのビルドには混ぜない。座標(group `cherry.mustache`、version `0.1.0`)・依存・ライセンス(Apache-2.0)・APIは、公開ファイルの要約による想定であり、取り込み時に実物で再確認する。**HTMLエスケープの既定動作は不明**のため、`MailTemplateRendererTest`で実際に確認する。エンジンが既定でエスケープしない場合は、`MailTemplateRenderer`が、差し込む値をHTMLエスケープしてから渡す。サブモジュールの取得(ネットワーク)に失敗した場合は、作業を止めて相談する。
6. **招待メールはHTMLのみを送り(テキスト版は送らない)、件名はHTMLの`<title>`から、自前の簡易処理で取り出す**(ユーザー決定済み)。HTMLパーサは使わない。文字参照(数値参照・主要な名前つき参照)のデコードも自前で行うため、デコードの漏れをテストで確認する。
7. **NFR Designのレビュー残り(R-11〜R-15)は、既定の解決を、この計画の前提とする**(ユーザー決定済み)。(a) C11の`verifyPasswordHash`は、U5がトランザクションの外で呼ぶ(前提事項1のC11追補)。(b) コミット後のイベント発行は、排他・許可の返却の後に行う(Step 12の`InvitationFacade`)。(c) 内部設定DBの接続プールの最大サイズは、通常の同時処理数50、招待の上限5、入れ子の接続の余裕を見込んだ60とし、`application.yml`の`spring.datasource.hikari.maximum-pool-size`に置く。(d) 永続化はSpring Data JPA(`spring.jpa.open-in-view=false`は設定済み)。(e) 認可・emailの正規化は、排他・許可の取得より前に行う。(f) DBのロック待ちタイムアウトは15秒(`mastersmith.users.db-lock-timeout`で変更可)。(g) `traceability.json`は、`ApiExceptionHandler`の旧名を`UserApiExceptionAdvice`として扱い、起動時にハッシュの許可を取れない場合(初期管理者の作成)は、起動失敗とする(待機2秒を超えた場合も、503ではなく起動失敗)。
8. **共通基盤(ユニットをまたぐ横断部品)の実装は、本Boltの対象外とする**。次は、logical-components.mdの「横断的な部品の所有」により、共通基盤(authentication-service・packaging等)の所有であり、本Boltでは実装しない: `RequestLogFilter`と観測の規約(`ServerRequestObservationConvention`)、`Referrer-Policy`ヘッダー、認証フィルタチェーン、汎用の例外ハンドラの基底、構造化ログ(JSON)の出力設定、トレース・メトリクスのエクスポート方式(保留11・12・15番)。本Boltは、U4のコード・ログ・メトリクス・ProblemDetailsの`instance`に、招待トークンの実際の値を出さないことだけを担保し、テストで確認する。接続プールの最大サイズ(前提事項7(c))のみ、`application.yml`へ最小限を設定する。
9. **C11の`revokeRefreshTokensOnDisable`は、本Boltでは実装しない**。契約上は呼び出し方向が曖昧で未確定(functional-spec.md Open Questions)であり、FR2.3の「以後の再認証不可」は、`isDisabled`が常に最新のstatusを返すこと(BR4.13)で担保する。`UserAccountLookupApi`は`findByEmail`・`verifyPasswordHash`・`isDisabled`の3メソッドで定義し、`revokeRefreshTokensOnDisable`は、U5の機能設計で方向が確定した時点で追加する(契約からの意図的な差異)。
10. **物理名・永続化の方針**: テーブルは`users`・`user_role`(直接付与のroleId、`User`の要素コレクション)・`user_preference`とし、マイグレーション`V4__create_user_management.sql`を追加する。`email`と`invitation_token`に一意インデックスを設ける(`invitation_token`は非nullのときだけ一意)。`roleId`は「不透明な文字列参照」の慣例に従い、物理FK制約を設けない(実在検証はアプリケーション層、BR4.5)。`User`の更新・無効化は、行ロック付き読み取り(悲観的書き込みロック)で直列化する。
11. **ハッシュのベンチマーク(NFR1.2、p95が300ms以下)は、通常のテスト実行に含める**。実行環境により結果が揺れうるが、目標値を緩めることはしない。基準を満たさない場合は、ギャップとして報告する(NFR1.2)。計測結果(CPUコア数・メモリ・p95)は`code-summary.md`に記録する。
12. **フロントエンド・複数プロファイル横断E2Eは、本Boltの対象外**。U4はバックエンドで、UIを持たない。招待リンクの受諾画面(U12)への要求(保留8番)は契約追補として記録するのみ。`team.md`必須テスト種別のうち、(a)安全失敗・(c)認可拒否は本Boltで実施し、(b)複数プロファイル横断E2Eは対象外(list-engine・record-edit-engine・frontend-uiが未実装)。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "custom",
  "source": "team",
  "ordering": "原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを",
  "scope": "config-driven-admin-mvp",
  "test_strategy": "comprehensive",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: custom\n- **Ordering**: 原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを\n  作成・実行する(test-after)。ただし権限判定ロジック(ロール階層継承・主権限\n  FULL/READ/NONE/指定なし・補助権限CREATE/DELETEの解決処理)に限り、実装に先立って\n  権限マトリクス(組み合わせケース)を洗い出してから実装する、test-first/ATDD寄りの\n  例外的な進め方を認める(インタビューQ4: A. 全体はtest-afterを基本方針とする。\n  Q5: A. 権限判定ロジックだけこの例外を認める)。\n- テストはすべてのBoltにおいて第一級の成果物として扱う。\n- **カバレッジフロア**: スコープ `config-driven-admin-mvp`(`mvp` 系)の既定に従い、\n  80%行カバレッジとマージ前のCI実行を課す。\n- **権限・監査ログの追加合格条件**: 権限判定ロジックおよび監査ログ記録の実装に限り、\n  80%行カバレッジに加えて「主要な権限マトリクスの組み合わせを網羅するテーブル駆動\n  テスト」を追加の合格条件とする(80%行カバレッジという基準そのものを緩めるのではなく、\n  この2箇所にのみ上乗せする)(インタビューQ6: A)。\n- **Test Strategy「Comprehensive」の具体的内容**: 単体テストに加え、統合テスト・\n  画面操作を通したE2Eテスト・設定ファイルの契約テスト(config-schemaの形式チェック)を\n  標準として含む。負荷・性能テストは既定には含めない(インタビューQ7: A, B, C選択、\n  Dは対象外)。\n- **設定駆動(config-driven)特有の必須テスト種別**: 「アプリ本体を1つのまま設定の\n  入れ替えだけで複数業務に転用できる」という成功定義を検証するため、以下を必須とする\n  (インタビューQ8: A, B, C選択)。\n  - (a) 設定ファイルが不正・不完全なときにアプリが安全に失敗する(バリデーションエラー\n    になる)ことを確認する安全失敗/バリデーションテスト\n  - (b) 実際に2種類以上の異なる業務ドメインの設定プロファイル(例: 商品マスタ用・\n    蔵書マスタ用)で同一の操作シナリオを流し、「設定を差し替えるだけで動く」ことを\n    確認する複数プロファイル横断E2Eテスト\n  - (c) 権限が不足する操作は確実に拒否されることを狙って確認する認可拒否\n    (negative-authorization)専用テスト\n  - **監査ログ完全性テスト(更新・作成・削除の全操作が監査ログへ記録されることの網羅的\n    検証)は、今回のMVPスコープでは必須としない**。これはインタビューで2度確認した上での\n    意図的なスコープ判断であり(Q8選択肢Dは不採用)、見落としではない。\n- Build and Test ステージは、定義済みカバレッジフロアと確認済み品質目標の充足を検証する。\n  これらのステップを通過させるために基準を緩めることはしない。"
    }
  ],
  "obligations": {
    "strategy": "comprehensive",
    "strategy_volume": [
      "Ten to fifteen tests per component.",
      "Unit, integration, and E2E tests.",
      "Add performance and security tests when NFRs demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "custom",
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
      "Custom ordering - 原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを",
      "Implementation and tests - preserve that exact ordering; do not convert it to layer-local TDD.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:9ff5f4a3117c43915b0647dc8da168bba8fefba2f289aa7ad964e81afab834bc",
  "contract_sha256": "sha256:4fa8d24c65e3a6e1326c42865b60dbacdbf8f2481aebedce163e1f10630b41d8"
}
```

## 対象外レイヤーの扱い

- **フロントエンド層**: 前提事項12のとおり対象外。
- **複数プロファイル横断E2Eテスト**: 前提事項12のとおり対象外。認可拒否(negative-authorization)専用テスト(必須テスト種別(c))は、コントローラ・サービスのテストとして実施する(Step 13・Step 15)。
- **共通基盤の各部品**: 前提事項8のとおり対象外。

## Story-to-Code Step Traceability

user-storiesステージはSKIP対象(`project.md`学習事項)のため、`requirements.md`のFR ID・`functional-design/rules.md`のBR ID・`nfr-requirements`/`nfr-design`のNFR ID単位でトレースする。

| FR/BR/NFR ID | 概要 | 対応Step |
|---|---|---|
| FR2.1, BR4.1, BR4.11 | ユーザー招待・再招待 | Step 10, 12, 14 |
| FR2.1, BR4.2, BR4.3 | 招待受諾・初回パスワード設定・UserPreference作成 | Step 12, 14 |
| FR2.2, BR4.5, BR4.12, BR4.14 | ユーザー更新(name・roleIds)・roleId実在検証・自己のroleIds変更の禁止・一覧 | Step 4, 12, 14 |
| FR2.3, BR4.6, BR4.13 | ユーザー無効化・C11の`isDisabled`(常に最新) | Step 12, 14 |
| FR2.4, BR4.7 | 初期管理者の自動作成 | Step 12 |
| FR2.5, FR2.6, BR4.4 | パスワードの長さ・Argon2idのハッシュ化・平文の非出力 | Step 6, 12, 17 |
| FR2.8, BR4.16 | 招待メール(SMTP)・失敗時のロールバックと503 | Step 8, 12 |
| FR9.1, FR10.1, BR4.3, BR4.8 | 表示設定(GET/PUT `/api/me/preferences`) | Step 12, 14 |
| BR4.9 | UserChangedEventの発行 | Step 10, 12, 16 |
| BR4.10 | `/api/users`系の認可(401/403) | Step 12, 14, 15 |
| BR4.15 | email・nameの検証と正規化 | Step 12 |
| FR1.6, NFR8.1 | 業務固有のハードコード禁止・設定値の外出し | Step 2, 12, 18 |
| NFR1.1 | 管理系APIの応答時間(キャッシュなし・認可1回・一覧の列限定) | Step 12, 14 |
| NFR1.2, NFR1.3 | ハッシュ計算時間・同時計算数の制御・待機2秒の503 | Step 6, 7 |
| NFR1.4 | 招待メール送信の時間予算(専用プール5・待ち行列なし・10秒の打ち切り) | Step 8, 9 |
| NFR2.1, NFR2.5 | サーバー側の認可再検証・権限昇格の防止 | Step 12, 13, 14, 15 |
| NFR2.2 | 認証情報の保護・専用の応答型・ログイン成功時のハッシュ更新 | Step 6, 12, 14 |
| NFR2.3 | パスワードポリシー・リクエストボディの64KiB上限(413) | Step 6, 14, 15 |
| NFR2.4 | 招待トークン(UUIDv4・受諾/取消でnull化・404の一本化) | Step 12, 14 |
| NFR2.6 | 個人情報の保護(氏名・emailをログ等へ出さない) | Step 17 |
| NFR2.7 | 招待メールの安全性(HTMLエスケープ・件名の抽出と拒否・ベースURL) | Step 8, 9, 12 |
| NFR2.8 | セキュリティCIゲート(SAST・シークレットスキャン。リポジトリに実値を置かない) | Step 2, 18 |
| NFR2.9 | エラーレスポンスの情報開示制御(`UserApiExceptionAdvice`・`instance`のテンプレート化) | Step 14, 15 |
| NFR2.10 | 招待トークンの露出抑止(U4分。共通基盤分は対象外) | Step 8, 14, 17 |
| NFR2.11 | 初期管理者の資格情報の供給・起動時の検証 | Step 12, 13 |
| NFR3.1, NFR3.2 | 規模に対する設計(インデックス・全件返却) | Step 4, 12 |
| NFR3.3, NFR3.4 | ハッシュ計算のリソース・プロセス内の状態 | Step 6, 10 |
| NFR4.1 | 招待受諾の原子性・行ロックによる更新の直列化 | Step 12, 13 |
| NFR4.2 | 招待の整合性(排他→許可→DB接続・メール送信失敗のロールバック・競合) | Step 10, 11, 12, 13 |
| NFR4.3 | 監査イベントの配信(コミット後・`REQUIRES_NEW`・例外の捕捉) | Step 10, 11, 16 |
| NFR4.4 | 起動時の初期管理者作成(ハッシュを先に・冪等・一意制約違反は既存扱い) | Step 12, 13 |
| NFR4.5 | 障害時の縮退(SMTP不調・ハッシュ混雑・同一emailの連打) | Step 11, 13 |
| NFR5.1〜NFR5.4 | メトリクス・ログ・スパン・ヘルスチェック(専用部品なし) | Step 17 |
| NFR7.1 | 招待メールの言語(ja/en、言語ごとのテンプレート) | Step 8, 9 |
| NFR7.2 | バリデーションエラーのi18nキー | Step 14, 15 |
| NFR8.2 | テスト要件(認可拒否・並行・安全失敗・トークン非露出・境界値) | Step 5, 7, 9, 11, 13, 15, 17 |

## Step 1: 契約追補・機能設計への追補(最初の作業、ドキュメントのみ)

- [ ] `inception/contract-design/contract-summary.md`のC5・C10・C11の各節へ、前提事項1の追補を追記する(既存記述は書き換えない。追補であることが分かる見出しまたは注記を付ける)
- [ ] `construction/user-management/functional-design/functional-spec.md`・`rules.md`の末尾へ、「Code Generation着手時の追補」節を追加し、W1・BR4.1(`locale`)、BR4.5(`roleExists`)、BR4.15(`name`の最大長・制御文字)、および「NFR Design保留8〜15番の扱い」(実装するもの・共通基盤への要求として記録のみのもの・本Boltで実装しないもの)を記録する
- [ ] NFR Designの保留9・13番を、前提事項7の内容で確定した旨を記録する(記録先は上記の「Code Generation着手時の追補」節)

## Step 2: プロジェクト構造・ビルド設定

- [ ] `backend/src/main/java/com/mastersmith/usermanagement/`配下にパッケージ構造を作成する(`entity`, `repository`, `dto`, `service`, `security`(パスワード・排他・許可), `mail`, `event`, `web`, `config`, `exception`の各サブパッケージ。C11の`UserAccountLookupApi`は`com.mastersmith.usermanagement`直下に置く。C11契約のpackage)
- [ ] `git submodule add https://github.com/agwlvssainokuni/java-mustache-processor external/java-mustache-processor`でサブモジュールを追加し、取得したコミットで固定する。座標・依存・ライセンス・APIを実物で再確認し、結果を`code-summary.md`へ記録する
- [ ] `settings.gradle.kts`に`includeBuild("external/java-mustache-processor")`を追加する。`backend/build.gradle.kts`に、`implementation("cherry.mustache:cherry-mustache-core")`、`spring-boot-starter-mail`、Argon2idの`org.springframework.security:spring-security-crypto`(Spring Boot BOMで管理されるか確認する)と`org.bouncycastle:bcprov-jdk18on`(最新の安定版を確認して固定する)を追加する。既存のSpotless・Checkstyle・JaCoCoの設定は緩めない
- [ ] `backend/src/main/resources/application.yml`に、`mastersmith.users.*`(Argon2idのパラメータ: メモリ19456KiB・反復2・並列度1、ハッシュの同時実行数(既定はCPUコア数)と待機2秒、招待の同時実行数5、排他の待機12秒、メール送信の打ち切り10秒、DBのロック待ち15秒、`initial-admin.email`・`password`・`role-ids`(環境変数から注入、既定値は置かない))、`mastersmith.mail.*`(招待リンクのベースURL・`allow-insecure-link`(既定false))、`spring.mail.*`(host・port・username・passwordを環境変数から注入、接続3秒・読み取り5秒・書き込み2秒)、`spring.datasource.hikari.maximum-pool-size: 60`を追加する。リポジトリに実値(初期管理者・SMTPの認証情報)を置かない(NFR2.8・NFR2.11)
- [ ] `backend/src/test/resources/application.yml`に、テスト用のダミー値(初期管理者・SMTP・ベースURL)を追加し、既存の`@SpringBootTest`が、起動時のfail fast検証を通って動くようにする

## Step 3: テストランナー確認

- [ ] `./gradlew :backend:test --tests "com.mastersmith.usermanagement.*"`がU4配下のテストを実行できることと、サブモジュール(`includeBuild`)のビルドがこのプロジェクトのビルドから解決されることを確認する(既存ユニットと共通のテスト基盤・Flywayマイグレーション適用フローを踏襲)

## Step 4: データモデル層の実装(entities.md準拠、NFR3.1)

- [ ] `V4__create_user_management.sql`を作成する。`users`(`user_id` PK VARCHAR(36)、`name`、`email`(一意)、`password_hash` nullable、`status`、`invitation_token` nullable(一意))、`user_role`(`user_id`・`role_id`、複合主キー)、`user_preference`(`user_id` PK・`theme`・`font_size`・`locale`)を定義する
- [ ] `User`・`UserPreference`エンティティと`UserStatus`(`invited`/`active`/`disabled`)、`Theme`・`FontSize`・`Locale`(許容値の列挙)を実装する。`User`は`passwordHash`・`invitationToken`を持つが、応答型・イベントのスナップショット型には含めない(NFR2.2)
- [ ] `UserRepository`(`findByEmail`・`findByInvitationToken`・email昇順の全件取得(`passwordHash`・`invitationToken`を含めない射影)・行ロック付きの読み取り(`findByIdForUpdate`、DBのロック待ちタイムアウトは設定値)・招待受諾の条件付き更新(`invitation_token`と`status=invited`が条件、更新件数を返す)・再招待の条件付き更新)、`UserPreferenceRepository`を実装する

## Step 5: データモデル層のテスト(test-after)

- [ ] `UserJpaTest`・`UserPreferenceJpaTest`: 往復の永続化、`email`・`invitationToken`の一意制約、`invitationToken`がnullの複数行、`roleIds`の要素コレクション、`invited`のUserの`passwordHash`がnullで保存できることを確認する
- [ ] `UserRepositoryTest`: email昇順の全件取得の射影(機微項目を含まない)、招待受諾の条件付き更新の更新件数(1件・0件)、再招待の条件付き更新、行ロック付き読み取りの動作を確認する

## Step 6: 基盤部品の実装(パスワード・ハッシュの同時計算の制御、NFR1.2・NFR1.3・NFR2.2・NFR2.3)

- [ ] `PasswordPolicy`(長さ8〜128をUnicodeコードポイント数で検証。招待受諾・初期管理者の作成・`verifyPasswordHash`で共通に使う)を実装する
- [ ] `HashConcurrencyLimiter`(公平な`Semaphore`、許可数の既定はCPUコア数、待機2秒、超過時は`HashCapacityExceededException`、許可は必ず返す)を実装する。超過の回数を`user.password.hash.rejected`へ記録する(NFR5.1)
- [ ] `PasswordHasher`(`hash`・`verify`(129文字以上は計算せずfalse)・`needsUpgrade`(保存済みハッシュの`$argon2id$v=19$m=…,t=…,p=…$`を解析して現在の設定と比較。`Argon2PasswordEncoder`が比較を行うかを実装時に確認し、行わない場合は自前で解析))を実装する。他のクラスは`Argon2PasswordEncoder`を直接使わない。所要時間を`user.password.hash.duration`へ記録する。平文をフィールドに保持せず、ログにも出さない

## Step 7: 基盤部品のテスト

- [ ] `PasswordPolicyTest`(テーブル駆動): 長さの境界値(7・8・128・129)、サロゲートペアを含む文字列でのコードポイント数の数え方を確認する
- [ ] `HashConcurrencyLimiterTest`: 許可数の上限、待機2秒での`HashCapacityExceededException`、例外時にも許可が返ること、超過カウンタの増加を確認する
- [ ] `PasswordHasherTest`: ハッシュの検証(正しいパスワード・誤ったパスワード)、129文字以上は計算せずfalse、`needsUpgrade`(現在の設定と同じ・古いパラメータ・不正な形式)、ハッシュ文字列に平文が含まれないことを確認する
- [ ] `PasswordHasherBenchmarkTest`: 並列度1・同時実行なし・十分なウォームアップ後に反復計測し、p95が300ms以下であること(NFR1.2)を確認する。CPUコア数・メモリ・p95をテストの出力に含める

## Step 8: メール部品の実装(NFR1.4・NFR2.7・NFR7.1)

- [ ] `MailTemplateRenderer`(自作mustacheエンジンへのアダプタ。言語(ja/en)ごとのHTMLテンプレートを`backend/src/main/resources/mail/`から読み、`Mustache.compile(...)`・`template.render(...)`でHTMLを得る。エンジンが既定でHTMLエスケープしない場合は、差し込む値を先にエスケープする)を実装する
- [ ] `SubjectExtractor`(レンダリング後のHTMLの`<title>`要素の値を自前の簡易処理で取り出し、文字参照(数値参照・主要な名前つき参照)をデコードし、デコード後にCR/LFを含む場合と200文字を超える場合、`<title>`が無い・空の場合は拒否する。除去はしない)を実装する
- [ ] `invitation_ja.html`・`invitation_en.html`(HTMLのみ。`<title>`に件名を持ち、氏名・招待リンクを差し込む)を作成する。招待リンクは`<ベースURL>/invitations/accept#token=<トークン>`の形式(NFR2.10)
- [ ] `InvitationMailExecutor`(スレッド数5、待ち行列なし。投入時の満杯は`RejectedExecutionException`)、`InvitationMailer`(組み立てと送信。宛先は正規化後のemailのみ、件名以外のヘッダーに利用者の入力値を入れない、非ASCII文字はヘッダーとして符号化する。呼び出し側が10秒で打ち切る。MDCを送信タスクへ引き継ぐ)を実装する。失敗の分類ごとに`user.invitation.mail.failed`・`user.invitation.subject.rejected`を記録する
- [ ] `MailTemplateValidator`(起動時に、全言語のテンプレートをサンプルの値でレンダリングし、`SubjectExtractor`で件名を取り出せることを確認する。取り出せなければ起動を失敗させる)と、起動時の設定検証(SMTPのhost未設定、ベースURLの`https`必須(`allow-insecure-link`がtrueのときだけ`http`を許可))を実装する

## Step 9: メール部品のテスト

- [ ] `MailTemplateRendererTest`: 言語ごとのテンプレートの選択(省略時ja)、氏名に含まれるHTML特殊文字がエスケープされること(エンジンの既定の挙動の確認を含む)、招待リンクの形式を確認する
- [ ] `SubjectExtractorTest`(テーブル駆動): 通常の件名、文字参照のデコード(数値参照・名前つき参照・デコードの漏れ)、デコード後の改行(CR/LF)の拒否、200文字の上限(200・201)、`<title>`の欠落・空を確認する
- [ ] `InvitationMailerTest`: 送信成功、送信失敗・10秒の打ち切りでの例外とカウンタ、プール満杯(`RejectedExecutionException`)、宛先・ヘッダーに入力値が入らないこと、MDCの引き継ぎを確認する(`JavaMailSender`はモック)
- [ ] `MailTemplateValidatorTest`・設定検証のテスト(安全失敗、`ApplicationContextRunner`): テンプレートに`<title>`が無い場合、SMTPのhost未設定、ベースURLが`http`かつ`allow-insecure-link=false`の場合に、起動が失敗することを確認する

## Step 10: 排他・許可・イベント発行の実装(NFR4.2・NFR4.3・NFR3.4)

- [ ] `EmailLockRegistry`(正規化後email単位の排他。参照数つきのロックの表、利用者がいなくなったエントリは取り除く、待機は最大12秒、ストライプ方式は採らない)を実装する
- [ ] `InvitationAdmission`(上限5の`Semaphore`、待たない`tryAcquire`、満杯は`InvitationCapacityExceededException`)を実装する
- [ ] `UserChangedEvent`・`UserSnapshot`(`name`・`email`・`status`・`roleIds`のみ)と`UserChangedEventPublisher`(コミット後に、`TransactionTemplate`の`REQUIRES_NEW`の中で同期の`ApplicationEventPublisher.publishEvent`を呼ぶ。全体をtry-catchで囲み、例外は警告ログ(userIdのみ)と`user.event.publish.failed`に記録してHTTPの結果に影響させない)を実装する

## Step 11: 排他・許可・イベント発行のテスト

- [ ] `EmailLockRegistryTest`: 同一emailの直列化、別emailの非干渉、参照数が0になったエントリの除去、待機12秒の超過で失敗すること(待機時間は設定で短縮して確認)を確認する
- [ ] `InvitationAdmissionTest`: 上限5・待たずに拒否・許可の返却を確認する
- [ ] `UserChangedEventPublisherTest`: 発行の例外がHTTPの結果に影響しないこと、`user.event.publish.failed`の増加、イベントのスナップショットに`passwordHash`・`invitationToken`が含まれないことを確認する

## Step 12: ビジネスロジック層の実装(W1〜W8、BR4.1〜BR4.16)

- [ ] `PermissionEngineApi.roleExists(String)`と`PermissionEngineApiImpl`の実装を追加する(前提事項4。既存メソッドは変更しない)
- [ ] `CurrentOperatorProvider`(インタフェース)と暫定実装`HeaderCurrentOperatorProvider`(前提事項2)、`UserAuthorizer`(`canAccessScreen(activeRoleId, "user-management")`の評価。操作者が解決できなければ401、権限がなければ403)、入力の検証・正規化(emailのtrim・小文字化・形式、nameの必須・最大100文字・制御文字の禁止、表示設定の許容値。フィールド単位のi18nキー)を実装する
- [ ] `UserApplicationService`(一覧W7、更新W3(自己のroleIds変更の拒否・`roleExists`・行ロック・beforeValue)、無効化W4(自己の無効化の拒否・冪等・invitedの取消でトークンnull化)。`@Transactional`は使わず、`TransactionTemplate`で囲み、コミット後に`UserChangedEventPublisher`を呼ぶ)を実装する
- [ ] `InvitationFacade`(W1。認可・正規化・検証を先に行い、`EmailLockRegistry`→`InvitationAdmission`→`TransactionTemplate`{既存検索・作成/再招待の条件付き更新・`roleExists`・メール送信}→許可・排他の返却(finally)→コミット後のイベント発行の順序を守る。メール送信の失敗・打ち切りはロールバックして503、一意制約違反は422、DBのロック待ちタイムアウトは503。再招待の条件付き更新が0件なら422)を実装する
- [ ] `InvitationAcceptService`(W2。トークン検索(トランザクションの外)→404、検証→422、`HashConcurrencyLimiter`でハッシュ計算(トランザクションの外)、`TransactionTemplate`{条件付き更新(0件なら404)・`UserPreference`作成}、コミット後にACTIVATEDイベント。未知のトークンではハッシュを計算しない)を実装する。`UserPreferenceService`(W6。GETは未作成なら既定値を返し作成しない、PUTは作成または更新。操作者のuserIdのみを用いる)を実装する
- [ ] `UserAccountLookupApi`(C11、3メソッド)と`UserAccountLookupService`(`findByEmail`は正規化後のemailで検索し`roleIds`は直接付与分とGroup経由分の和集合で`passwordHash`はnull、`verifyPasswordHash`はactiveかつ`passwordHash`が非nullの場合のみ検証(129文字以上は計算せずfalse)し、成功時に`needsUpgrade`なら同じ許可の中で新ハッシュを計算して`REQUIRES_NEW`で条件付き更新(失敗は警告ログ・ログインの成否に影響させない・イベントなし)、`isDisabled`は常に最新のstatusで判定(不存在はtrue))を実装する
- [ ] `InitialAdminProperties`(`@ConfigurationProperties`、未設定・パスワードの長さ・emailの形式不正を、バインド時に検証して起動を失敗させる)と`InitialAdminBootstrap`(`ApplicationRunner`。存在確認→存在しなければハッシュ計算(トランザクションの外)→1つのトランザクションでUser・UserPreferenceを作成、コミット後にBOOTSTRAPPEDイベント(actorは`system`)。一意制約違反は「既に存在した」として扱う。ログは事実のみ)を実装する

## Step 13: ビジネスロジック層のテスト(認可拒否専用テスト・並行テスト含む)

- [ ] `UserAuthorizerTest`・`UserApplicationServiceTest`(テーブル駆動の**認可拒否専用テスト**、`team.md`必須テスト種別(c)): 操作者の未解決(401)・権限なし(403)・許可の組み合わせを、一覧・更新・無効化・招待のすべてで確認する。自己のroleIds変更の拒否(422と`user.role.escalation.denied`)、`roleExists`がfalseの場合の422、更新可能項目以外の指定の422、自己の無効化の拒否、すでにdisabledへの再DELETEの冪等(イベントなし)、invitedのUserの取消(トークンnull化)を確認する
- [ ] `UserApplicationServiceConcurrencyTest`(統合): 同一Userへの同時更新で、beforeValueが直前の確定した値になること(行ロック)を確認する
- [ ] `InvitationFacadeTest`: 作成・再招待・重複(active/disabled)・メール送信失敗のロールバックと503(イベントなし)・件名の拒否・排他と許可がコミット後に返ること・許可の満杯で待たずに503・同一emailの同時招待の直列化・同一emailの連打が他のemailの許可を占有しないこと・再招待と受諾・取消の競合(422・503)・DBのロック待ちタイムアウトの503・一意制約違反の422を確認する
- [ ] `InvitationAcceptServiceTest`(統合を含む): 正常系(User・UserPreferenceの作成、既定値へのフォールバック)、並行受諾(1件のみ成功、他方は404)、未知・使用済み・取消済みのトークンがいずれも同一の404、未知のトークンでハッシュを計算しないこと、パスワード・氏名・表示設定の検証(422)を確認する
- [ ] `UserAccountLookupServiceTest`: `findByEmail`(和集合・`passwordHash`がnull・正規化)、`verifyPasswordHash`(active以外・`passwordHash`がnull・129文字以上はfalse、`HashCapacityExceededException`の伝播)、ログイン成功時のハッシュ更新(古いパラメータの更新・条件付き更新の競合で更新しない・更新の失敗がログインの成否に影響しない・読み取り専用のトランザクションの中から呼んでも更新が失われない)、`isDisabled`(最新のstatus・不存在はtrue)を確認する
- [ ] `InitialAdminBootstrapTest`・`InitialAdminPropertiesTest`(安全失敗、`ApplicationContextRunner`): 未設定・パスワードの長さ・emailの形式不正で起動が失敗すること、作成(status=active・roleIds・UserPreference既定値・BOOTSTRAPPED)、冪等(存在すれば何もしない・ハッシュを計算しない)、一意制約違反を既存として扱うこと、パスワードがログ・例外メッセージに出ないことを確認する
- [ ] `UserPreferenceServiceTest`: GETの既定値(作成しない)、PUTの作成と更新、他人の設定を操作できないこと(対象userIdはトークン由来のみ)、許容値外の422を確認する
- [ ] `PermissionEngineApiImplTest`に`roleExists`の存在・不存在のケースを追加する

## Step 14: API層の実装(C5・BR4.10・NFR2.1・NFR2.3・NFR2.9・NFR7.2)

- [ ] DTO(`UserResponse`(`userId`・`name`・`email`・`status`・`roleIds`のみ)、`InviteUserRequest`(email・name・roleIds・locale)、`UpdateUserRequest`(name・roleIds)、`AcceptInvitationRequest`(password・name・theme・fontSize・locale)、`UserPreferenceDto`)を実装する。`passwordHash`・`invitationToken`を持たない型とする
- [ ] `UserController`(`GET/POST /api/users`、`PUT/DELETE /api/users/{userId}`)、`InvitationAcceptController`(`POST /api/users/invitations/{token}/accept`、認証不要)、`MePreferencesController`(`GET/PUT /api/me/preferences`、activeRoleIdに依存しない)を実装する
- [ ] `RequestSizeLimitFilter`(U4のURLパターン`/api/users/**`・`/api/me/preferences`に限る。`Content-Length`が64KiBを超える場合は内容を読まずに413、チャンク転送は読み込み量を数えるストリームで包み64KiB超で`RequestBodyTooLargeException`。認証フィルタより前に置く最高優先の順序で登録する)を実装する
- [ ] `UserApiExceptionAdvice`(`@RestControllerAdvice`、U4のコントローラに限定、`@Order`を明記)を実装する。`HashCapacityExceededException`・`InvitationCapacityExceededException`・ロック待ちタイムアウト・メール送信失敗は503、`RequestBodyTooLargeException`(`HttpMessageNotReadableException`の原因の判別を含む)は413、招待トークンの不一致は404、フィールド単位の検証エラーは422(`errors[]`に`field`と`message`(i18nキー)、必要なら`params`)、401・403にも対応する。RFC 9457のProblemDetailsとし、`instance`は招待受諾APIではルートのテンプレート(`/api/users/invitations/{token}/accept`)にする。入力値(パスワードなど)・スタックトレース・内部の型名を含めない

## Step 15: API層のテスト(認可拒否専用テスト含む)

- [ ] `UserControllerTest`(`@WebMvcTest`)・`InvitationAcceptControllerTest`・`MePreferencesControllerTest`: 正常系(200/201/204)、**認可拒否専用テスト**(未認証の401・権限なしの403を`/api/users`系の全エンドポイントで確認。`/api/me/preferences`が他人の設定を操作できないこと)、422(フィールド単位・i18nキー・入力値を含まない)、404(対象不存在・トークンの不一致)、503(ハッシュ・招待の同時実行数・メール送信失敗)、`GET /api/users`の応答に`passwordHash`・`invitationToken`が含まれないことを確認する
- [ ] `RequestSizeLimitFilterTest`・`UserApiExceptionAdviceTest`: `Content-Length`のある場合・チャンク転送の場合の413、認証前(操作者が未解決でも)のボディの拒否、64KiBちょうどの通過、ProblemDetailsの`instance`にトークンを含む生のパスが入らないことを確認する

## Step 16: audit-logging(U7)への追加と、コミット後の発行の統合テスト

- [ ] `UserChangedEventListener`(`com.mastersmith.audit.event`)と`AuditLogEventMapper.fromUserChangedEvent`を追加する(前提事項3。既存のリスナー・マッパーは変更しない)
- [ ] `UserChangedEventListenerTest`・`AuditLogEventMapperTest`への追加: 5種類の`operation`の対応付け(BOOTSTRAPPEDは`actorRaw`=`system`)、スナップショットのMap化、記録失敗が発行元へ伝わらないことを確認する
- [ ] `UserChangedEventAuditIntegrationTest`(`@SpringBootTest`): コミット後の発行で、監査ログの行が実際に永続化される(発行後に別のトランザクションから読める)こと、ロールバック・メール送信失敗の場合は行が作られないことを確認する(NFR4.3)

## Step 17: 可観測性の実装とトークン・個人情報の非露出の確認(NFR2.6・NFR2.10・NFR5.1〜NFR5.4)

- [ ] NFR5.1の7つのメトリクス(`user.invitation.mail.failed`・`user.invitation.accept.not_found`・`user.role.escalation.denied`・`user.password.hash.duration`・`user.password.hash.rejected`・`user.invitation.subject.rejected`・`user.event.publish.failed`)を、ラベルなしで記録する
- [ ] 招待メール送信・ハッシュ計算・C11の各メソッド・`/api/users`系に、`ObservationRegistry`による観測(スパン)を付ける。属性にメールアドレス・氏名・件名・トークンを含めない。ログは、パスワード・トークン・`passwordHash`・メールアドレス・氏名・件名の実値を出さず、userIdを用いる(初期管理者の作成は事実のみ)
- [ ] `UserManagementNoLeakTest`: 招待・受諾(成功・404・422)・ログイン成功時のハッシュ更新・初期管理者の作成・メール送信失敗の各経路で、U4のログ出力(`ListAppender`で捕捉)・メトリクスのラベル・ProblemDetails・イベントのスナップショットに、パスワード・招待トークン・`passwordHash`・メールアドレス・氏名の実値が現れないことを確認する
- [ ] 専用のヘルスチェック部品は設けない(NFR5.4。共通基盤のデータソースのヘルスチェックに含まれる)

## Step 18: 環境・ビルド設定

- [ ] 新規パッケージ・マイグレーション・依存が、既存のGradle/Spotless/Checkstyle/JaCoCo設定下でビルド・整形されることを確認する(`spotlessCheck`・`checkstyleMain`・`checkstyleTest`が本ユニットのファイルに関して合格、既存の全テストがグリーン、行カバレッジ80%以上)。カバレッジの基準・閾値は緩めない
- [ ] リポジトリに、初期管理者・SMTPの実値や認証情報が入っていないことを確認する(NFR2.8。シークレットスキャンの観点)。`.gitmodules`とサブモジュールのコミットの固定を確認する

## Step 19: ドキュメント・トレーサビリティ

- [ ] 各クラス・メソッドに必要最小限のJavadoc(非自明な設計判断のみ)を付与する。生成する全ソースファイルの先頭に、Apache License 2.0の標準ヘッダー(年`2026`、著作権者`agwlvssainokuni`)を入れる
- [ ] `code-summary.md`・`traceability.json`はオーケストレーターが実施。`source-manifest.json`はdispatch指示により開発エージェントが作成する(U4が作成・変更したアプリケーションのソースのパス。サブモジュール・`.gitmodules`・permission-engine・audit-loggingへの追加分を含む)
