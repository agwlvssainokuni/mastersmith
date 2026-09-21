# Code Summary — config-import-export (U9)

`code-generation-plan.md`(22ステップ、すべて実施)に基づく、config-import-exportユニットのCode Generationの結果。本ユニットは、設定一式のJSONのエクスポート(`GET /api/config/export`)・インポート(`POST /api/config/import`)を担う(C7)。あわせて、機能設計・NFR設計で確定した追補として、config-engine・menu-navigation・permission-engine・audit-loggingを改修した。

## 作成・変更・削除したファイル

一覧は、`source-manifest.json`(111件)を参照する。主な構成は次のとおり。

| パッケージ | 内容 |
|---|---|
| `com.mastersmith.configio`(新規) | `document`(`ConfigDocument`)、`parser`(`ConfigDocumentParser`・`ImportErrorCollector`)、`mapper`(`ConfigDocumentMapper`・`NaturalKeyIndex`)、`validation`(`ReferenceValidator`)、`service`(`ConfigExportService`・`ConfigImportService`・`ImportOrchestrator`・`PostCommitCoordinator`ほか)、`event`(`ConfigImportEventPublisher`・`ConfigImportExecutedEvent`)、`exception`、`web`(`ConfigImportAuthorizer`・`ConfigImportExportController`・`ConfigImportExceptionHandler`・`ImportResultResponse`) |
| `com.mastersmith.common.configio`・`common.cache`(新規) | `PostCommit`・`ApplyResult`・`ImportValidationError`・`SectionCounts`・`ImportSections`・`ImportMessageKeys`、`ReloadFailureBackoff`・`CacheReloadUnavailableException` |
| `com.mastersmith.config`(改修) | `ConfigCache`(状態・世代・スナップショットを1つの不変な値にまとめ、compare-and-setで置き換え。`invalidate()`・遅延の再読み込み)、`transfer/ConfigTransfer`(DB直接のエクスポート・検証と反映の分割・全置換・自然キー・`isPrimaryKey`の維持)、`ConfigEngineApi`・`ConfigModelStore`・`ConfigValidator`。旧`importConfigSet`と`ConfigImportSet`は削除 |
| `com.mastersmith.menu`(改修) | `MenuStructureApi`・`MenuStructureApiImpl`(`validateMenuStructure`・`applyMenuStructure`。全置換・再採番)。旧`importMenuStructure`は置き換え。menu-navigationはキャッシュを持たない |
| `com.mastersmith.permission`(改修) | `rbacio`(`RbacExporter`・`RbacImportValidator`・`RbacImporter`・`RbacTransfer`・`ActorGrantSnapshot`・`ReservedScopes`)、`cache/PermissionCacheControl`・`PermissionCacheKey`(世代番号)、`PermissionEngineApi`(`exportRbac`・`isBootstrapState`・`validateRbacImport`・`applyRbacImport`)、`event/PermissionImportedEvent` |
| `com.mastersmith.audit`(改修) | `ConfigImportExecutedEventListener`・`PermissionImportedEventListener`(同期の`@EventListener`と全体のtry-catch。既存のリスナーと同じパターン)、`AuditLogEventMapper` |
| 設定・ビルド | `application.yml`(本番・テスト。Hibernateのバッチ設定・キャッシュの再読み込みの設定)、`build.gradle.kts`(`BuildProperties`の生成、`nfr-performance`タグの除外と`nfrPerformanceTest`タスク) |
| 契約書(`aidlc/`) | `inception/contract-design/contract-summary.md`にC7・C9・C10・C12・C15・audit-loggingの追補(Step 2) |

## 主な実装の決定

- **取り込みの原子性(NFR4.1)**: `ConfigImportService`は`@Transactional`を付けず、`TransactionTemplate`(`REPEATABLE_READ`)で検証・反映の部分だけを包み、トランザクションの外で失敗の分類と失敗の監査イベントの発行を行う。反映の順序は、削除(依存の逆順)→追加・更新(依存の順)で、段階の境界で`flush()`する。
- **確定後の処理(NFR4.2)**: `PostCommitCoordinator`が1つだけ`TransactionSynchronization`を登録し、`afterCommit`で、3ユニットの無効化→個別イベント→成功の監査イベントを、固定した順序・独立したtry-catchで実行する。
- **監査イベントの永続化(NFR4.5)**: `ConfigImportEventPublisher`が、`REQUIRES_NEW`の`TransactionTemplate`の中で同期発行する(user-managementと同じ対処)。
- **キャッシュの世代管理**: config-engineは、状態・世代・スナップショットを1つの不変な値にまとめ、compare-and-setで置き換える。permission-engineは、世代番号をキーに含める方式にし、トランザクションの中にいる間は、キャッシュを介さず、そのトランザクションの中で解決する。再読み込み・ロードは`REQUIRES_NEW`の読み取り専用トランザクションで行い、失敗は抑制の期間を置いて間引く(`ReloadFailureBackoff`)。
- **エクスポート(NFR4.4)**: 読み取り専用・`REPEATABLE_READ`のトランザクションの中で、3ユニットが、キャッシュを介さずDBから直接読む。
- **認可(NFR2.1)**: コントローラーの`ConfigImportAuthorizer`(操作者の解決→401、`canAccessScreen`→403)。`@RequestBody`の束縛の例外は、`ConfigImportExceptionHandler`が、同じ認可を先に行い、成功した場合だけ、422(MALFORMED)と失敗の監査イベントを返す。
- **例外の翻訳(NFR4.3)**: H2の更新競合・ロック待ちのタイムアウトが、素のJPA例外として上がる場合があるため、`ConfigImportExceptions.translate`でSpringの`DataAccessException`系に翻訳した。503は、`ConcurrencyFailureException`・`QueryTimeoutException`・`DataAccessResourceFailureException`・`TransientDataAccessException`・`TransactionException`。それ以外は500。
- **バッチ書き込み**: 主キーはアプリ採番のUUIDで`IDENTITY`ではない。Spring Dataの`save`は、採番済みIDの新規エンティティを`merge`にして、挿入の前に1件ずつSELECTが走るため、挿入は`EntityManager.persist`を使う。`hibernate.jdbc.batch_size=50`・`order_inserts`・`order_updates`を追加した。
- **Jackson**: Jackson 3系(`tools.jackson.databind` 3.1.5)を使う。既定は、入れ子の深さ500・文字列長1億文字・ドキュメント長は無制限で、重複するプロパティは黙って後勝ちで上書きされる(`JacksonDefaultsProbeTest`で固定)。

## テストの結果(リードによる独立した再実行を含む)

- **ユニットテスト手順書のコマンド**(`com.mastersmith.configio.*`・`common.configio.*`・`config.*`・`menu.*`・`permission.*`・`audit.*`)と、`checkstyleMain`・`checkstyleTest`を、リードが独立に再実行した: **3,224件、失敗0・エラー0・スキップ0。BUILD SUCCESSFUL。checkstyle警告0**。
- 開発エージェントの報告(リードは再実行していない): 全体(`:backend:test :backend:checkstyleMain :backend:checkstyleTest :backend:spotlessCheck :backend:jacocoTestCoverageVerification`)は4,202件・失敗0・BUILD SUCCESSFUL。プロジェクト全体の行カバレッジは95.22%(80%以上。基準は緩めていない)。新規パッケージのカバレッジは、`configio/*`が97.5〜100%、`config/transfer`が99.0%、`permission/rbacio`が99.7%。
- **権限マトリクス(先行、Step 4)**: `RbacImportValidationMatrixTest`を実装の前に書き、スタブに対して634件が失敗する(Red)ことを確認してから、Step 10で実装した(Green)。U9のエンドツーエンドの表形式は`ConfigImportEscalationMatrixE2ETest`(32件)。
- **必須の種別(team.md Q8)**: (a)`ConfigImportSafeFailureTest`(不正・不完全なファイル22種を、既存設定のあるDBと空のDBの両方に投入し、422・全9表・エクスポート内容・キャッシュが不変であることを確認)、(b)`ConfigDrivenProfilesE2ETest`(商品マスタ用・蔵書マスタ用の2プロファイルで同じシナリオを流し、コードに業務名がないことをソースの走査で確認)、(c)`ConfigImportNegativeAuthorizationTest`(401、ロール未選択、実在しないロール、権限なし、画面NONE、束縛の失敗時は403で監査行なし)。
- **並行・競合・障害(Step 20)**: `CacheGenerationConcurrencyTest`(3件)、`ImportFailureModesTest`(4件。更新の競合とロック待ちが503になり、失敗の監査イベントが確定して残ること)。
- **性能の確認(Step 21、開発エージェントの1回の実行)**: 環境は8 CPU、ヒープ1024MB、macOS aarch64、Java 25.0.4、H2のファイルモード・単一プロセス。規模は、テーブル100・カラム3,000・翻訳6,000・ロール51・主権限約5,010・補助権限約510、本体約1.9MB。エクスポートp95=78ms(目標3秒)、インポートp95=652ms(約9,100件の更新。目標10秒)、空DBへの最初の取り込み=1,581ms、2件同時3回・取り込み直後の最初の読み取り10回の最大=35ms(目標3秒)。結果は`backend/build/nfr-performance-result.txt`(build配下)。NFR Designの予算(インポート7.6秒)に対して、実測は大幅に下回った。

## 計画からの逸脱・判断

1. **`PermissionImportedEvent`・`PermissionImportedEventListener`を新設**: 既存の`PermissionChangedEvent`は1件ごとの割当用で、件数を持てないため、permission-engineのサマリイベント(BR3.11)用に、別のレコードを新設し、audit-loggingに同期の`@EventListener`を追加した(Step 12の範囲の拡張)。
2. **旧メソッドの削除と、旧テストの削除・書き換え**: 旧`importConfigSet`・`importMenuStructure`・`ConfigImportSet`を削除した。旧メソッドの単体テスト(`ConfigModelStoreTest`の6件、`MenuStructureApiImplTest`の6件)を削除し、`PermissionEngineIntegrationTest`は、データ投入の方法を書き換えた(検証内容は同じ)。置き換え先のテストは、Step 6〜8で作成した。他の既存テストは、変更なしで成功する。
3. **`ConfigCache`の個別の更新の`reload()`**: 従来どおり、呼び出し元のトランザクションの中で読む。`STALE`の間の読み取りだけが、`REQUIRES_NEW`・`REPEATABLE_READ`・読み取り専用の独立したトランザクションで再読み込みする。
4. **permission-engineのキャッシュ**: 世代番号をキーに含める方式にした(NFR設計の「値に世代を持たせる」と等価)。呼び出し元がトランザクションの中にいる間は、キャッシュを介さず、そのトランザクションの中で解決する(未確定の内容や、古いスナップショットを、共有のキャッシュに載せないため)。
5. **例外の翻訳を追加**(実装中に発見した不具合の修正): 各ユニットが`EntityManager.flush()`を直接呼ぶため、H2の更新競合・ロック待ちが素のJPA例外として上がり、初回は500になった。Springの`DataAccessException`系への翻訳を、サービスとハンドラーの両方に入れた。
6. **`ConfigImportExceptionHandler`の依存は`ObjectProvider`**: `@ControllerAdvice`が、既存の`@WebMvcTest`スライスにも読み込まれ、認可・サービスのBeanがなく、98件が失敗したため。
7. **昇格判定の基準**: 操作者の割当を1回の問い合わせで全件読み、メモリ上で解決する(`ActorGrantSnapshot`)。既存の`PermissionResolver`と、3,888通りの組み合わせで一致することを、`ActorGrantSnapshotConsistencyTest`で確認した(解決規則を二重に持つことによる食い違いの防止)。
8. **位置の表現**: パーサーの位置は、JSON Pointer風ではなく、`schema.tables[3].columns[2].editorType`形式(BR9.7・C7追補と同じ)にした。NFR設計書は、JSON Pointer(`/schema/tables/0/...`)と記述しており、**設計書の記述と実装の表記が異なる**(機能設計のBR9.7は「JSON上の位置」)。承認ゲートで人間に提示する。
9. **ロール階層継承**: 計画書・テストの契約文にある「ロール階層継承」は、permission-engineのBR3.4により、実装上は存在しない(階層があるのはスコープ COLUMN→TABLE→SCHEMA)。マトリクスは、スコープの階層を網羅している。
10. **`MenuStructureApiImpl`の遷移先の実在確認**: コンストラクターの引数を残して未使用にした(既存のテストの構築との互換のため)。実在は、schemaを先に反映する順序で満たされる。
11. **`Clock`**: Beanがあれば使い、なければ`Clock.systemUTC()`。

## 既知の制約・未解決事項(計画どおり、対処していない)

- **ロールの削除とユーザーの`roleIds`**(機能設計の残余リスク6): 全置換で削除されるロールを、user-managementの`roleIds`(直接付与)やグループ経由で保持しているユーザーには、追従しない。選択可能なロールの一覧に、実在しないロールが残りうる(選ぶと、権限なし(NONE)と判定される)。運用の手順とする。
- **ブートストラップ判定**(機能設計の残余リスク7): permission-engineの判定が主権限の行数0件に依存し、設計BR3.13「永続的に終了」と一致しない。修正していない。取り込み側は、BR9.12(主権限が0件になる取り込みの拒否)で回避した。他の経路で主権限が0件になると、例外が再び有効になる。
- **R-13(NFR設計のレビュー指摘)**: REPEATABLE_READ化により、行が重ならない同時の取り込みでは、両ファイルの和集合が残りうる。設計書の記述の訂正が必要なため、実装は設計書どおり。行が重なる場合は、競合で503になることを、`ImportFailureModesTest`で確認した。承認ゲートで人間に提示する。
- **行ロックの待ち**: 取り込みAが未確定の間に、取り込みBが同じ行を書くと、H2の既定の約2秒でタイムアウトし、503になる(NFR4.3どおり)。
- **権限を「除く」だけの取り込みは、昇格判定の対象外**: 判定は「ファイルに書かれたエントリ」が対象(BR9.11どおり)。下位の制限(NONE)を外すと、実効権限が、継承で上がりうるが、エントリがないため検出しない。`loweringOrRemovingPermissionsIsNeverAnEscalation`で挙動を固定した。
- **アクティブロール未選択(null・空)の操作者の昇格判定**: 割当なしとして扱う(fail closed)。ブートストラップ状態では、昇格判定自体を行わない。
- **`nfrPerformanceTest`**は、タグの除外のため、単独ではjacocoの対象外。

## 実装しなかったもの(計画の範囲外)

- frontend-ui(U12)への要求(確認モーダルなど)。
- 内部設定DBの退避の機能(運用の手順のみ。設計どおり)。
- 大きさ・時間の上限、取り込みの排他(MVPの受け入れ済みリスク)。
