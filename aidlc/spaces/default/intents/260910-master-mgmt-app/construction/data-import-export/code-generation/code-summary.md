# Code Summary — data-import-export (U8)

## 作成・変更ファイル

### 前提修正(config-engine、brownfield in-place修正)

- `backend/src/main/java/com/mastersmith/config/entity/ColumnConfig.java` — `isPrimaryKey`フィールド追加。新規4引数コンストラクタ(`tableConfigId, columnName, editorType, isPrimaryKey`)を追加し、setterは非公開(BR1.14: writeTableConfigDraft経由の構築時にのみ設定可能)。既存3引数コンストラクタは互換維持(isPrimaryKey=false)。
- `backend/src/main/java/com/mastersmith/config/dto/ColumnDraftEntry.java` — `isPrimaryKey`フィールド追加(2引数コンストラクタは互換維持)。
- `backend/src/main/java/com/mastersmith/config/store/ConfigEngineApi.java` — `getTableConfigById(tableConfigId)`を追加。data-import-exportがtableConfigIdのみから物理テーブル名(schemaName/tableName)を解決するために必要な契約拡張(functional-spec.md W1/W2はtableConfigIdのみを受け取るため)。
- `backend/src/main/java/com/mastersmith/config/store/ConfigModelStore.java` — 上記2点の実装。
- `backend/src/test/java/com/mastersmith/config/entity/ColumnConfigJpaTest.java`・`backend/src/test/java/com/mastersmith/config/store/ConfigModelStoreTest.java` — isPrimaryKeyの往復・伝播テストを追加。

### 本体実装(`com.mastersmith.dataio`、新規パッケージ)

- `DataImportExportApi.java` — C13契約インタフェース(`exportCsv`/`importCsv`)。
- `dto/` — `CsvExportRequest`, `CsvImportRequest`, `CsvColumnDefinition`, `CsvImportRowResult`, `ImportResult`, `RowError`, `ImportOperation`, `ImportOutcome`(いずれもrecord、entities.md準拠)。
- `event/ImportExecutedEvent.java` — Springイベントとしてfire-and-forget発行するBR8.9監査サマリイベント(config-engineの`ConfigChangedEvent`と同じパターン)。
- `csv/CsvColumnDefinitionResolver.java` — `ConfigEngineApi.getColumnConfigs`からhidden列・permittedColumnNames対象外列を除外(BR8.2)。
- `csv/CsvRowValidator.java` — 型変換・validationRule適用・upsert判定(BR8.3, BR8.5, BR8.6)。DBアクセスを持たず、主キー存在チェックは呼び出し元が`Predicate<String>`として注入する設計(単体テスト容易性)。
- `csv/RowValidationResult.java` — バリデーション結果(`CsvImportRowResult` + 変換後の値)の内部値オブジェクト。
- `config/BusinessDataSourceConfig.java` — 業務データ用RDBMS接続(`mastersmith.business-datasource.enabled=true`時のみBean生成、内部設定DBとは別接続)。
- `service/CsvExportService.java` — JDBCカーソル経由のストリーミングエクスポート(BR8.1, BR8.10)。
- `service/CsvImportService.java` — ストリーミング読み取り→全行検証→(全件有効時のみ)`TransactionTemplate`による単一トランザクションでのINSERT/UPDATE一括反映(BR8.4, BR8.7, BR8.10)、`ImportExecutedEvent`発行(BR8.9)。
- `service/DataImportExportApiImpl.java` — `DataImportExportApi`の実装、`CsvExportService`/`CsvImportService`への委譲ファサード。
- `exception/CsvFormatException.java`・`CsvExportException.java` — BR8.1形式違反・エクスポート失敗の例外。

### テスト(`com.mastersmith.dataio`配下、test-after)

- `csv/CsvColumnDefinitionResolverTest.java`
- `csv/CsvRowValidatorTest.java`(テーブル駆動)
- `service/CsvExportServiceTest.java`
- `service/CsvImportServiceTest.java`(組込みH2 + Spring `ApplicationEvents`)

### ビルド・設定

- `backend/build.gradle.kts` — Apache Commons CSV依存追加。
- `backend/src/main/resources/application.yml` — 業務データ用RDBMS接続プロパティを環境変数経由で設定可能化(既定はenabled=false)。

## 計画からの逸脱

- **`ConfigEngineApi.getTableConfigById`の追加**(計画外): 承認済みプランにはなかったが、C13契約(`exportCsv`/`importCsv`はtableConfigIdのみを受け取る)と`ConfigEngineApi.getTableConfig(schemaName, tableName)`(schemaName/tableNameが必要)の間にギャップがあることが実装中に判明したため、逆方向解決用のメソッドを追加した。config-engine(U1)自身の契約(C9)への軽微な拡張であり、既存コンシューマーへの影響はない。
- **`CsvImportService`のトランザクション方式**: `@Transactional`ではなく`TransactionTemplate`を採用(自己呼び出し(self-invocation)によるSpringプロキシ経由の`@Transactional`無効化を避けるため)。

## テストカバレッジ概要

- `./gradlew :backend:test`(プロジェクト全体)実行結果: **BUILD SUCCESSFUL**、テストケース176件全てpass、命令カバレッジ83%(80%フロアを充足)。
- 本Unit本体のみを対象とした`./gradlew :backend:test --tests "com.mastersmith.dataio.*" --tests "com.mastersmith.config.entity.ColumnConfigJpaTest" --tests "com.mastersmith.config.store.ConfigModelStoreTest"`もgreen。

## 修正した既知の回帰

実装中の全体テスト実行で、既存の`ConfigEngineFailFastStartupTest`(Spring Boot全体起動を伴う統合テスト)が、新規`CsvExportService`が`businessDataSource`Beanを無条件に要求することによる起動失敗を起こしていた。`CsvExportService`に`BusinessDataSourceConfig`と同じ`@ConditionalOnProperty(prefix = "mastersmith.business-datasource", name = "enabled", havingValue = "true")`を付与し解消した(`CsvImportService`・`DataImportExportApiImpl`は実装時点から同条件を付与済み)。

## 再レビュー(iteration 1、NOT-READY)への修正(2026-09-21)

本ユニットは、Code Generationのレビュー導入前に作成されたため、初回のアーキテクチャレビューで、重要度の高い指摘4件と細かい指摘3件(`reviews/review-01.md`)を受けた。次のとおり修正した(詳細・`[assumption]`は`code-generation-notes.md`)。

| 指摘 | 対処 |
|---|---|
| R-01(主キー値が数値に変換できない行が、ファイル全体の形式エラーになる) | 主キー列自体の検証に失敗した行では存在確認を呼ばず、行単位エラーのみ返す。`CsvFormatException`をCSVパース由来の失敗に限定 |
| R-02(エクスポートした日時が、インポートで読めない) | `CsvValueFormatter`(新規)で、エクスポート値をインポートが受け付ける正規形式(日付`yyyy-MM-dd`、日時は`T`区切りのISO 8601、真偽値`true`/`false`、数値は指数表記なし)に整形。往復テスト`CsvRoundTripTest`を追加 |
| R-03(ストリーミングが対象RDBMSで効かない) | 読み取り専用+自動コミット無効のコネクションと、ドライバ別のfetchSize(PostgreSQLほかは500、MySQL/MariaDBは`Integer.MIN_VALUE`)。**実RDBMSでの検証は未実施**(JDBCモックによる呼び出し順序の確認のみ。Build and Test以降の課題) |
| R-04(空セル・ヘッダーにない列が、既存値をNULLで上書きする) | CSVヘッダーにない列は、UPDATEでは対象外(既存値を維持)、INSERTでは`required`のみエラー。空セルは、`required`でなければNULL(`[assumption]`) |
| R-05〜R-07(細かい指摘) | 呼び出し元の`OutputStream`を閉じない、識別子の引用符(`SqlIdentifiers`、新規)、0件更新は全体ロールバック、コミット時のDB制約違反は行エラー+`ImportExecutedEvent(committed=false)`、テストの拡充。1行ごとの存在確認(N+1)は、照合順序・数値表現の食い違いを避けるため維持(H2で10万行の計測は追記のとおり) |

- テスト: dataio配下は6クラス・94件(修正前は27件)。プロジェクト全体は1,389件、失敗0。行カバレッジは全体93.1%(フロア80%)。
- 残る懸念: R-03の実DB検証。コミット時の制約違反の`RowError.field`が番兵値`*`であること(frontend-uiの扱いの確認)。ドライバ別の前提(接続URLで`useAffectedRows=true`を指定しない、MySQL/MariaDBはストリーミング中に同じ接続で他のSQLを発行しない)をBuild and Testの手順へ反映すること。
- `unit-test-instructions.md`は、承認済みの内容のため編集していない。記述(`@SpringBootTest`など)と実態(`CsvImportServiceTest`は`@SpringJUnitConfig`+独立H2)に差異がある。
