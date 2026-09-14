# Tech Stack Decisions — permission-engine (U3)

技術スタックはFeasibilityステージ(2026-09-10)・practices-discoveryで確定済み(`team.md`参照)であり、本ユニット固有の新規選定はない。

| 領域 | 選択 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 + Spring Boot | プロジェクト全体で確定済み |
| ビルド | Gradle | プロジェクト全体で確定済み |
| データストア | 内部設定DB(組込みDB、例: H2)、業務データ用RDBMSとは別接続 | Role/PrimaryPermission/AuxiliaryPermission/Group関連はアプリ設定の一種であり、業務データとは論理的に分離する(team.md Q12b) |
| キャッシュ | プロセス内キャッシュ(具体的な実装、例: Caffeine等はCode Generationで選定) | Q2(短いTTLキャッシュ)を満たすための実装手段。追加のミドルウェア(Redis等)は本MVP規模(数十ロール、数千行程度)では不要と判断する |
| メトリクス・トレーシング | NFR5に従いOTEL基盤へエクスポート(具体的なライブラリ選定はNFR設計/CI Pipelineで確定) | プロジェクト全体の可観測性方針に従う |

## 決定: 追加ミドルウェアを導入しない

Q3(データ規模: ロール数十件、設定数百〜数千行)を踏まえ、分散キャッシュ(Redis等)や専用の権限判定エンジン(OPA等)の追加導入は本MVPスコープでは行わない。プロセス内(JVMヒープ内)の軽量キャッシュで要件(NFR1.1: 50ms以内、NFR3.4: 短いTTL)を満たせると判断する。将来的に規模が拡大した場合の再検討事項として記録する。
