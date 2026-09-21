# Unit Test Instructions — config-import-export (U9)

## テストフレームワーク・構成

- JUnit 5 + Spring Boot Test(既存の`backend`モジュールと共通)。`@WebMvcTest`(コントローラー・例外の処理)、`@SpringBootTest`(トランザクション・キャッシュ・イベントの統合)。
- 組込みH2(内部設定DBのファイルモードではなく、テスト用のインメモリ。既存の他ユニットと同じ)。H2の`REPEATABLE_READ`の挙動は、別のトランザクションを使うテストで確認する。
- Mockito(各ユニットの内部インタフェースの、単体テストでの切り離し)。

## このユニットのテスト実行コマンド

本ユニット本体と、本ユニットのために改修する既存ユニット(config-engine・menu-navigation・permission-engine・audit-logging)の、改修に関わるテストを、次のコマンドで実行する(既存のテストクラスも含めて、改修したパッケージのテストがすべて成功すること)。

```
./gradlew :backend:test --tests "com.mastersmith.configio.*" --tests "com.mastersmith.common.configio.*" --tests "com.mastersmith.config.*" --tests "com.mastersmith.menu.*" --tests "com.mastersmith.permission.*" --tests "com.mastersmith.audit.*"
```

性能の確認(NFR1.1・NFR1.2。通常の`test`からは除外):

```
./gradlew :backend:nfrPerformanceTest --tests "com.mastersmith.configio.performance.*"
```

チェックスタイル: `./gradlew :backend:checkstyleMain :backend:checkstyleTest`

## カバレッジ目標

- 行カバレッジ80%以上(`team.md`確定のmvp系スコープフロア。`jacocoTestCoverageVerification`)。緩めない。
- **追加の合格条件(`team.md` Q6=A)**: 権限判定に関わる実装(permission-engineの`validateRbacImport`・`applyRbacImport`、U9の昇格拒否・主権限0件の拒否)は、主要な権限マトリクスの組み合わせを網羅する表形式(`@ParameterizedTest`+`@MethodSource`)のテストを必須とする(Step 4・Step 19)。

## テスト規模(Comprehensive戦略)

各コンポーネント10〜15件程度(単体)。加えて、統合テスト・エンドツーエンド(取り込みAPI経由)・設定ファイルの契約テスト(形式の検査)。対象: `ConfigDocumentParser`・`ConfigDocumentMapper`・`ReferenceValidator`・`ImportErrorCollector`・`ImportOrchestrator`・`ConfigImportService`・`ConfigExportService`・`PostCommitCoordinator`・`ConfigImportEventPublisher`・`ConfigImportAuthorizer`・`ConfigImportExportController`・`ConfigImportExceptionHandler`、および、改修した各ユニットの検証・反映・キャッシュの世代管理。

## 必須テスト種別(`team.md` Q8由来)

- (a)安全失敗・バリデーション: 不正・不完全な設定ファイルで422となり、内部設定DBが変わらないこと(Step 18)。
- (b)複数プロファイル横断: 2種類以上の業務ドメインの設定プロファイル(商品マスタ用・蔵書マスタ用)で、エクスポート→インポートの同じ操作が動くこと(Step 18)。
- (c)認可拒否(negative-authorization): 401・403を確実に拒否すること。初期状態の例外(Step 18)。
- 監査ログの完全性テスト(更新・作成・削除の全操作の網羅)は、MVPでは必須としない。ただし、取り込みの監査イベントが、成功・失敗のそれぞれで、1回の取り込みにつき1件、永続化されること(BR9.16)は確認する(Step 12・Step 17)。

## モック・スタブ方針

- 単体テストは、各ユニットの内部インタフェース(`ConfigEngineApi`・`MenuStructureApi`・`PermissionEngineApi`)をMockitoで切り離す。
- トランザクション・キャッシュの世代・`afterCommit`の順序・監査イベントの永続化・H2の分離レベルは、モックせず、実際の組込みH2で検証する(統合テスト)。
- 競合のテスト(Step 20)は、実際のスレッド(`ExecutorService`・`CountDownLatch`)を使い、`Thread.sleep`による時間待ちに依存しない。

## テストデータ管理

- テストごとに、独立した設定(連番から機械的に生成。業務固有の名前をコードに埋め込まない。BR9.20)を、ファクトリで構築する。統合テストは、各テストの前に、内部設定DBを初期化する。
- 性能の確認のフィクスチャは、想定規模の上限を、連番から機械的に生成する(Step 21)。

## 対象テストファイル一覧(想定)

| レイヤー | テストクラス(想定) | 対応するPlan Step |
|---|---|---|
| 事前の確認 | `JacksonDefaultsProbeTest`・`H2IsolationProbeTest` | Step 1 |
| 共通基盤 | `PostCommitTest`・`ImportValidationErrorTest` | Step 3 |
| 権限マトリクス(先行) | `RbacImportValidationMatrixTest` | Step 4・10 |
| config-engine | `ConfigCacheGenerationTest`・`ConfigExportDirectReadTest`・`ConfigApplyValidateTest` | Step 5〜7 |
| menu-navigation | `MenuStructureValidateApplyTest` | Step 8 |
| permission-engine | `RbacExportTest`・`RbacApplyTest`・`PermissionCacheGenerationTest` | Step 9〜11 |
| audit-logging | `ConfigImportExecutedEventListenerTest` | Step 12 |
| configio(部品) | `ConfigDocumentParserTest`・`ConfigDocumentMapperTest`・`ReferenceValidatorTest`・`ImportErrorCollectorTest` | Step 13 |
| configio(サービス) | `ConfigImportServiceTest`・`ConfigExportServiceTest`・`PostCommitCoordinatorTest`・`ConfigImportEventPublisherTest` | Step 14 |
| configio(Web) | `ConfigImportAuthorizerTest`・`ConfigImportExportControllerTest`・`ConfigImportExceptionHandlerTest` | Step 15 |
| 統合・必須種別 | `ConfigImportExportIntegrationTest`・`ConfigDrivenProfilesE2ETest`・`ConfigImportNegativeAuthorizationTest` | Step 17〜19 |
| 並行・競合 | `CacheGenerationConcurrencyTest`・`ImportFailureModesTest` | Step 20 |
| 性能の確認 | `ConfigImportExportPerformanceTest`(`nfr-performance`) | Step 21 |
