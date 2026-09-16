# Code Generation Plan — menu-navigation (U6)

menu-navigationは業務メニュー(N階層、`MenuItem`)・管理メニュー(4項目、ハードコード)の構成管理と、ログイン後トップ画面・サイドバーの表示制御(FR7.1〜FR7.3)を担う。権限判定は自前で持たず、`PermissionEngineApi`(C10)・`ConfigEngineApi`(C9)へ委譲する。本Boltで、参照API(`GET /api/menu`)に加え、業務メニューのCRUD API(`POST/PUT/DELETE /api/menu-items`、Contract Design追補)とC12内部インタフェース(`MenuStructureApi`、config-import-export向け、未実装コンシューマー)を実装する。

## 前提事項(Plan Approval時に確認いただきたい設計判断)

1. **管理メニュー4項目の`MenuItem`表現(BR6.2、Q8=A)**: 管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)はDBへ永続化しないハードコード項目のため、C3契約の`MenuItem`スキーマ(`menuItemId`/`label`/`targetTableConfigId`/`children`)にそのまま当てはめる際、`targetTableConfigId`に相当する値がない。固定の`menuItemId`(例: `admin-business-menu-config`/`admin-user-management`/`admin-audit-log`/`admin-config-management`)を割り当て、`targetTableConfigId`は`null`とする。functional-design-questions.md Q9=A(「追加で必要な情報はない」)により、フロントエンド側のルーティングは固定4項目を`label`または位置で判別する前提とする。
2. **`GET /api/menu`と`/api/menu-items`で401/403の切り分けを分ける**: C3契約は`GET /api/menu`に`401`のみ(`403`は宣言されていない。BR6.3の権限フィルタは「除外」であり「エラー」ではないため)を、`/api/menu-items`には`401`と`403`の両方を宣言している。`ActiveRoleResolver`(暫定実装、Q4=A)がactiveRoleIdを解決できない場合を「401(未認証)」、解決できたが`canAccessScreen`が`false`を返す場合を「403(権限不足)」として区別する。schema-introspector/audit-loggingは自身の契約に401を持たないため両者を403へ一本化していたが、menu-navigationは契約が401を明示するため区別する(契約への忠実性を優先する、正当化された差異)。
3. **`order`は列名`item_order`とする**: `order`はSQL予約語のため、H2上の物理カラム名を`item_order`とし、Javaフィールド名・DTOフィールド名は`order`(entities.md/契約どおり)のまま`@Column(name = "item_order")`でマッピングする。
4. **物理外部キー制約は設けない**: `parentMenuItemId`は既存ユニット(`targetTableConfigId`等)と同じ「不透明な文字列参照」の慣例に従い、DB上の`FOREIGN KEY`制約は設けない(存在確認はアプリケーション層で行う、entities.md制約と整合)。

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

- **フロントエンド層**: menu-navigationはバックエンドサービスでありUIを持たない。トップ画面・サイドバー(frontend-ui、U12、未実装)からの呼び出しは対象外。
- **複数プロファイル横断E2Eテスト**: `team.md`必須テスト種別(b)は、list-engine・record-edit-engine・frontend-uiが未実装の現時点では実行不能なため本Boltでは対象外とする。認可拒否(negative-authorization)専用テスト(必須テスト種別(c))はコントローラ単体テストとして実施可能なため含める(Step 8)。

## Story-to-Code Step Traceability

user-storiesステージはSKIP対象(`project.md`学習事項)のため、`requirements.md`のFR ID・`functional-design/rules.md`のBR ID・`nfr-requirements`/`nfr-design`のNFR ID単位でトレースする。

| FR/BR/NFR ID | 概要 | 対応Step |
|---|---|---|
| FR7.1, BR6.1, BR6.2 | 業務メニューN階層・管理メニュー4項目(ハードコード) | Step 3, 5 |
| FR7.2, BR6.9 | トップ画面Card表示(第1階層グループ単位、追加対応不要) | Step 5, 7 |
| FR7.3, BR6.5 | 空メニュー時の案内表示はフロントエンド側の責務、空配列をそのまま返す | Step 7 |
| BR6.3, BR6.4 | screenKey決定規則・フォルダの再帰的可視性導出 | Step 5, 6 |
| BR6.6 | 兄弟項目のorder昇順ソート | Step 5, 6 |
| BR6.7, NFR4.1 | 参照先TableConfig欠損時の実行時除外 | Step 5, 6 |
| BR6.8 | MenuItem CRUD APIの認可・バリデーション | Step 5, 7, 8 |
| NFR1.1 | `GET /api/menu`応答時間(逐次呼び出し、キャッシュなし) | Step 5 |
| NFR1.2 | `/api/menu-items`応答時間(単純CRUD) | Step 7 |
| NFR2.1, NFR2.2 | 認証・認可(401/403の切り分け、前提事項2) | Step 7, 8 |
| NFR2.3 | 機微情報の非記録・非出力 | Step 7, 9 |
| NFR2.5 | 入力検証(400) | Step 7, 8 |
| NFR4.3 | `/api/menu-items`の失敗時はfail fast、自動リトライなし | Step 7 |
| NFR4.4 | DELETE時の子孫存在チェック(EXISTS、409) | Step 5, 6 |
| NFR5.1, NFR5.2 | メトリクス・構造化ログ | Step 9 |

## Step 1: プロジェクト構造(パッケージ作成)

- [ ] `backend/src/main/java/com/mastersmith/menu/`配下にパッケージ構造を作成する(`entity`, `repository`, `dto`, `tree`, `service`, `web`, `exception`の各サブパッケージ)

## Step 2: テストランナー確認

- [ ] 既存のGradleテストタスク(`./gradlew :backend:test`)がmenu-navigation配下の新規テストクラスを実行できることを確認する(既存ユニットと共通のテスト基盤・Flywayマイグレーション適用フローを踏襲)

## Step 3: データモデル層の実装(エンティティ・マイグレーション、entities.md準拠)

- [ ] `backend/src/main/resources/db/migration/V3__create_menu_item.sql`を作成する。`menu_item`テーブル(`menu_item_id` PK VARCHAR(36)、`parent_menu_item_id` VARCHAR(36) nullable・物理FK制約なし、`label` VARCHAR NOT NULL、`item_order` INTEGER NOT NULL、`target_table_config_id` VARCHAR(36) nullable)と、DELETE時の子孫存在チェック(`existsByParentMenuItemId`、NFR4.4)を効率化する`idx_menu_item_parent_menu_item_id`(`parent_menu_item_id`)を定義する
- [ ] `MenuItem`エンティティ(`com.mastersmith.menu.entity`)を実装する(entities.md準拠。`order`フィールドは物理カラム`item_order`にマッピング、前提事項3)

## Step 4: データモデル層のテスト(test-after)

- [ ] `MenuItemJpaTest`: INSERT後の読み取り往復、`parentMenuItemId`/`targetTableConfigId`がnullで永続化されるケース(ルート直下・フォルダ項目)を確認する

## Step 5: ビジネスロジック層の実装(メニュー構築・CRUD、BR6.2〜BR6.9・NFR1.1・NFR4.4)

- [ ] `MenuItemRepository`(`com.mastersmith.menu.repository`)を実装する。Spring Data JPAの`Repository<MenuItem, String>`を継承し、`findAll()`(W1、1クエリ一括取得、performance-design.md)・`findById`・`existsByParentMenuItemId(String)`(W4、NFR4.4)・`save`・`deleteById`を宣言する
- [ ] `AdminMenuDefinition`(`com.mastersmith.menu.tree`)を実装する。管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)を固定`menuItemId`・`label`・`screenKey`のリストとしてハードコードする(BR6.2、Q8=A、前提事項1)。screenKeyは「業務メニュー設定」「設定管理」が`"config-import-export"`を共有、「ユーザ管理」が`"user-management"`、「監査ログ管理」が`"audit-log"`(BR6.3-(3)〜(5))
- [ ] `MenuTreeBuilder`(`com.mastersmith.menu.tree`)を実装する。全`MenuItem`から`parentMenuItemId`により木構造を再構築し(W1手順3)、リーフ項目ごとに`ConfigEngineApi.getTableConfigById`存在確認(BR6.7、`TableConfigNotFoundException`時は除外)・`PermissionEngineApi.canAccessScreen(activeRoleId, targetTableConfigId)`(BR6.3-(1))を呼び出して除外フィルタを適用し、フォルダ項目の可視性を配下リーフから再帰的に導出し(BR6.4)、各階層を`order`昇順でソートする(BR6.6)。管理メニューは`AdminMenuDefinition`の4項目それぞれに対応するscreenKeyで`canAccessScreen`を呼び出しtrueの項目のみ返す(W1手順7)
- [ ] `MenuQueryService`(`com.mastersmith.menu.service`)を実装する。`MenuItemRepository.findAll()`→`MenuTreeBuilder`呼び出しを束ね、`{businessMenu, adminMenu}`を返す(W1)
- [ ] `MenuItemCommandService`(`com.mastersmith.menu.service`)を実装する。作成(W2: `parentMenuItemId`指定時は既存確認・`targetTableConfigId`指定時はConfigEngine存在確認、いずれも不整合なら`MenuItemValidationException`)・更新(W3: 対象存在確認は`MenuItemNotFoundException`、以降はW2と同様の検証)・削除(W4: 対象存在確認、`existsByParentMenuItemId`がtrueなら`MenuItemConflictException`)を実装する
- [ ] `MenuStructureApi`(`com.mastersmith.menu`、C12契約インタフェース)と実装クラス`MenuStructureApiImpl`(`com.mastersmith.menu.service`)を実装する。`getExportableMenuStructure()`(`MenuItemRepository.findAll()`をC12の`MenuItem`型へ変換)・`importMenuStructure(List<MenuItem>)`(全件洗い替え、`ConfigValidationException`をfail fastで送出)を提供する。config-import-export(U9)は本Bolt時点で未実装のコンシューマーだが、契約所有者(menu-navigation)側の責務としてconfig-engineの`getExportableConfigSet`/`importConfigSet`と同様の先行実装パターンを踏襲する

## Step 6: ビジネスロジック層のテスト

- [ ] `MenuTreeBuilderTest`(テーブル駆動): フォルダ/リーフ混在の木構造、リーフ権限フィルタ(許可/拒否)、フォルダの再帰的可視性導出(配下全て非表示→フォルダも非表示、1件でも表示可→フォルダも表示)、`order`昇順ソート(重複値を含む)、TableConfig欠損時の実行時除外、管理メニュー4項目の権限フィルタを網羅する(BR6.3〜BR6.7・BR6.9の主要な組み合わせケースを網羅するテーブル駆動テスト、`team.md`インタビューQ6の追加合格条件)
- [ ] `MenuItemCommandServiceTest`: 作成・更新の正常系とバリデーションエラー(存在しない`parentMenuItemId`/`targetTableConfigId`)、削除の正常系と子孫存在時の`MenuItemConflictException`(NFR4.4)を確認する

## Step 7: API層の実装(C3契約・BR6.8・NFR1.1・NFR1.2・NFR2.1・NFR2.2・NFR2.5)

- [ ] `MenuItemView`・`MenuItemInput`・`MenuResponse`(`com.mastersmith.menu.dto`)を実装する(C3契約のスキーマにそのまま対応)
- [ ] `MenuController`(`com.mastersmith.menu.web`)を実装する。`GET /api/menu`は`ActiveRoleResolver`でactiveRoleIdを解決できない場合401(前提事項2、C3契約に403が宣言されていないため)、解決できれば`MenuQueryService`の結果をそのまま200で返す(空配列を含む、BR6.5)。`POST/PUT/DELETE /api/menu-items`はactiveRoleId未解決時401、`PermissionEngineApi.canAccessScreen(activeRoleId, "config-import-export")`が`false`なら403(BR6.8)、`MenuItemCommandService`の各操作結果を201/200/204で返す
- [ ] 例外ハンドラ(`MenuItemNotFoundException`→404、`MenuItemValidationException`→400、`MenuItemConflictException`→409、いずれもRFC 9457 `ProblemDetail`)を実装する

## Step 8: API層のテスト(認可拒否専用テスト含む)

- [ ] `MenuControllerTest`(`@WebMvcTest`または既存パターンに合わせた統合テスト): `GET /api/menu`の正常系(200、businessMenu/adminMenu)・未認証(401)、`/api/menu-items`のCRUD正常系(201/200/204)・バリデーションエラー(400)・**認可拒否専用テスト**(403、`canAccessScreen`がfalseを返すケース。`team.md`必須テスト種別(c))・対象不存在(404)・子孫存在時の削除拒否(409)をテーブル駆動で網羅する

## Step 9: 可観測性の実装(NFR5.1・NFR5.2)

- [ ] `MenuController`にMicrometer計装(`menu_navigation.get_menu.duration`/`.error_count`、`menu_navigation.menu_items_crud.duration`/`.error_count`、observability-design.md)を追加する
- [ ] `/api/menu-items`の作成・更新・削除実行をINFOレベル構造化ログ(activeRoleId・menuItemId・操作種別)、失敗(400/403/404/409)をERRORレベル構造化ログで記録する(`GET /api/menu`は高頻度パスのため個別ログを出力しない、observability-design.md)

## Step 10: 可観測性のテスト

- [ ] メトリクス・ログ出力の直接テストは行わない(Micrometerの`Counter`/`Timer`登録自体はSpring Bootの自動構成に委ね、既存ユニットと同様、専用の単体テストは設けない方針を踏襲)

## Step 11: 環境・ビルド設定

- [ ] 新規パッケージ・マイグレーションファイルが既存のGradle/Spotless/Checkstyle設定下でビルド・整形されることを確認する(spotlessCheck・checkstyleMain・checkstyleTestすべて本ユニットのファイルに関して合格)

## Step 12: ドキュメント・トレーサビリティ

- [ ] 各クラス・メソッドに必要最小限のJavadoc(非自明な設計判断のみ)を付与する
- [ ] `code-summary.md`・`traceability.json`はオーケストレーターが実施。`source-manifest.json`はdispatch指示により開発エージェントが作成する
