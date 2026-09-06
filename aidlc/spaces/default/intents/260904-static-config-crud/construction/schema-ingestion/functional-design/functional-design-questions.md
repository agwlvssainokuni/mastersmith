# Functional Design Questions: schema-ingestion

team.mdのTesting Posture(スキーマ読み込み層の前倒し特性テスト対象: 複合主キー構成・主キーなしテーブルの扱い・ビューの読み取り専用扱い・RDBMS間の型差異)を踏まえ、requirements.md・domain-design/components.mdだけでは確定しきれない2点を確定する。

## Q1. スキーマ/データベースのスコープ

PostgreSQLは1つの接続(データベース)の中に複数の「スキーマ」を持てるが、MySQL/MariaDBは「データベース」自体がスキーマに相当する。JDBC `DatabaseMetaData.getTables()`はスキーマパターンを指定できるが、DB接続先設定(DbConnection、FR2.1)がどのスキーマ/データベースを対象にするかを確定する。

- A. 1つのDbConnectionは常に単一のスキーマ/データベースを対象とする。PostgreSQLの場合は`public`スキーマ固定(接続文字列でスキーマを指定する運用は対象外)、MySQL/MariaDBの場合は接続文字列で指定されたデータベース1つを対象とする。複数スキーマにまたがる取り込みが必要な場合は、DbConnectionを複数登録して個別に取り込む(推奨: MVPの単純化として妥当)
- B. 1つのDbConnectionで複数のスキーマ/データベースを横断して取り込めるようにする(管理者がスキーマ一覧から選択)
- X. Other (please specify)

[Answer]: B。DbConnectionはホスト・ポート・認証情報等の接続先情報のみを保持する(特定のスキーマ/データベースに固定しない)。取り込み実行時に、その接続で利用可能なスキーマ/データベース一覧を取得し、管理者がその中から対象を選んで取り込みを実行する。

## Q2. 複合外部キー(複数カラムから成るFK)の扱い

requirements.md・domain-design/components.mdは外部キーの表現(参照先テーブル・カラム)を単一カラムの組として記述しており、複数カラムから成る複合外部キーへの言及がない。JDBC `DatabaseMetaData.getImportedKeys()`は複合外部キーも複数行(同じFK名、異なるKEY_SEQ)として返しうるため、取り扱いを確定する。

- A. 単一カラムの外部キーのみをサポート対象とする。複合外部キー(2カラム以上から成るFK)が見つかった場合は、その外部キーを無視する(取り込み結果には含めない。テーブル自体の取り込みは妨げない)(推奨: MVPの単純化。config-management/dynamic-data-accessのFK関連機能[FR4.1、FR4.2]がいずれも単一カラムのFKを前提に設計されているため)
- B. 複合外部キーもサポートする(具体的な表現方法を教えてください)
- X. Other (please specify)

[Answer]: B。外部キーを単一カラムの組(column, referencedTable, referencedColumn)ではなく、複数カラムの組(columns[], referencedTable, referencedColumns[])として表現する(単一カラムのFKは要素数1の配列として表現)。contract-summary.md(schema-ingestionのOpenAPI応答形状)も合わせて更新する。

## Requested Changes Feedback

DB接続情報にパラメータを追加。serverTimezone=Asia/Tokyoなど。

## Q3. DB接続パラメータ(serverTimezone等)の指定方法

[Answer]: jdbcUrlに埋め込む。DbConnection(config-management所有)のjdbcUrl文字列自体に、MySQL/MariaDBのserverTimezone等のドライバ固有パラメータをクエリパラメータとして含める(例: `jdbc:mysql://host:3306/db?serverTimezone=Asia/Tokyo`)。DbConnectionへの新しい属性追加は行わない。schema-ingestionはjdbcUrlをそのまま使って接続する。

## Q4. DbConnectionの認証情報(業務DBパスワード)の保持方法

[Answer]: 内部H2データストアに暗号化して保持する(外部ファイル参照ではない)。DbConnection.credentialRefは「外部参照のキー」ではなく「暗号化された認証情報の実体」を直接保持する属性に変更する。暗号化・復号化に用いる鍵はapplication.yml(環境変数経由)で与え、鍵自体は内部H2データストアには保存しない。具体的な暗号アルゴリズムの選定はCode Generation以降に委ねる。domain-design/components.mdのDbConnectionエンティティ定義(config-management所有)をこの方針に沿って更新済み。

## Consolidated Summary Confirmation

- DbConnectionは接続先情報(ホスト・ポート・認証情報)のみを保持し、特定のスキーマ/データベースに固定しない。取り込み実行時に利用可能なスキーマ/データベース一覧を取得し、管理者がその中から対象を選んで取り込みを実行する
- 外部キーはcolumns[]/referencedColumns[]の配列形式で表現し、複合外部キーもサポートする(contract-summary.mdのOpenAPI応答形状も更新)
- serverTimezone等のドライバ固有の接続パラメータは、DbConnection.jdbcUrlのクエリパラメータとして埋め込む(新しい属性は追加しない)
- DbConnectionの認証情報(業務DBパスワード)は内部H2データストアに暗号化して保持する。暗号鍵はapplication.yml(環境変数経由)で与え、DBには保存しない(domain-design/components.mdを更新済み)

- Looks correct
- Request changes

[Answer]: Looks correct
