# Unit Test Instructions: audit-log

## テスト戦略

Testing Contract(code-generation-plan.md参照)の`test_strategy: comprehensive`を適用する: コンポーネントあたり10〜15件のテスト、Unit/Integration/E2Eの3種を揃え、NFRが求める場合はperformance/securityテストを追加する。順序は`custom`(test-after): 各レイヤーを実装してから、そのレイヤーのテストを書く。

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5(JUnit Platform)+ Spring Boot Test(`@DataJpaTest`、`@WebMvcTest`、`@SpringBootTest`)+ AssertJ(アサーション)+ Mockito(モック)。
- **テスト用DB**: 組み込みH2(インメモリモード、`application-test.yml`で本番のファイルベースH2と分離)。
- **ビルドツール統合**: Gradle標準の`test`タスク(JUnit Platform)。

## 本Unitのテスト実行コマンド(Gradle、本Unitスコープのみ)

```bash
./gradlew test --tests "com.mastersmith.auditlog.*"
```

上記コマンドはaudit-log Unit配下(`com.mastersmith.auditlog`パッケージ)のテストのみを実行し、他Unitのテストを含まない。Build and Testステージはこのコマンドをそのまま使用する。

`common`パッケージ(JWT認証基盤等)のテストは、それを最初に導入したUnitとして本Unit自身が以下のコマンドで実行する:

```bash
./gradlew test --tests "com.mastersmith.common.*"
```

## コンポーネント別テスト範囲(comprehensive: 10〜15件/コンポーネント)

### 1. データモデル層(AuditLogEntry・AuditLogSettings)— 目安10件

- AuditLogEntryのpersist/find往復(必須項目のみ、全項目埋め)
- actorAccountIdがnullのpersist(システム起因操作を想定)
- AuditLogSettingsのpersist/find、シングルトン一意制約違反(2件目作成の拒否)
- retentionDaysの既定値365の確認
- 各インデックス対象カラム(occurredAt・actorAccountId・actionType)を用いたクエリの正常動作確認

### 2. リポジトリ/データアクセス層(AuditLogEntryRepository・AuditLogSettingsRepository)— 目安12件

- actionType単独条件での絞り込み
- actorAccountId単独条件での絞り込み
- occurredAt範囲(from/toそれぞれ・両方)での絞り込み
- targetDescriptionの部分一致検索(Search欄、q相当)
- 上記条件の複数組み合わせ(AND条件、BR2.2)
- 条件なし(全件、ページング付き)
- 削除基準日時ちょうど・その前後1件ずつの境界値による一括削除
- 削除0件時の正常動作(該当なし)
- AuditLogSettingsのシングルトン行取得・更新

### 3. ビジネスロジック層(AuditLogEventListener・AuditLogService)— 目安15件

- イベントリスナー: 正常イベント受信時の記録
- イベントリスナー: 記録処理中の例外捕捉(発行元への非伝播をモックで検証)、ERRORログ出力の確認
- CSV形式エクスポートの内容・ヘッダー行検証
- JSON形式エクスポートの内容検証
- 不正な`format`値でのサービス層エラー
- `olderThanDays`: 1(最小値、境界)・0(拒否)・負値(拒否)・非整数相当(拒否)・通常値(受理)
- `retentionDays`: 同様の境界値検証
- 削除基準日時算出ロジックの正確性(固定時刻をモックし、日数計算を検証)
- 設定更新後、次回の削除確認ダイアログ初期値相当のretentionDays取得値が更新されていることの確認

### 4. API/エンドポイント層(AuditLogController)— 目安13件

- `GET /api/admin/audit-log`: 正常系(絞り込みなし、絞り込みあり)
- `GET /api/admin/audit-log`: isAdminクレーム欠如時の403(セキュリティテスト、BR5.1)
- `DELETE /api/admin/audit-log`: 正常系(204)
- `DELETE /api/admin/audit-log`: `olderThanDays`欠如・0以下・非整数での400
- `GET /api/admin/audit-log/export`: csv形式・json形式それぞれの正常系
- `GET /api/admin/audit-log/export`: `format`不正値での400
- `GET /api/admin/audit-log/settings`: 正常系(既定値365の返却)
- `PUT /api/admin/audit-log/settings`: 正常系・`retentionDays`不正値での400
- 未認証(トークンなし)リクエストの401

### 5. 統合テスト(Integration)— 目安3〜5件

- `@SpringBootTest`: イベント発行 → 実DB永続化 → REST API経由での取得、が一連で成立することの確認
- `@SpringBootTest`: 削除操作が実際にDBの行を削除することの確認(削除前後の件数比較)
- `@SpringBootTest`: 設定更新がAPI経由で永続化され、以降の取得に反映されることの確認

### 6. E2Eテスト — 目安2〜3件

- 認証込みの一連のシナリオ(複数件記録 → 絞り込み参照 → CSVエクスポート → 保持期間超過削除 → 削除後の再参照で件数減少)
- 権限なし利用者による全操作拒否の一連のシナリオ

## モック・スタブ方針

- イベントリスナーの例外捕捉テストでは、Repositoryをモック化(Mockito)して意図的に例外をスローさせる。
- 削除基準日時算出テストでは、時刻取得を抽象化(`Clock`または同等の注入可能な時刻源)し、固定時刻をテストで注入する。
- 外部システム(業務DB等)への依存はないため、外部サービスのモックは不要。

## テストデータ管理

- 各テストは`@DataJpaTest`/`@SpringBootTest`のトランザクションロールバック機構により、テスト間のデータ汚染を防ぐ(既定でテスト終了時にロールバック)。
- テストデータは各テストメソッド内で明示的に構築する(共有フィクスチャファイルは用いない、テストの独立性を優先)。

## カバレッジ目標

- comprehensive戦略の10〜15件/コンポーネントの目安件数を満たすこと。
- mvpスコープの80%ラインカバレッジ floor(org.md Testing Posture)をBuild and Testステージで確認する。本ステージでは上記のテストケース網羅を優先する。
