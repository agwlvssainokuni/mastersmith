# Tech Stack Decisions: schema-ingestion

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準 |
| ビルドツール | Gradle | プロジェクト全体の標準 |
| DB接続方式 | JDBC `DatabaseMetaData` | PostgreSQL/MySQL/MariaDBの3種のテーブル・カラム・型・PK/FK・制約を横断的に取得するため(team.md Testing Posture、requirements.md FR1.1) |
| JDBCドライバ | アプリケーションに内包(バンドル) | project.md Mandated: 実行環境(自宅サーバ)側にドライバの追加インストールを前提としない(NFR4関連) |
