# NFR Requirements Questions: packaging

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、packaging固有に新たな数値目標は追加しない。packagingはkind=packagingのUnitであり、functional-designステージを持たない(業務ロジックを持たないビルド・パッケージング関心事のため)。本ステージで作成する成果物はsecurity-requirements.md・tech-stack-decisions.md・traceability.jsonの3件のみとする。requirements.md(NFR4・NFR8)・project.md(Mandated: JDBCドライバ内包、Forbidden: 秘密情報の非コミット)を直接の根拠として具体化する。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

packaging Unitのnfr-requirements成果物を以下の内容で確定します。

**security-requirements.md**: 単一WAR成果物には秘密情報(内部H2の接続情報・パスワードハッシュのソルト/ペッパー・アクセストークンの署名鍵・業務DB認証情報の暗号鍵)を一切含めない(project.md Forbidden)。これらは環境変数、または実行ユーザーのみが読めるパーミッション(600)を設定したローカル設定ファイル経由で外部化する。ビルド成果物(WAR)自体、およびそれをビルドするリポジトリには機微情報を含めない。

**tech-stack-decisions.md**: Gradleにより、React/TSXフロントエンドのビルド成果物(静的アセット)とJava/Spring Bootバックエンドを単一の実行可能WARへパッケージングする(NFR4)。業務DB接続用JDBCドライバ(PostgreSQL/MySQL/MariaDB用)はアプリケーションに内包(バンドル)し、実行環境(自宅サーバ)側への追加インストールを前提としない(project.md Mandated)。

**traceability.json**: upstream_ids = NFR4(OK、単一WARパッケージング)、NFR8(OK、アプリケーション内でのHTTPS終端を行わずリバースプロキシに委ねる。パッケージング自体はTLS証明書を扱わない)。NFR1〜NFR3・NFR5〜NFR7・NFR9はN/A(横断方針または他Unit担当で、パッケージング自体の関心事ではない)。

[Answer]: Looks correct
