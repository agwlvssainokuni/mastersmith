# NFR Design Questions: config-management

軽量版方針で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

config-management Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: DbConnection・TableConfig・MenuItemの読み取りはCaffeineキャッシュ経由で行う(NFR1.2、BR6.1)。TTL未設定時は無期限、明示的クリア(POST /api/admin/config/cache/clear)または個別エンティティ変更時の無効化(BR6.2)のみが更新契機。

**security-design.md**: 全操作はisAdminクレーム必須、GET /api/menuのみ例外でX-Active-Role+契約#22のcanList権限フィルタ(NFR-AUTHZ.1・NFR-AUTHZ.2)。DbConnection.credentialRefは暗号化して保持し、暗号鍵は環境変数経由で外部化(NFR-DATA.1)。エクスポートは暗号化されたまま出力(NFR-DATA.2)。

**scalability-design.md**: 単一インスタンス構成(NFR2.1)。Caffeineキャッシュは想定運用規模(数十〜数百テーブル)ではメモリ使用量が問題にならない(NFR2.2)。

**reliability-design.md**: 設定インポートは全件検証後に不整合があれば全体拒否(BR5.2、部分適用なし、NFR-CONSISTENCY.1)。キャッシュとDBの整合性は書き込み後の明示的無効化で担保(NFR-CONSISTENCY.2)。schema-ingestion呼び出し失敗時は5xxとして伝播(NFR-FAILSAFE.1)。

**observability-design.md**: DbConnection・TableConfig・MenuItemのCRUD操作および設定インポート成功を監査ログイベントとして記録(NFR3.1、CONFIG_TABLE_*/CONFIG_CONNECTION_*/CONFIG_MENU_*/CONFIG_IMPORTED)。credentialRefの実値はログに出力しない(NFR3.2)。

**logical-components.md**: config-managementは以下3つの論理コンポーネントで構成する。RESTコントローラ(管理系エンドポイントの受付、isAdmin認可検証、GET /api/menuの例外処理)、サービス(TableConfig一括作成〈スキーマ取り込み、BR2.1〜BR2.8、外部キー遡及解決〉、設定インポート検証〈BR5.2〉、Caffeineキャッシュ管理〈BR6.1〜BR6.2〉、メニューのcanList権限フィルタリング〈BR4.3、permission Unitへの契約#22プロセス内呼び出し〉)、リポジトリ(DbConnection・TableConfig・MenuItemの内部H2永続化)。

**traceability.json**: nfr-requirementsで確定した各NFRx.y項目を、上記の設計解へマッピングする。

[Answer]: Looks correct
