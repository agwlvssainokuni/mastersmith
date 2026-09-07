# Reliability Requirements: schema-ingestion

自宅サーバ1台構成(team.md Deployment)のため、SLA/SLO形式の数値目標は設けない。

## NFR-RESILIENCE.1: ステートレス設計による復旧の単純さ

schema-ingestion自身はステートレスであり、走査結果を永続化しない(domain-design/components.md参照)。業務DBへの接続失敗時は例外として呼び出し元へ伝播し、REST境界では500として応答する(BR6.1)。本Unit自体の障害からの復旧はアプリケーションの再起動のみで完結し、専用のリカバリ手順は不要である。
