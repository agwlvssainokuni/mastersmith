# Observability Design: dynamic-data-access

requirements.md NFR3を踏襲する。

## 監査ログイベントによる可観測性

業務データの作成・更新の成功を、契約#5〜#8に基づきAuditableActionOccurredEventとして発行する。actionTypeはDATA_RECORD_CREATED・DATA_RECORD_UPDATEDの2種(BR6.1、NFR3.1)。削除操作は本Unitに存在しないためDATA_RECORD_DELETEDは発行しない。イベント発行失敗は主処理をブロックしない。

## サイドチャネル防止と可観測性のバランス

security-design.mdのNFR-SIDECHANNEL.1により、recordId検証失敗・該当行なしはいずれも404として区別不能に統一する(クライアント向け応答)。サーバ側の構造化ログには実際の失敗理由(デコード失敗/キー検証失敗/accessLevel再検証失敗/該当行なし)を区別して記録し、運用者による事後調査を可能にする(NFR3.2)。ログにはrecordIdの値自体や非表示カラムの実値を含めない。

## メトリクス・分散トレーシング

Spring Boot Actuator + Micrometerの標準メトリクス、およびSpring Boot標準のOpenTelemetry自動計装に委ね、本Unit固有の追加実装は行わない。
