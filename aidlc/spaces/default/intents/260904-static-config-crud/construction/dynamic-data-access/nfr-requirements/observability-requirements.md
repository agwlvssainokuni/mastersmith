# Observability Requirements: dynamic-data-access

requirements.md NFR3(Twelve-Factor App + OpenTelemetry + 構造化ログ)を踏襲する。

## NFR3.1: 監査ログイベントによる可観測性

業務データの作成・更新の成功を、契約#5〜#8(監査ログイベント契約)に基づきAuditableActionOccurredEventとして発行する。actionTypeはDATA_RECORD_CREATED・DATA_RECORD_UPDATEDの2種とする(BR6.1)。本Unitに削除操作は存在しないため、契約#7が語彙として持つDATA_RECORD_DELETEDは発行しない。targetDescriptionは"{physicalTableName}: {recordIdの人間可読な表現}"とする。イベント発行失敗は主処理をブロックしない。

## NFR3.2: サイドチャネル防止と可観測性のバランス

NFR-SIDECHANNEL.1(security-requirements.md)により、recordId検証失敗・該当行なしはいずれも404として区別不能に統一する。この統一はクライアント向け応答の話であり、サーバ側の構造化ログには実際の失敗理由(デコード失敗/キー検証失敗/accessLevel再検証失敗/該当行なし)を区別して記録してよい(運用者による事後調査を妨げないため)。ただしログにはrecordIdの値自体や非表示カラムの実値を含めない。
