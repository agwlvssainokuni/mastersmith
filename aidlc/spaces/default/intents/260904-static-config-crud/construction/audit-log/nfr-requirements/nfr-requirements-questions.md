# NFR Requirements Questions: audit-log

requirements.mdのNFR1〜NFR9は、既に本プロジェクト(自宅サーバ1台・個人利用中心、`project.md`/`team.md`で確認済みの軽量運用方針)に即した内容で確定しており、audit-log固有に新たな数値目標や論点を追加する必要はない。functional-design(rules.md BR1.1〜BR6.1)で既に確定済みの内容(保持日数設定、CSV/JSON形式でのエクスポート、isAdminゲーティング、記録失敗の非伝播)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

audit-log Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし、実用上問題のない応答速度)を踏襲。監査ログの参照・エクスポートはページネーション(BR2.1)前提であり、大量データの一括取得を行わない設計のため、追加の性能対策は不要とする。

**security-requirements.md**: 全操作がisAdminクレーム必須(BR5.1)。記録は作成後不変(BR6.1)であり、改ざん・削除は保持期間超過分の一括削除(BR4.1)のみに限定される点を、監査ログとしての完全性(integrity)要件として明記する。

**scalability-requirements.md**: NFR2(1インスタンス=1業務、マルチテナント運用なし)を踏襲。データ量の増加は保持期間ポリシー(BR4.1、既定365日)による削除で制御し、インフラ的なスケーリングは対象外とする。

**reliability-requirements.md**: 自宅サーバ1台構成(team.md)のため、可用性についてSLA/SLO数値目標は設けない。監査記録処理の失敗は発行元Unitの主処理をブロックしない設計(BR1.2)を、信頼性要件の中核として明記する。

**observability-requirements.md**: NFR3(Twelve-Factor App準拠、OpenTelemetry対応、構造化ログ)を踏襲。記録失敗時はERRORレベルの構造化ログを出力する(BR1.2)。

**tech-stack-decisions.md**: プロジェクト全体で確定済みの技術スタック(Java 25/Spring Boot/Gradle、内部H2データストア、Spring `ApplicationEventPublisher`/`@EventListener`によるイベント連携)をaudit-log固有の文脈で記載する。本Unit固有の追加の技術選定はない。

**traceability.json**: upstream_ids = NFR1, NFR2, NFR3, NFR5(内部H2データストアの`ms_`接頭辞命名規約、AuditLogEntry/AuditLogSettingsテーブルに適用)。NFR4・NFR6〜NFR9は本Unitの担当範囲外(N/A)として明記する。

[Answer]: Looks correct
