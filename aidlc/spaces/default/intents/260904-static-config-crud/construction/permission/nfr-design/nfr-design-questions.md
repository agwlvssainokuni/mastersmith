# NFR Design Questions: permission

軽量版方針(確定済みのNFR要件とfunctional-designの内容から実装レベルの設計を導出し、エンタープライズ規模のインフラ設計は行わない)で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

permission Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: 権限確認(契約#3、テーブル単位・カラム単位)は高頻度に呼ばれるため、TablePermission・ColumnPermissionのroleId+tableId(+columnName)複合インデックスを設ける。キャッシュ層は設けない(軽量版方針、権限変更の即時反映を優先しキャッシュ無効化の複雑さを避ける)。

**security-design.md**: テーブル単位権限はセキュアバイデフォルト(TablePermission未設定時は全操作拒否、NFR-AUTHZ.1)、カラム単位権限はeditableをデフォルト許可(NFR-AUTHZ.2、既存テーブルへの新規カラム追加時に誤って全カラムが非表示にならないための設計判断)とする。管理系エンドポイント(ロール・グループのCRUD、割当設定)はisAdminクレーム必須とし、契約#3・#20・#21・#22経由の内部呼び出しにはisAdmin検証を適用しない(呼び出し元Unit自身が別途の認可コンテキストで動作するプロセス内呼び出しであるため、NFR-AUTHZ.3)。

**scalability-design.md**: 単一インスタンス構成(NFR2)。TablePermission・ColumnPermissionのレコード数はロール数×テーブル数(×カラム数)に比例するが、想定運用規模では問題にならない。

**reliability-design.md**: 契約#3(dynamic-data-access)・契約#22(config-management)からの権限確認呼び出し自体が例外(内部H2接続断等)を起こした場合、フェイルセーフ(fail-closed、拒否側)として扱う(NFR-FAILSAFE.1)。呼び出し元Unit(dynamic-data-access・config-management)は、この例外を5xxとして自身の呼び出し元へ伝播させる(リトライは行わない)。

**observability-design.md**: 権限確認呼び出し(契約#3・#20・#21・#22)自体は監査ログ対象外とする(高頻度なプロセス内呼び出しであり、BR8.1相当の監査イベントは定義されていない)。ロール・グループ・割当・権限設定のCRUD操作(管理系エンドポイント経由)については、他Unitと同様に監査ログイベント発行の対象とする(functional-design側で確定済みの範囲を踏襲)。

**logical-components.md**: permissionは以下4つの論理コンポーネントで構成する。RESTコントローラ(管理系エンドポインドの受付、isAdmin認可検証)、内部呼び出しAPI(契約#3・#20・#21・#22によるプロセス内呼び出しの受付窓口)、サービス(ロール割当の全置換〈BR3.2〉、冪等処理〈BR1.2・BR3.3〉、有効ロール集合の計算〈直接割当+グループ経由の和集合、BR4.1〉、テーブル/カラム権限判定〈BR5.1・BR5.2・BR5.3〉、カスケード削除〈BR1.4・BR1.5〉)、リポジトリ(Role/Group/GroupMembership/RoleAssignment/TablePermission/ColumnPermissionの内部H2永続化)。障害ドメインはpermission自身に閉じ、契約呼び出し元(dynamic-data-access・auth・account-management・config-management)への影響はフェイルセーフ設計(上記reliability-design.md)により限定される。

**traceability.json**: nfr-requirementsで確定した各NFRx.y項目を、上記の設計解へマッピングする。

[Answer]: Looks correct
