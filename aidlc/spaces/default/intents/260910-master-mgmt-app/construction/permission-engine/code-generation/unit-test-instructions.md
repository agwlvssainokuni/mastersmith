# Unit Test Instructions — permission-engine (U3)

## テストフレームワーク・構成

- JUnit 5 + Spring Boot Test(既存プロジェクトの`backend`モジュールと共通)
- 組込みH2による統合テスト(config-engine等と同じ内部設定DB用インメモリDB)
- Mockito(Repository層のモック、Caffeineキャッシュの動作検証)

## このユニットのテスト実行コマンド

```
./gradlew :backend:test --tests "com.mastersmith.permission.*"
```

## 期待されるカバレッジ目標

- 80%行カバレッジ(team.md既定、mvpスコープ)
- **権限判定ロジック(`PermissionResolver`、BR3.4/BR3.5/BR3.6/BR3.8)は追加合格条件として、主要な権限マトリクスの組み合わせを網羅するテーブル駆動テストを必須とする**(team.md Q6=A)。

## テスト規模(Comprehensive戦略)

各コンポーネント10〜15件程度。対象コンポーネント: `PermissionResolver`, `PermissionEngineApiImpl`, Repository層各種, `PermissionCacheConfig`, `BootstrapStateChecker`, `PermissionEscalationChecker`。

## 必須テスト種別(team.md Q8由来)

- (a) 安全失敗/バリデーションテスト: 該当する場合(不正なscopeRef文字列長超過等)
- (b) 複数プロファイル横断E2Eテストは本ユニット単体では対象外(list-engine/record-edit-engine等、実際に業務プロファイルを切り替えるユニットのE2Eテストで検証)
- (c) **認可拒否(negative-authorization)専用テスト必須**: `resolveEffectivePermission`がNONEを返すケース、`assignPermission`が`PermissionEscalationException`を投げるケースを明示的に検証する専用テストを作成する

## モック・スタブ方針

- Repository層はSpring Data JPAの実DB(組込みH2)を用いた統合テストを主とし、`PermissionResolver`単体テストではRepositoryをモック化してスコープ階層探索ロジックのみを検証する
- キャッシュ層のテストはCaffeineの実インスタンスを用い、TTL・invalidateAll()の実際の挙動を検証する(モック化しない)

## テストデータ管理

- テストごとに独立したRole/PrimaryPermission/AuxiliaryPermissionレコードを`@BeforeEach`で構築し、テスト間の状態共有を避ける
- 権限マトリクステーブル駆動テストは`@ParameterizedTest`+`@MethodSource`でケースを列挙する
