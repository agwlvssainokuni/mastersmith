# NFR Requirements Questions: schema-ingestion

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、schema-ingestion固有に新たな数値目標は追加しない。functional-design(rules.md BR1.1〜BR6.1)で既に確定済みの内容(JDBC DatabaseMetaDataによる走査、複合主キー・主キーなしテーブル・ビューの扱い、RDBMS間の型差異の正規化、認証情報の暗号化保持)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

schema-ingestion Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし)を踏襲。スキーマ走査(JDBC DatabaseMetaData呼び出し)はテーブル数に比例して時間がかかりうるが、個人利用中心の想定業務規模(数十〜数百テーブル程度)では実用上問題ない範囲とする。著しい遅延が確認された場合は取り込み対象テーブルの絞り込み(プレビュー画面での選択)で対応する。

**security-requirements.md**: 業務DBへの接続に用いる認証情報(DbConnection.credentialRef)は、config-management側で暗号化して内部H2に保持され、本Unitは復号済みの値を呼び出し元(config-management)経由で受け取るのみで、自身では永続化しない(functional-design-questions.md Q4)。接続失敗・認証失敗は例外として呼び出し元へ伝播し、詳細なエラー内容(認証情報そのもの等)を含まない(rules.md BR6.1)。暗号鍵・認証情報をリポジトリにコミットしない旨(project.md Forbidden)を本Unitの前提として明記する。

**scalability-requirements.md**: NFR2(1インスタンス=1業務)を踏襲。複数のDbConnectionを扱う場合でも、走査は都度1接続に対して実行される設計であり、追加のスケーリング機構は不要。

**reliability-requirements.md**: 自宅サーバ1台構成のためSLA/SLO数値目標は設けない。業務DBへの接続失敗時は例外として呼び出し元へ伝播し、REST境界では500として応答する(BR6.1)。schema-ingestion自身はステートレスであり、走査結果を永続化しないため、本Unit自体の障害からの復旧は再起動のみで完結する。

**observability-requirements.md**: NFR3(Twelve-Factor App準拠、OpenTelemetry対応、構造化ログ)を踏襲。接続テスト・プレビュー実行の失敗は構造化ログに記録することが望ましいが、認証情報等の機微情報はログに出力しない。

**tech-stack-decisions.md**: プロジェクト全体で確定済みの技術スタック(Java 25/Spring Boot/Gradle)に加え、PostgreSQL/MySQL/MariaDB用のJDBCドライバをアプリケーションに内包する方針(project.md Mandated、NFR4関連)を明記する。

**traceability.json**: upstream_ids = NFR1, NFR2, NFR3, NFR4(JDBCドライバのアプリケーション内包)。NFR5〜NFR9は本Unitの担当範囲外(N/A)として明記する。

[Answer]: Looks correct
