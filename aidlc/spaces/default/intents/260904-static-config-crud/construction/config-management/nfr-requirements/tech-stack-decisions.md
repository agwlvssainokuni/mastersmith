# Tech Stack Decisions: config-management

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準 |
| ビルドツール | Gradle | プロジェクト全体の標準 |
| キャッシュ機構 | Caffeine(インメモリキャッシュ) | rules.md BR6.1。リクエストのたびにDBへ設定を問い合わせる実装を避けるという確定済み方針(project.md Mandated TC-14)を、追加インフラ(Redis等の外部キャッシュサーバ)なしで実現できる |
| 認証情報の暗号化 | 対称鍵暗号(鍵はapplication.yml経由、外部管理) | DbConnection.credentialRefの保護(entities.md、project.md Forbidden)。具体的なアルゴリズム(AES-GCM等)の選定はコード生成段階で行う |
