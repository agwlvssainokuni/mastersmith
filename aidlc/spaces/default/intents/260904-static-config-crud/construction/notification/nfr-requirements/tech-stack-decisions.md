# Tech Stack Decisions: notification

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準 |
| ビルドツール | Gradle | プロジェクト全体の標準 |
| メールテンプレートエンジン | 自作Mustacheエンジン java-mustache-processor | rules.md BR2.1。未公開の自作ライブラリであり、取り込み方式・バージョン固定方針はドメイン設計以降で扱う(team.md Code Style) |
