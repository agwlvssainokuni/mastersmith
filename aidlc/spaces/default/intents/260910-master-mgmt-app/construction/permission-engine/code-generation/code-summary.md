# Code Summary — permission-engine (U3)

## 作成・変更ファイル

### 前提修正(config-engine、brownfield in-place修正)

- `backend/src/main/java/com/mastersmith/config/store/ConfigEngineApi.java` — `findColumnConfigById(columnConfigId): Optional<ColumnConfig>`を追加。permission-engineが主権限のスコープ階層解決(BR3.4: COLUMN→TABLE→SCHEMA)において、COLUMNスコープから親TABLEのtableConfigIdを解決するために必要な契約拡張(`getTableConfigById`追加と同種、C9契約への軽微な拡張)。存在しない場合は例外ではなく`Optional.empty()`を返す設計とし、呼び出し元がこの不在を「データ不整合」ではなく「それ以上の階層解決を打ち切りBR3.6のデフォルトへフォールバックする」という通常の制御フローとして扱えるようにした。
- `backend/src/main/java/com/mastersmith/config/cache/ConfigCache.java` — `columnConfigById`(columnConfigId→ColumnConfig)のインデックスマップをSnapshotへ追加し、`findColumnConfigById`アクセサを追加。
- `backend/src/main/java/com/mastersmith/config/store/ConfigModelStore.java` — `findColumnConfigById`をキャッシュへ委譲する形で実装。
- `backend/src/test/java/com/mastersmith/config/cache/ConfigCacheTest.java`・`backend/src/test/java/com/mastersmith/config/store/ConfigModelStoreTest.java` — 上記追加メソッドのテストを追加(ヒット/未検出の両ケース)。

### 本体実装(`com.mastersmith.permission`、新規パッケージ)

- `PermissionEngineApi.java`(トップレベル) — C10契約インタフェース。`resolveEffectivePermission`/`canAccessScreen`/`assignPermission`/`assignAuxiliaryPermission`/`getGroupDerivedRoleIds`。C10 YAMLからの追補・差異はJavadocに明記(下記「計画からの逸脱」参照)。
- `entity/` — `Role`, `PrimaryPermission`, `AuxiliaryPermission`, `Group`(テーブル名`permission_group`、SQL予約語`group`との衝突回避), `GroupMembership`+`GroupMembershipId`(複合キー), `GroupRole`+`GroupRoleId`(複合キー), `ScopeType`(SCHEMA/TABLE/COLUMN), `PermissionLevel`(FULL/READ/NONE、`rank()`による強さの比較)。`(role_id, scope_type, scope_ref)`一意複合インデックスをPrimaryPermission/AuxiliaryPermissionへ付与(scalability-design.md)。
- `repository/` — Spring Data JPAリポジトリ6種。`GroupMembershipRepository.findByIdUserId`・`GroupRoleRepository.findByIdGroupIdIn`が選択可能ロール一覧算出(W3)を支える。
- `resolver/PermissionResolver.java` — BR3.4(主権限COLUMN→TABLE→SCHEMA)・BR3.5(補助権限TABLE→SCHEMA)・BR3.6(デフォルト)のスコープ階層解決アルゴリズム。config-engineのTABLE/SCHEMA解決で例外(`TableConfigNotFoundException`)や不在(`Optional.empty()`)が生じた場合は、階層探索を打ち切りBR3.6のデフォルトへフォールバックする(データ不整合を安全側で扱う設計判断)。
- `cache/` — `PermissionCacheKey`(activeRoleId, scopeType, scopeRefの3項組)、`PermissionCacheConfig`(Caffeine、既定TTL 30秒・既定最大サイズ5000エントリ、`recordStats()`を有効化しMicrometer `CaffeineCacheMetrics`で`permission`名で計装)。
- `bootstrap/BootstrapStateChecker.java` — PrimaryPermission行数=0判定(BR3.13)。
- `escalation/PermissionEscalationChecker.java` — BR3.8権限昇格防止。`permission_escalation_denied_total`カウンタのインクリメントと拒否時の構造化ログ出力を担う。
- `event/PermissionChangedEvent.java` — Springアプリケーションイベント(fire-and-forget、BR3.11)。
- `exception/PermissionEscalationException.java` — BR3.8違反時の例外。
- `dto/EffectivePermission.java` — C10の戻り値レコード。
- `service/PermissionEngineApiImpl.java` — 公開API実装。`permission_check_duration_seconds`タイマーで`resolveEffectivePermission`を計装し、`assignPermission`/`assignAuxiliaryPermission`成功時に変更内容(ロールID・スコープ・変更前後のレベル)を構造化ログへ記録する。

### テスト(`com.mastersmith.permission`配下)

- `resolver/PermissionResolverTest.java` — **ATDD/test-first**(team.md Q4/Q5の例外適用)。BR3.4/BR3.5/BR3.6の権限マトリクスを`@ParameterizedTest`+`@MethodSource`のテーブル駆動テストを含め16件で検証。
- `entity/`配下 — 各エンティティのJPAマッピングテスト(往復・一意性制約)、`PermissionLevelTest`(強さの順序)。
- `repository/`配下 — 6リポジトリの`@DataJpaTest`統合テスト(組込みH2)。
- `cache/PermissionCacheConfigTest.java` — 実インスタンスのCaffeineキャッシュでTTL・サイズ上限ポリシー・`invalidateAll()`・統計計上を検証。
- `bootstrap/BootstrapStateCheckerTest.java`・`escalation/PermissionEscalationCheckerTest.java` — 昇格許可/拒否/自己昇格拒否/ブートストラップ例外の各ケース、カウンタ計上を検証。
- `service/PermissionEngineApiImplTest.java` — 公開API層の単体テスト。認可拒否(negative-authorization)専用テストを含む。
- `event/PermissionChangedEventTest.java`
- `PermissionEngineIntegrationTest.java` — Spring Boot統合テスト(`@SpringBootTest`+`@Transactional`、組込みH2)。COLUMN→TABLE→SCHEMAフォールバックの実DB検証、ブートストラップ→昇格チェック移行、キャッシュ無効化の即時反映、`getGroupDerivedRoleIds`の実DB検証、認可拒否(negative-authorization)専用テストを含む。

### ビルド・設定

- `backend/build.gradle.kts` — `com.github.ben-manes.caffeine:caffeine`(実効権限キャッシュ)、`org.springframework.boot:spring-boot-starter-actuator`(Micrometer `MeterRegistry`のBean自動構成に必要)を追加。

## 計画からの逸脱

1. **`ConfigEngineApi.findColumnConfigById`の追加**(計画外): 承認済みプランには明記がなかったが、functional-spec.md W1(COLUMNスコープから親TABLEのtableConfigIdをconfig-engineから解決する)の実装に構造的に必須であり、data-import-exportが先行して追加した`getTableConfigById`と同種の、config-engine自身の契約への軽微な拡張である。
2. **`assignPermission`/`assignAuxiliaryPermission`への`actorRoleId`引数の追加**(計画外・構造的に必須): C10 YAMLの`assignPermission`パラメータリストは`{roleId, scopeType, scopeRef, level}`のみで、操作者(昇格判定の比較基準)の情報を運ぶパラメータを持たない。しかしBR3.8(権限昇格の防止、project.md Forbidden)は「操作者自身の実効権限との比較」を要求しており、操作者情報なしにはこの核心的責務を実装できない。config-engineの`ConfigChangedEvent`が抱える同種の「actor伝搬」の未解決課題(config-engine側はJavadocに既知の制約として記録するに留めている)を、本契約では引数として明示的に解決した。
3. **`assignAuxiliaryPermission`メソッドの新設**(計画内で言及されていたが契約上は非明示): 計画のStep7は「`assignPermission`の実装(W1/W2/W4のワークフローを反映)」とのみ記載していたが、W4は主権限(level)だけでなく補助権限(createAllowed/deleteAllowed)の割当も扱うと明記する一方、C10 YAMLの`assignPermission`パラメータは`level`のみを持つ。entities.mdがAuxiliaryPermissionを独立したエンティティとして定義していることと整合させるため、主権限用と補助権限用のメソッドを分離した。
4. **`getGroupDerivedRoleIds`の追加**: 計画のStep7に明記済み(functional-spec.md「Domain Design/Contract Designへの追補」3番、計画時点で識別済みの正当化された逸脱)。
5. **`scopeType`/`level`の型付け**: C10 YAMLは`scopeType: "schema|table|column"`・`level: string`という緩やかな文字列表現だが、本実装は`ScopeType`/`PermissionLevel`のJavaのenumとして受け渡す(config-engineが`TableConfig`等の具象型を返す既存の実装方針と整合)。

いずれも本ユニット自身の契約(C10)への軽微な拡張、またはconfig-engineへの既に前例のある軽微な拡張であり、コンシューマー側の呼び出し方に対する破壊的変更ではない(C10は現時点でpermission-engine自身以外に実装を持たない)。

## 既知の未解決事項(計画通り踏襲、修正はスコープ外)

- **R-07**: 複数エントリからなる初回RBACインポートで、1件目のコミット直後にブートストラップ状態が終了し2件目以降が再び昇格チェックにかかりうる問題。設計通り「upsert単位トランザクション」のまま実装した(次回Functional Design見直し時の課題)。
- **R-04**: `invalidateAll()`が`assignPermission`の呼び出し頻度(バルクインポート時にエントリ数分)と組み合わさるとキャッシュが実質機能不全になる可能性。設計通り`invalidateAll()`を実装し、追加対応は行っていない。

## テスト実行結果

- `./gradlew :backend:test --tests "com.mastersmith.permission.*"`: **BUILD SUCCESSFUL**、108件全てpass。
- `./gradlew :backend:test`(プロジェクト全体): **BUILD SUCCESSFUL**、288件全てpass(0 failures, 0 errors)。
- `./gradlew :backend:test :backend:checkstyleMain :backend:checkstyleTest :backend:jacocoTestCoverageVerification`: **BUILD SUCCESSFUL**(checkstyle違反なし、80%行カバレッジフロアを充足)。
- 行カバレッジ(プロジェクト全体): **84.0%**(953/1134行、80%フロアを充足)。
- 権限判定ロジック(`PermissionResolver`)のカバレッジ: 98.0%(50/51行)。テーブル駆動テストによりBR3.4/BR3.5/BR3.6の主要な権限マトリクス組み合わせ(team.md Q6の追加合格条件)を網羅。
- `permission-engine`固有パッケージ別カバレッジ: `service` 100%, `bootstrap` 100%, `cache` 100%, `dto` 100%, `event` 100%, `exception` 100%, `escalation` 89.3%, `resolver` 98.0%, `entity` 61.7%(JPAエンティティのgetter/setter/equals/hashCode等の定型コードが主な未到達行。往復・一意性制約は各エンティティで検証済み)。

## 未修正の既存回帰

なし(`./gradlew :backend:test`全体実行で既存の288件全て(config-engine, data-import-export含む)がpassし、回帰は確認されなかった)。

## Spotless(フォーマッタ)について

本ユニットが新規作成・修正したファイルはすべてSpotless(google-java-format)準拠を確認済み(`spotlessJavaCheck`で違反なし)。プロジェクト全体の`spotlessJavaCheck`は、本Code Generationが一切変更していない既存ファイル(`com.mastersmith.dataio`配下・`ColumnConfig.java`・`ColumnDraftEntry.java`等、data-import-export由来の20ファイル)に起因する**既存の技術的負債**により失敗する状態だが、これは本Code Generation着手前から存在していたフォーマット未適用(baseline drift)であり、本ユニットのスコープ外として現状のまま維持した(`git stash`による着手前ベースラインとの比較で確認済み)。
