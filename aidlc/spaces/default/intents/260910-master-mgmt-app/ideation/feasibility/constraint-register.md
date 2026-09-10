# Constraint Register — MasterSmith(マスタ管理アプリ)

## Technical Constraints

| 制約 | 内容 | Source |
|---|---|---|
| 対象RDBMS | PostgreSQL/MySQL/MariaDBの複数対応が必要 | Q3 |
| バックエンド技術 | Java 25 + Spring Boot(最新) + Gradle(最新) | Q5 |
| フロントエンド技術 | TypeScript + Vite + React | Q5 |
| デプロイ形態 | 実行可能WAR(frontendを同梱し、Spring Bootから配信。CORS設定不要) | Q5, [Q8:intent-capture] |
| 既存システム連携 | なし(スタンドアロン、対象RDBMSに直接接続のみ) | Q1 |
| 想定利用規模 | 数十名程度 | Q4 |

## Organizational Constraints

| 制約 | 内容 | Source |
|---|---|---|
| 予算・スケジュール | 特に厳密な制約なし | Q6 |
| 組織的障害 | 特になし(変更凍結期間・競合優先タスクなし) | Q7 |
| 意思決定 | 依頼者本人のみが決定権を持つ | [Q6:intent-capture] |

## Regulatory Constraints

| 制約 | 内容 | Source |
|---|---|---|
| 規制・コンプライアンス要件 | なし(社内利用のみ、規制対象データは扱わない) | Q2 |
| データの機微性 | 個人情報・財務関連設定など機微な情報は含まれない | [Q10:intent-capture] |

## Assumptions & Open Questions

None.
