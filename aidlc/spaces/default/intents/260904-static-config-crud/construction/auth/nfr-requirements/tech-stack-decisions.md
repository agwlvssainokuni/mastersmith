# Tech Stack Decisions: auth

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準 |
| ビルドツール | Gradle | プロジェクト全体の標準 |
| パスワードハッシュ化 | Argon2PasswordEncoder(Spring Security、デフォルトパラメータ) | NFR7、rules.md BR5.1。可逆暗号化ではなく不可逆ハッシュ化を要求されており、Spring Security標準実装が最も枯れた選択肢 |
| アクセストークン形式 | JWT(HS256署名) | rules.md BR2.2。ステートレスな検証を可能にし、他Unit(config-management等)での署名検証を単純化する |
| リフレッシュトークン保存 | ハッシュ値のみ永続化(平文非保存) | rules.md BR2.3。トークン漏洩時の被害範囲を限定する |
