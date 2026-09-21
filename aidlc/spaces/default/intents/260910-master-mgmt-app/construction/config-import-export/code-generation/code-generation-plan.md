# Code Generation Plan — config-import-export (U9)

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

**適用**: 権限昇格の判定・主権限0件の拒否(BR9.11・BR9.12)の検証ロジック(permission-engineの検証専用メソッドと、U9側の結合)は、実装に先立って、権限マトリクス(組み合わせのケース)を洗い出す(ATDD寄り、`team.md`の例外)。それ以外のレイヤー(値オブジェクト・パーサー・マッパー・サービス・コントローラー・他ユニットの改修)は、test-after(各レイヤーの実装の後に、そのレイヤーのテストを作成・実行する)とする。

## 実装対象パッケージ

- **新規**: `com.mastersmith.configio`(C7の実装。コントローラー・サービス・パーサー・マッパー・検証・イベント発行・例外の処理)。共有の型は`com.mastersmith.common.configio`(共通基盤。`PostCommit`・`ImportValidationError`・`ApplyResult`)。
- **既存ユニットの改修(追補、`functional-spec.md`の追補一覧2〜4・`nfr-design/logical-components.md`の追補1〜7)**: `com.mastersmith.config`(config-engine)・`com.mastersmith.menu`(menu-navigation)・`com.mastersmith.permission`(permission-engine)・`com.mastersmith.audit`(audit-logging)。

## 前提事項・実装上の決定(承認の対象)

1. **他ユニットの改修は、本ユニットのCode Generationの一部として行う**。理由: C9・C10・C12の内部インタフェースに、検証と反映の分割・自然キー入力・呼び出し元トランザクションへの参加・DB直接のエクスポート・キャッシュの世代管理が必要で、本ユニットの機能設計・NFR設計で確定済みのため。改修は、既存のテスト(config・menu・permission・audit)を、そのまま成功させることを条件とする。
2. **menu-navigationは、キャッシュを持たない**(実装の調査結果。DBから直接読む)。NFR設計の「3ユニットの無効化」は、menu-navigationについては、無効化が不要(`PostCommit`の`invalidateCaches`は空の動作)。キャッシュを持つのは、config-engine(`ConfigCache`・`TranslationStore`)と、permission-engine(Caffeine)。`ConfigCache`は、既に、不変スナップショットを`AtomicReference`で差し替える方式であり、世代管理は、この方式を拡張する。
3. **NFR設計のレビュー指摘(iteration 2のsuggestion)のうち、実装の詳細にとどまるものは、設計書を変更せず、実装で採用する**: (R-12)キャッシュの状態・世代番号・データを、1つの不変な値にまとめ、compare-and-setで置き換える。(R-14)再読み込みのロックの保持者にも、上限をつけ、permission-engineにも、失敗の抑制を入れる。(R-15)監査イベントの永続化と、H2の更新競合の503への変換を、テストで確認する。(R-16)`consumes`は宣言しない(マッピング時の415が、`assignableTypes`のハンドラーに届かないため)。(R-17)再読み込みも`REPEATABLE_READ`で行う。**R-13(REPEATABLE_READ化の帰結: 行が重ならない同時の取り込みで、両ファイルの和集合が残りうる)は、設計書の記述の訂正が必要なため、実装では対処せず、ステージ全体の承認ゲートで、人間に提示する**。
4. **既知の未解決事項(踏襲。機能設計の残余リスク)**: (a)全置換で削除されるロールを、ユーザー(user-managementの`roleIds`・グループ経由)が保持している場合の扱いは、実装しない(運用の手順とし、`code-summary.md`に、既知の制約として記録する。選択肢: 取り込みの拒否・C11の`roleIds`の絞り込み・運用の手順のうち、運用の手順)。(b)permission-engineのブートストラップ判定が主権限の行数(0件)に依存し、設計(BR3.13「永続的に終了」)と一致しない点は、修正しない(取り込み側で、BR9.12により、主権限が0件になる取り込みを拒否して回避する)。
5. **`build.gradle.kts`**: Jackson 3(`tools.jackson`)は、`spring-boot-starter-web`が既に取り込んでいるため、追加の依存は不要(Step 1で確認する)。既存のJackson 2への明示的な依存は、`ProblemDetailsWriterTest`が使用しているため、削除しない(NFR設計の追補6は「確認のみ」)。
6. **チェックスタイル・カバレッジ**: 既存の`checkstyle`(警告0)・`jacoco`(行カバレッジ80%)の設定を、緩めない。新規・改修のコードは、これらを満たす。生成するソースの先頭には、Apache License 2.0のヘッダー(2026、agwlvssainokuni)を付ける(project.md)。
7. **性能の確認(NFR1.1・NFR1.2)のテストは、通常の`test`タスクから除外する**(JUnitのタグ`nfr-performance`。専用のGradleタスク`nfrPerformanceTest`で実行する。Build and Testで実行する)。`team.md`の「負荷・性能テストは既定には含めない」に従い、負荷の掛け方は含めず、単一の利用者による繰り返しの実行(30回)だけを行う。

## Steps

- [ ] Step 1: 事前の確認(調査。結果を`code-summary.md`に記録し、実装の前提とする)
  - (a)Jackson 3(`tools.jackson.databind`)が、`spring-boot-starter-web`から解決されること。使用するバージョンの、入れ子の深さ・文字列の長さ・重複プロパティの既定の挙動を、小さなテスト(`JacksonDefaultsProbeTest`)で確認して固定する(要件は、制限を設けないこと。Q2=C)。
  - (b)H2 2.4.240の`REPEATABLE_READ`が、別のトランザクションの反映の途中の状態を読まないこと、および、更新の競合が、どの例外(`ConcurrencyFailureException`の系統か)に変換されるかを、テスト(`H2IsolationProbeTest`)で確認する。
  - (c)内部設定DBのエンティティの主キーの生成方式(`IDENTITY`か否か)と、`hibernate.jdbc.batch_size`の設定の有無を確認する。挿入のバッチが効かない場合は、`JdbcTemplate`のバッチ更新を使う(Step 7・9・13で反映)。
  - (d)menu-navigationが、キャッシュを持たないこと、`MenuStructureApiImpl.importMenuStructure`の現状を確認する。
- [ ] Step 2: 契約書の追補(`inception/contract-design/contract-summary.md`)
  - C7(エクスポート・インポートの要求・応答・エラー、メッセージキー)・C9・C10・C12(検証と反映の分割、自然キー、呼び出し元トランザクションへの参加、DB直接のエクスポート、`PostCommit`、世代管理)・C15(`OperatorContext`の読み取り側のコンシューマーとしての本ユニット)・audit-logging(`ConfigImportExecutedEvent`の購読)の、追補を記載する。
- [ ] Step 3: 共通基盤の型(`com.mastersmith.common.configio`)
  - `PostCommit`(`invalidateCaches`・`publishEvents`の2つの動作)、`ImportValidationError`(位置・i18nキー・パラメータ)、`ApplyResult`(追加・更新・削除の件数と`PostCommit`)。単体テスト(test-after)。
- [ ] Step 4: 権限マトリクスの洗い出し(ATDD寄り、権限判定に限りtest-first。BR9.11・BR9.12)
  - permission-engineの`validateRbacImport`(昇格の判定・主権限0件・全置換の検証)の、権限マトリクスの組み合わせを、洗い出し、表形式(`@ParameterizedTest`+`@MethodSource`)の、失敗するテスト(`RbacImportValidationMatrixTest`)を、先に書く。ケース: 操作者のロールの実効権限(FULL・READ・NONE・指定なし)×取り込むエントリの権限×スコープ(SCHEMA・TABLE・COLUMN)×補助権限(CREATE・DELETE)×ブートストラップ状態(あり・なし)×ロールの階層継承×取り込みで新規に作るロール・テーブル(仮の識別)への昇格。
- [ ] Step 5: config-engine — キャッシュの世代管理(NFR4.2)
  - `ConfigCache`を、状態(`VALID`・`STALE`)・世代番号・スナップショットを、1つの不変な値にまとめ、compare-and-setで置き換える方式に拡張する(R-12)。`invalidate()`(失敗しえない)、読み取り前の`STALE`の確認と再読み込み(待ち上限つきの排他・二重の確認・独立した読み取り専用トランザクション(`REQUIRES_NEW`、`REPEATABLE_READ`)・終了時の世代の確認・失敗の共有と抑制の期間)。`TranslationStore`の、同様の扱いを確認して揃える。個別の更新の経路(既存)を、`STALE`・世代が進んだ場合は、`invalidate()`に変える。設定値(`reload-wait-timeout`・`reload-failure-backoff`)を、`application.yml`に追加する。テスト(スレッドを使った競合のテスト・失敗の抑制のテストを含む)を作成・実行する。
- [ ] Step 6: config-engine — エクスポートのDB直接の読み取り(Q2=A・NFR4.4)
  - `getExportableConfigSet`を、キャッシュを介さず、内部設定DBから直接読み、他から変更できないコピーを返すように改める。テスト。
- [ ] Step 7: config-engine — 取り込みの検証と反映の分割(BR9.9・BR9.10)
  - 自然キー(schemaName・tableName・columnName)で表す入力の型(`ConfigNaturalKeySet`)を追加。`validateConfigSet`(何も反映せず、誤りの一覧を返す。既存のBR1.1〜BR1.4の検証を、全件を集める形で再利用)と、`applyConfigSet`(伝播`MANDATORY`。全置換: 削除→追加・更新、段階ごとの`flush()`、`isPrimaryKey`の維持(BR1.14)、既知の課題(既存のColumnConfigを上書きできない)の解消、バッチ更新、`ApplyResult`(`PostCommit`を含む)を返す)を実装する。旧`importConfigSet`(本ユニットだけがコンシューマー)は、置き換える。テスト(既存のテストの更新を含む)。
- [ ] Step 8: menu-navigation — 検証と反映の分割(BR9.10・BR9.14)
  - `validateMenuStructure`(構造の規則: 階層・表示順。遷移先のテーブルの実在は、反映の順序により、反映の段階で満たされる)と、`applyMenuStructure`(伝播`MANDATORY`。全置換・再採番・`flush()`・`ApplyResult`)を実装する。`getExportableMenuStructure`は、DBから直接読む現状のまま。旧`importMenuStructure`は、置き換える。テスト。
- [ ] Step 9: permission-engine — RBACの書き出し・ブートストラップ判定の公開(BR9.1・BR9.11)
  - `exportRbac()`(ロール・グループ・グループとロールの対応・主権限・補助権限を、DBから直接読む。不変なコピー)。`isBootstrapState()`をC10に公開する。テスト。
- [ ] Step 10: permission-engine — 検証専用メソッドの実装(Green。Step 4のテストを通す)
  - `validateRbacImport(RbacImportSet, actorRoleId, bootstrapAtStart)`: 昇格の判定を、取り込み開始時点のスナップショットの実効権限を基準に、すべてのエントリについて行い、昇格するエントリをすべて集める。主権限が0件の場合の誤りを返す。ブートストラップ状態では、昇格の判定を行わない。既存の`PermissionEscalationChecker`・`PermissionResolver`を再利用する。
- [ ] Step 11: permission-engine — 反映と、キャッシュの世代管理
  - `applyRbacImport(RbacImportSet, actorRoleId)`(伝播`MANDATORY`。全置換: 削除の順序(補助権限・主権限→グループとロールの対応・グループ→ロール)→追加・更新、`flush()`、バッチ更新、サマリイベント(BR3.11)を`PostCommit.publishEvents`に含める。取り込みでは、`assignPermission`の1件ごとの`invalidateAll()`とイベント発行を行わない(既知のR-04の解消))。Caffeineのキャッシュに、世代番号を導入する(`invalidate()`は世代を進めて`invalidateAll()`。値に世代を持たせ、古い世代は破棄。ロードは、独立した読み取り専用トランザクション`REQUIRES_NEW`、失敗の抑制)。テスト(競合のテストを含む)。
- [ ] Step 12: audit-logging — `ConfigImportExecutedEvent`の購読
  - `ConfigImportExecutedEventListener`(同期の`@EventListener`と、全体のtry-catch。既存のリスナーと同じパターン)と、`AuditLogEntry`への対応付け(操作者・日時・結果・セクションごとの件数・失敗の分類。ファイルの内容は記録しない)。テスト(発行元が`REQUIRES_NEW`で発行した場合に、書き込みが永続化されること(R-15)を含む)。
- [ ] Step 13: configio — 値オブジェクト・パーサー・マッパー・検証(BR9.2・BR9.3・BR9.6〜BR9.8・BR9.13・BR9.14)
  - `ConfigDocument`ほかの値オブジェクト(entities.md)、`ImportErrorCollector`(最大100件・打ち切りの表示)、`ConfigDocumentParser`(`JsonNode`→`ConfigDocument`。`formatVersion`の確認、未知のプロパティの無視、構造・型・許容値・一意性の検証、JSON Pointerでの位置)、`ConfigDocumentMapper`(内部の表現⇔自然キー。メニューの入れ子)、`ReferenceValidator`(セクションをまたぐ参照)。テスト(表形式のバリデーションのテストを含む)。
- [ ] Step 14: configio — サービス・調整・イベント発行(BR9.4・BR9.5・BR9.10〜BR9.12・BR9.15〜BR9.17・NFR4.1・NFR4.2・NFR4.5)
  - `ConfigExportService`(`readOnly`・`REPEATABLE_READ`)、`ImportOrchestrator`(3ユニットの検証・反映。反映の順序と`flush()`)、`ConfigImportService`(`TransactionTemplate`・`REPEATABLE_READ`。失敗の分類。トランザクションの外の`catch`で失敗の監査イベント)、`PostCommitCoordinator`(1つの`TransactionSynchronization`。無効化→個別イベント→成功の監査イベントを、固定した順序・独立したtry-catchで実行)、`ConfigImportEventPublisher`(`REQUIRES_NEW`の`TransactionTemplate`で同期発行)。テスト。
- [ ] Step 15: configio — 認可・コントローラー・例外の処理(C7、BR9.4・BR9.7・BR9.17・BR9.21・NFR2.1・NFR2.6)
  - `ConfigImportAuthorizer`(操作者の解決→401、`canAccessScreen`→403)、`ConfigImportExportController`(`GET /api/config/export`・`POST /api/config/import`。`@RequestBody JsonNode`。`consumes`は宣言しない)、`ConfigImportExceptionHandler`(`assignableTypes`。RFC 9457のProblemDetails。422の`errors[]`。束縛の例外は、認可を先に行い、成功した場合だけ422(MALFORMED)と失敗の監査イベント。DBの障害の例外を503へ)。`@WebMvcTest`のテスト。
- [ ] Step 16: 構成・設定
  - `application.yml`(キャッシュの再読み込みの待ち上限・抑制の期間、バッチの設定(Step 1(c)の結果による))、エクスポートの`appVersion`の取得(`BuildProperties`。ビルド情報の生成を`build.gradle.kts`に追加する場合は、既存の設定への影響を確認)。`nfr-performance`のタグ・`nfrPerformanceTest`タスクの設定。
- [ ] Step 17: 統合テスト(`@SpringBootTest`、組込みH2)
  - エクスポート→インポートの往復。全置換(削除を含む)。原子性(反映の途中の失敗でロールバックし、DB・キャッシュが不変であること)。確定後のキャッシュの無効化と遅延の再読み込み。監査イベントの永続化(成功・失敗・MALFORMED。認可を通らない場合は発行されないこと)。エクスポートの反映中の一貫性(H2の`REPEATABLE_READ`)。
- [ ] Step 18: 設定駆動に特有の必須テスト(`team.md` Q8)
  - (a)安全失敗・バリデーション: 不正・不完全な設定ファイルで422となり、内部設定DBが変わらないこと。(b)複数プロファイル横断: 2種類以上の、業務ドメインの異なる設定プロファイル(商品マスタ用・蔵書マスタ用)で、エクスポート→インポートの同じ操作を流し、設定を差し替えるだけで動くこと。(c)認可拒否(negative-authorization): 401・403、権限のない操作の確実な拒否、初期状態の例外。
- [ ] Step 19: 権限昇格・主権限0件のテスト(表形式、U9側の結合。追加の合格条件、`team.md` Q6)
  - Step 4のマトリクスを、U9のエンドツーエンド(取り込みのAPI経由)にも適用し、昇格の拒否・主権限0件の拒否・初期状態の初回投入を確認する。
- [ ] Step 20: 並行・競合・障害のテスト
  - キャッシュの世代の競合(再読み込みの最中の`invalidate()`・個別の更新が失われない)、DB障害の間の再読み込みの失敗の抑制(待ちの連鎖が起きない)、取り込みの未確定の内容がキャッシュに載らない、`PostCommitCoordinator`の順序と、1つの動作の例外が他を妨げないこと、更新の競合が503に変換されること(R-15)、コミット時の例外の分類。
- [ ] Step 21: 性能の確認の実装(NFR1.1〜NFR1.3。`nfr-performance`のタグ)
  - 想定規模の上限(テーブル100・カラム約3,000・翻訳約6,000・ロール50・主権限約5,000)の設定を、連番から機械的に生成するフィクスチャ生成器と、計測のハーネス(ウォームアップ5回・計測30回・p95は最近順位法の29番目、2件同時3回はすべて目標以内、取り込み直後の最初の読み取り10回の最大値が3秒以内)。通常の`test`では実行しない(結果は、Build and Testで記録する)。
- [ ] Step 22: 品質・ドキュメント・トレーサビリティ
  - `./gradlew :backend:checkstyleMain :backend:checkstyleTest`(警告0)、既存のテスト(config・menu・permission・audit・その他)がすべて成功すること、`jacocoTestCoverageVerification`(行カバレッジ80%以上)を確認する。`code-summary.md`(既知の制約: ロールの削除とユーザーの`roleIds`・ブートストラップ判定・R-13を含む)、`source-manifest.json`、`traceability.json`を作成する。

## 既知の未解決事項(実装時に踏襲、修正はスコープ外)

- **ロールの削除とユーザーの`roleIds`**(機能設計の残余リスク6): 実装しない(運用の手順)。
- **permission-engineのブートストラップ判定**(機能設計の残余リスク7、設計BR3.13との不一致): 修正しない。
- **NFR設計のレビュー指摘R-13**(REPEATABLE_READ化の帰結の記述の訂正): 設計書は、ステージ全体の承認ゲートで、人間に提示する。実装は、設計書どおり(REPEATABLE_READ)。
