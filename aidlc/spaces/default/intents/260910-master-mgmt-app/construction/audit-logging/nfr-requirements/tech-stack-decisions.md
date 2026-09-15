# Tech Stack Decisions — audit-logging (U7)

技術スタックはFeasibilityステージ(2026-09-10)・practices-discoveryで確定済み(`team.md`参照)であり、本ユニット固有の新規選定はない。

| 領域 | 選択 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 + Spring Boot | プロジェクト全体で確定済み |
| イベント購読 | Spring `@EventListener`(`ApplicationEventPublisher`経由でのfire-and-forget受信) | config-engine・permission-engine・data-import-exportの既存実装と同一パターン(functional-design rules.md BR7.1) |
| ビルド | Gradle | プロジェクト全体で確定済み |
| データストア | 内部設定DB(組込みDB、例: H2)、業務データ用RDBMSとは別接続 | AuditLogEntryはアプリ自身の設定・記録の一種であり、業務データとは論理的に分離する(team.md Q12b) |
| インデックス | `occurredAt`降順インデックス、`targetType`複合インデックス | NFR1.2(応答時間維持)を満たすための必須設計。具体的なインデックス定義はCode Generationで確定する |
| メトリクス・トレーシング | NFR5.1に従いHTTPエンドポイントのレイテンシ・エラー率をOTEL基盤へエクスポート(具体的なライブラリ選定はCI Pipelineで確定) | プロジェクト全体の可観測性方針に従う。他ユニットと同水準(Q4=B) |

## 決定: 追加ミドルウェアを導入しない

Q2で想定した規模(年間数万〜数十万件)では、専用の監査ログ基盤(外部SIEM、分散ストリーミング基盤等)の追加導入は本MVPスコープでは行わない。既存の内部設定DB(組込みDB)へのインデックス付きテーブルとして記録し、要件(NFR1.1・NFR1.2)を満たせると判断する。将来的に規模が拡大した場合の再検討事項として記録する(scalability-requirements.md NFR3.3参照)。
