# Observability Design: config-management

requirements.md NFR3を踏襲する。

## 監査ログイベントによる可観測性

DbConnection・TableConfig・MenuItemの作成・更新・削除、および設定インポートの成功を、契約#5〜#8に基づきAuditableActionOccurredEventとして発行する(BR8.1)。actionTypeはCONFIG_TABLE_CREATED/UPDATED/DELETED、CONFIG_CONNECTION_CREATED/UPDATED/DELETED、CONFIG_MENU_CREATED/UPDATED/DELETED、CONFIG_IMPORTEDを用いる。設定インポート成功時は個別イベントを発行せず、CONFIG_IMPORTED 1件のみとする。イベント発行失敗は主処理をブロックしない(契約#5〜#8のasync仕様どおり)。

## 機微情報のログ非出力

DbConnection.credentialRefの実値(復号後の業務DB認証情報)は、アプリケーションログ・監査ログのいずれにも出力しない(NFR-DATA.3、security-requirements.md)。AuditableActionOccurredEventのtargetDescriptionは対象エンティティの識別情報(テーブル名・接続名・メニューラベル)のみを含む(BR8.1)。

## メトリクス・分散トレーシング

Spring Boot Actuator + Micrometerの標準メトリクス、およびSpring Boot標準のOpenTelemetry自動計装に委ね、本Unit固有の追加実装は行わない。
