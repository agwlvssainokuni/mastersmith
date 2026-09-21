# Code Generation Notes — data-import-export (U8) — レビュー指摘(iteration 1)への対処

対象レビュー: `aidlc/spaces/default/intents/260910-master-mgmt-app/construction/data-import-export/code-generation/reviews/review-01.md`(NOT-READY、R-01〜R-07)。
本書は、各指摘への対処、選択した方針と`[assumption]`、実行結果、残る懸念を記録する。承認済み計画(`code-generation-plan.md`)と`unit-test-instructions.md`の本文は編集していない。

## 指摘への対処

| ID | 重大度 | 対処 | 主な変更 |
|---|---|---|---|
| R-01 | Major | 対処済み | 主キー列自体の型変換/validationRuleが失敗した行では存在確認を呼ばず、行単位エラー(`typeMismatch`等)のみ返す。存在確認の述語は、型変換済みの主キー値(`Predicate<Object>`)を受け取る(`convertForLookup`を廃止)。形式エラー(`CsvFormatException`)への変換は、CSVパース由来の失敗(`IOException`・`UncheckedIOException`・ヘッダー不備の`IllegalArgumentException`)のみに限定し、DBエラー・行検証由来の例外は変換しない |
| R-02 | Major | 対処済み | 新規`CsvValueFormatter`。editorTypeに応じ、DATEは`yyyy-MM-dd`、DATETIMEは`yyyy-MM-dd'T'HH:mm:ss[.fraction]`、SWITCH/CHECKBOXは`true`/`false`、INTEGER/DECIMALは指数表記なしで出力する。NULLは空文字列。列はインデックスで読み取る |
| R-03 | Major | 対処済み(実DBでの動作検証は未実施、下記「残る懸念」) | エクスポート専用コネクションを`setReadOnly(true)`・`setAutoCommit(false)`にして読み取り、処理後に`rollback`のうえ元の状態へ復元する。fetchSizeはPostgreSQLとその他は500、MySQL/MariaDB(`getDatabaseProductName`で判定)は`Integer.MIN_VALUE`(行単位ストリーミング、接続URLの`useCursorFetch`不要) |
| R-04 | Major | 対処済み | CSVヘッダーに存在する列のみを「CSVに存在する列」とみなす。UPDATEはヘッダー存在列のみをSETし、非存在列は既存値を維持する。INSERTはヘッダー存在列のみをINSERT文へ含める。`required`はヘッダー存在列、およびINSERT時のヘッダー非存在列に適用する |
| R-05 | Minor | 対処済み | `CSVPrinter`をtry-with-resourcesで閉じず`flush()`のみとし、`DataImportExportApi.exportCsv`の契約(クローズしない)に合わせた |
| R-06(1) | Minor | 対処せず(計測・許容を明記) | UPDATE行ごとの存在確認(N+1)は維持。理由と計測値は下記 |
| R-06(2) | Minor | 対処済み | UPDATEの更新件数が0件なら`notFound`の行エラーとして全体ロールバック |
| R-06(3) | Minor | 対処済み | コミット時の`DataIntegrityViolationException`を`constraintViolation`の行エラー(行番号付き、SQL・DBメッセージなし)で返し、`ImportExecutedEvent(committed=false)`を発行する。それ以外の予期しない失敗はイベントを発行したうえで例外を伝播する |
| R-06(4) | Minor | 対処済み(ドライバ報告の引用符方式) | 新規`SqlIdentifiers`。スキーマ・テーブル・列名を`DatabaseMetaData.getIdentifierQuoteString()`の引用符で囲む(引用符自体は二重化してエスケープ) |
| R-07 | Minor | 対処済み(`unit-test-instructions.md`の修正のみ保留、下記) | dataioのテストを27件から94件へ拡充 |

## 選択した方針と[assumption]

人間による後日の確認を要する判断を、設計文書に規定のない点から順に列挙する。

- **[assumption] R-04: ヘッダーに存在しない列は「更新対象外」**(レビューの選択肢(a)を採用)。`functional-spec.md`・`rules.md`に規定がないため、エクスポート→編集→再インポートの往復を成立させる最小の解として採った。ヘッダーの列不足をファイル形式エラーとする案(b)は、エクスポートがhidden列・権限のない列を意図的に省く(BR8.2)ことと両立しないため採用しない。機能設計(`functional-spec.md`・`rules.md` BR8.3/BR8.5)への追補が必要であり、追補はオーケストレーター側の判断に委ねる。
- **[assumption] R-04: 空セルの扱い**。ヘッダーに存在する列の空セルは、`required`でなければNULL(既存の挙動を明文化)、`required`ならエラー。値の数がヘッダーより少ない行の欠けた列は、空セルとして扱う(ヘッダー存在列は「CSVに存在する列」であるため)。
- **[assumption] R-04: INSERT時のヘッダー非存在列**。`required`ならBR8.5に従い`required`エラー、それ以外はINSERT文の列リストから外す(対象RDBMSの既定値またはNULL)。
- **[assumption] BR8.1の解釈: 空ファイル・ヘッダー行なし・対象テーブルの列を1つも含まないヘッダーは`CsvFormatException`**(1行目はカラム名のヘッダー行、という規定に反するため。列を含まないヘッダーで発行されうる列なしのINSERTを防ぐ意味もある)。ヘッダーに未知の列名が混在する場合は従来どおり無視する(形式エラーにはしない)。
- **[assumption] R-02: 日時はISO-8601(タイムゾーンなし、`T`区切り)に統一**。インポートが従来から受け付ける形式(`LocalDate.parse`・`LocalDateTime.parse`)にエクスポートを合わせた。インポート側を緩める(空白区切りの許容など)案は、BR8.1の「双方で同一形式」に反するため採らない。タイムゾーン付きの列型(`timestamptz`等)はオフセットを落として出力する(インポートがオフセットを受け付けないため)。
- **[assumption] R-03: MySQL/MariaDBは`fetchSize=Integer.MIN_VALUE`**。レビューは`useCursorFetch=true`の要件化または「ストリーミング用のfetchSize指定」を挙げており、後者を採った(接続URLの設定に依存しないため)。ドライバ製品名の判定(`MySQL`・`MariaDB`を含む)はMySQL Connector/JとMariaDB Connector/Jで検証していない。
- **[assumption] R-03: `setReadOnly(true)`の併用**。レビューの「読み取り専用トランザクション」に従った。
- **[assumption] R-06(3): コミット時のDB制約違反の`RowError.field`は番兵値`*`、`message`は`constraintViolation`**。`RowError`はフィールド名を必須とするが、制約違反は特定の列に帰属しないため。C13契約の`ImportResult.errors`にこの番兵値が現れる。frontend-ui(`CsvImportErrorModal`)がフィールド名を列名として解決する場合は、`*`の扱いを確認されたい。コミット後に遅延評価される制約(遅延可能な外部キー等)の違反は、行が特定できないため行エラー化せず、`committed=false`のイベントを発行して例外を伝播する。
- **[assumption] R-06(4): 引用符はドライバ報告方式**。config-engineが保持する名称はスキーマ探索時にRDBMSのメタデータから取得した実名であるため、引用符で囲んでも大文字小文字は実名と一致する、という前提に依る。設定を手書きで編集し、実名と大文字小文字が異なる名称を保持している場合は、引用符付きの参照が失敗しうる(以前は多くのRDBMSが大文字小文字を吸収していた)。スキーマ名が空の場合はテーブル名のみで参照する。

## R-06(1): N+1の存在確認を維持する理由と計測

UPDATE行ごとに`SELECT 1 ... WHERE pk = ?`を1回発行する構造は維持した。IN句による一括確認は、Java側で行値の一致を判定する必要があり、対象RDBMSの照合順序(例: MySQLの大文字小文字を区別しない比較)や数値型の表現差で、DBの判定(`UPDATE ... WHERE pk = ?`)と食い違い、誤った`notFound`を生みうるためである。存在確認の判定規則をDBに一致させることを優先した。

計測(H2インメモリ、使い捨てのテスト、確認後に削除): 10万行のINSERTインポート 656ms、10万行のエクスポート 137ms、10万行(全行UPDATE、行ごとの存在確認を含む)のインポート 830ms。ネットワーク越しのRDBMSでは1回の往復が支配的になるため、この値は下限である。NFR1.2(10万行を2分)に対し、往復1回0.3msなら存在確認だけで約30秒と見積もるが、これは見積もりであり、PostgreSQL/MySQL/MariaDBでの実測は未実施。実測で目標を超える場合は、DB側で結合する一括確認(一時テーブルとの結合など)を検討する。

## 実行結果

- `./gradlew :backend:test --tests "com.mastersmith.dataio.*" --tests "com.mastersmith.config.entity.ColumnConfigJpaTest" --tests "com.mastersmith.config.store.ConfigModelStoreTest"`: 成功(dataio配下は6クラス・94件、失敗0)。
  - `CsvRowValidatorTest` 23、`CsvValueFormatterTest` 22、`CsvColumnDefinitionResolverTest` 7、`CsvImportServiceTest` 23、`CsvExportServiceTest` 17、`CsvRoundTripTest` 2(旧27件)。
- `./gradlew :backend:test`(全体): 1389件、失敗0、エラー0、スキップ0。
- `:backend:spotlessCheck`・`:backend:checkstyleMain`・`:backend:checkstyleTest`・`:backend:jacocoTestCoverageVerification`: すべて成功。
- カバレッジ(全体、jacoco): 行93.1%(80%フロア充足)、命令91.9%、分岐81.8%。dataio配下の行: `service` 255/277、`csv` 133/152。

追加した回帰テストの対応:

- R-01: 数値でない/桁あふれの主キーで、全行のエラーが収集され例外にならないこと(`CsvImportServiceTest`、`CsvRowValidatorTest`)。存在確認のDBエラーが`CsvFormatException`にならないこと。
- R-02: 型別のテーブル駆動(`CsvValueFormatterTest`)。DATE・DATETIME(小数秒)・真偽値・NULL・小数を持つテーブルのエクスポート→そのままインポートで、全データが不変であること(`CsvRoundTripTest`)。
- R-03: PostgreSQL/MySQL/MariaDB/H2/Oracleの製品名ごとに、`setReadOnly(true)`→`setAutoCommit(false)`→`setFetchSize`→`rollback`→復元の順序をJDBCモックで検証(`CsvExportServiceTest`)。クエリ失敗時にも復元されること。
- R-04: ヘッダーにない列(hidden・required含む)が更新で維持されること、往復でhidden列が変化しないこと、空セル・短い行・INSERT時のrequiredの扱い。
- R-05: クローズ状態を記録する出力ストリームで、クローズされずflushされること。
- R-06: 0件更新(検証後に行が削除される競合を、検証器の差し替えで再現)で全体ロールバック+`notFound`+イベント、一意制約違反で行エラー+イベント、予期しないSQLエラーでイベント発行+例外伝播、予約語の識別子。
- R-07: `CsvFormatException`(空ファイル・ヘッダー不正・列なしヘッダー・データ行の引用符不正)、BOMなし、ヘッダーのみ、対象テーブル不在、行番号・フィールド・メッセージの内容、`filter`/`sort`(未知の列名の無視)、hidden・権限のない列の実出力、`CsvExportException`(SQL失敗・書き込み失敗)。

## 残る懸念

1. **R-03の実RDBMSでの検証は未実施**。ストリーミング設定はJDBCモックの呼び出し順序で検証したが、PostgreSQL(自動コミット無効+fetchSizeでカーソル使用)とMySQL/MariaDB(`Integer.MIN_VALUE`)で、10万行のエクスポート時にアプリケーションのメモリが増えないことは、実DBでは未確認である。レビューが求めるTestcontainers等の検証手順は用意できていない(依存追加とコンテナ実行環境が必要で、本作業の範囲外とした)。BR8.10の実DBでの充足は、リスクとして明示する。Build and Test以降で、PostgreSQL/MariaDBの実コンテナに対し、10万行のエクスポートを小さいヒープ(例: `-Xmx128m`)で実行する手順の実施と承認を要する。
2. **ドライバ別の前提**(`code-summary.md`・Build and Testの手順に載せてほしい): (a)エクスポートは読み取り専用・自動コミット無効のコネクションを使い、接続URLに`useCursorFetch`は不要。(b)MySQL/MariaDBの接続URLで`useAffectedRows=true`を指定しない(値が変わらない更新が0件となり、0件更新の検出が誤検知する)。(c)MySQL/MariaDBはストリーミング中、同一コネクションで他のSQLを発行できない(本実装は発行しない)。`application.yml`の`business-datasource`にも同内容をコメントで記載した。
3. **`unit-test-instructions.md`は編集していない**(承認済みフィンガープリントの対象のため)。記述と実態の不一致が残る: 記述は`@SpringBootTest`・config-engineの内部設定DBの再利用・テスト後のロールバックだが、実装は`CsvImportServiceTest`が`@SpringJUnitConfig`+テストごとに独立したH2(`DROP TABLE`で初期化)、`CsvExportServiceTest`/`CsvRoundTripTest`はSpring起動なしのMockito+独立H2である。修正が必要な場合は、オーケストレーターが承認手続きに沿って改訂する。想定テスト数(25〜35件)も、現在は94件である。
4. 複数プロファイル横断E2Eテスト(`team.md`の必須種別(b))は、計画どおり本Boltの対象外(list-engine・record-edit-engine・frontend-ui未実装)。
5. 存在確認のN+1(R-06(1))は維持しており、実DBでの性能実測は未実施(上記)。
6. ヘッダーの重複列名は、従来どおり(最後の列が優先)。形式エラーにはしていない。仕様に規定がないため新設しなかった。
7. 列なしINSERT(CSVヘッダーの既知列が主キーのみで、主キーが空の行)は`INSERT INTO t DEFAULT VALUES`を発行する。MySQLはこの構文を受け付けないため、その場合はSQLエラーとして例外が伝播する(極めて限定的な入力のため、方言別の分岐は入れていない)。

## config-engine側への変更

なし(`ColumnConfig`・`ColumnDraftEntry`・`ConfigModelStore`は変更していない)。
