# Tech Stack Decisions: account-management

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準 |
| ビルドツール | Gradle | プロジェクト全体の標準 |

本Unit固有の技術選定はない。アカウント作成・一覧・編集・無効化はいずれも契約#4(auth)・契約#21(permission)への委譲呼び出しの合成であり、独自の永続化層・外部ライブラリを必要としない。
