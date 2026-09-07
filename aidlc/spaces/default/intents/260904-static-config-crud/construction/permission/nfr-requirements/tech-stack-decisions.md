# Tech Stack Decisions: permission

permission固有の追加の技術選定はなく、プロジェクト全体で確定済みの技術スタックをそのまま用いる。

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準 |
| ビルドツール | Gradle | プロジェクト全体の標準 |
| 永続化先 | 内部H2データストア(`ms_`接頭辞、NFR5) | Role・Group・RoleAssignment・TablePermission・ColumnPermission・GroupMembershipを保持 |
| 呼び出し方式 | 同一JVM内のプロセス内呼び出し | dynamic-data-access(契約#3)・auth(契約#20)・account-management(契約#21)・config-management(契約#22)からの呼び出しをネットワーク境界を越えずに処理する(functional-design-questions.md Q2「Java メソッド呼び出しの意味レベル記述」方針を踏襲) |
