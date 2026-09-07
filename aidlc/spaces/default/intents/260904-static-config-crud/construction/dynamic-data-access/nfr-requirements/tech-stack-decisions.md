# Tech Stack Decisions: dynamic-data-access

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準 |
| ビルドツール | Gradle | プロジェクト全体の標準 |
| 動的SQL組み立て | Spring JDBC `NamedParameterJdbcTemplate` | rules.md BR1.1。テーブル名・カラム名はTableConfig由来の既知識別子のみをSQL文字列に組み込み、値は名前付きプレースホルダで安全にバインドできる。ORMではなくJDBCベースを選ぶのは、実行時にスキーマ形状(テーブル・カラム構成)が動的に決まるという本Unitの性質上、静的エンティティマッピングを前提とするORMと相性が悪いため |
