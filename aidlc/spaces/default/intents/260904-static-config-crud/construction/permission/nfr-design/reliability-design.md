# Reliability Design: permission

## フェイルセーフな権限判定

内部呼び出しAPI経由の権限確認呼び出し(契約#3、契約#22)自体が例外(内部H2接続断等)を起こした場合、サービスコンポーネントはこの例外を握りつぶさず、そのまま呼び出し元(dynamic-data-access・config-management)へ伝播させる。permission自身の内部例外・応答不能(明示的な不許可判定とは異なるケース)は、明示的な「拒否」判定を返すBR4.1(dynamic-data-access)/BR4.3(config-management)のロジックとは別に扱われる。dynamic-data-access経路(契約#3)については、契約summary.mdの共通規約(HTTPステータス割り当て、500=予期しないエラー)に従い呼び出し元が500として応答することになる。config-management経路(契約#22)については、契約#22自身のFailure behaviorが「該当なし(該当tableIdが0件の場合は空集合を返す。エラー条件ではない)」と明記するのみで、permission自身の内部例外時の挙動を明示的に規定していないため、共通規約(Q8)の一般原則からの推定に留まる(config-management側で契約#22呼び出しを個別にtry-catchし、共通規約どおり500として応答することを期待する設計とするが、契約#22自体の文言による裏付けはない)。いずれの経路でも、誤って「許可」判定に倒れることはない(fail-closed、拒否側に倒す設計)。permission自身がフェイルセーフとして「例外時は許可扱いにする」というフォールバックは行わない。

```java
// 内部呼び出しAPI(契約#3)のイメージ:例外を握りつぶさずそのまま伝播させる
public TablePermissionResult checkTablePermission(String roleId, String tableId, String action) {
    return permissionService.evaluateTablePermission(roleId, tableId, action);
    // 例外はcatchせず呼び出し元(dynamic-data-access)へ伝播する
}
```

## リトライ・サーキットブレーカー

リトライは行わない。permission自身は他Unitへの外部呼び出しを行わないため、サーキットブレーカーは設計しない。

## ヘルスチェック

Spring Boot Actuatorの標準`/actuator/health`エンドポイントに委ねる。

## バックアップ・リカバリ

内部H2データストア全体のバックアップ方針はインフラ横断の関心事であり、本Unit固有の設計は行わない。
