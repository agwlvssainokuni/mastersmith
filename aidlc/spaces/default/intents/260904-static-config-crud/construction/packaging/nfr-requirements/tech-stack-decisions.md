# Tech Stack Decisions: packaging

| 項目 | 選定 | 根拠 |
|---|---|---|
| ビルドツール | Gradle | プロジェクト全体の標準 |
| パッケージング形式 | 単一実行可能WAR(React/TSXビルド成果物+Java/Spring Boot) | requirements.md NFR4。React/TSXフロントエンドのビルド成果物(静的アセット)をSpring Bootの静的リソースとして同梱し、単一のデプロイ単位とする |
| JDBCドライバ | PostgreSQL/MySQL/MariaDB用ドライバをアプリケーションに内包(バンドル) | project.md Mandated。実行環境(自宅サーバ)側にドライバの追加インストールを前提としない(TC-01) |
| TLS終端 | アプリケーション内では扱わない(リバースプロキシに委ねる) | requirements.md NFR8。WARパッケージング自体はTLS証明書・鍵を含まない |
