# NFR Design Questions: audit-log

本プロジェクトは自宅サーバ1台・個人利用中心の運用規模であり、nfr-designステージも軽量版方針(確定済みのNFR要件〈nfr-requirements〉とfunctional-designの内容から実装レベルの設計〈使用クラス・パターン・コンポーネント構成〉を導出し、エンタープライズ規模のインフラ設計〈マルチAZ・オートスケーリング・分散キャッシュ層等〉は行わない)で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

audit-log Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: 参照・絞り込み(BR2.1・BR2.2)のクエリに対応するため、AuditLogEntryテーブルにoccurredAt・actorAccountId・actionTypeの各カラムへインデックスを設ける。キャッシュ層は設けない(監査ログは追記主体・参照頻度が低い管理者専用機能のため、キャッシュ導入のコストに見合わない)。

**security-design.md**: 全操作(参照・エクスポート・削除・設定変更)はSpring SecurityによるisAdminクレーム検証をコントローラ層で行う(BR5.1)。エクスポートのformatパラメータ・削除のolderThanDaysパラメータは、コントローラ層でのDTOバリデーション(Bean Validation)により許容値のみを受け付ける(BR3.1・BR4.3)。

**scalability-design.md**: 単一インスタンス構成(NFR2)。水平スケーリング・パーティショニングは行わない。一覧取得はページネーション(page/size)により応答サイズを制御する。

**reliability-design.md**: イベントリスナー(記録受付、ワークフロー1)は例外を自身の処理内で捕捉し、発行元(config-management・dynamic-data-access・auth・account-management)へは伝播させない(BR1.2)。捕捉した例外はSpring ApplicationListener内のtry-catchで処理し、外部への再送・リトライは行わない(記録失敗は許容する設計、BR1.2)。ヘルスチェックはSpring Boot Actuatorの標準エンドポイントに委ねる。

**observability-design.md**: イベント処理失敗時はactionType・occurredAt・例外内容を含む構造化ログ(JSON形式)をERRORレベルで出力する(BR1.2)。分散トレーシングはSpring Boot標準のOpenTelemetry自動計装に委ね、本Unit固有の追加実装は行わない(NFR3)。

**logical-components.md**: audit-logは単一のSpring Bootモジュール内に、イベントリスナー(AuditableActionOccurredEvent購読)・RESTコントローラ(参照/エクスポート/削除/設定変更)・リポジトリ(AuditLogEntry・AuditLogSettingsの内部H2永続化)の3論理コンポーネントで構成する。障害ドメインはaudit-log自身に閉じており(BR1.2により発行元への伝播を遮断)、本Unitの処理失敗が他Unitの主処理に影響することはない(blast radius = audit-log自身のみ)。

**traceability.json**: nfr-requirementsで確定した各NFRx.y項目(NFR1.1・NFR1.2〈性能〉、NFR2.1・NFR2.2〈スケーラビリティ〉、NFR3.1・NFR3.2〈可観測性〉、NFR-RESILIENCE.1〈信頼性〉、NFR-AUTHZ.1・NFR-INTEGRITY.1・NFR-DATA.1・NFR-SEC.1〈セキュリティ〉、NFR5〈`ms_`接頭辞、tech-stack-decisions.md〉)を、上記の設計解へマッピングする。

[Answer]: Looks correct
