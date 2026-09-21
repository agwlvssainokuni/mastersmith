<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Code Generation Notes — schema-introspector (U2)

アーキテクチャレビュー(adversarial、iteration 1、NOT-READY)の指摘R-01〜R-07への対処記録。対象のコードは`backend/src/main/java/com/mastersmith/schema/`と`backend/src/test/java/com/mastersmith/schema/`。

## 指摘ごとの対処

| ID | 重要度 | 対処 | 状態 |
|---|---|---|---|
| R-01 | Major | `RdbmsMetadataReader`で、`getColumns`・`getTables`のスキーマ名・テーブル名(LIKEパターン)を、`DatabaseMetaData.getSearchStringEscape()`でエスケープして渡すようにした(`%`・`_`・エスケープ文字自身)。加えて、結果の行の`TABLE_NAME`(PostgreSQL方式では`TABLE_SCHEM`も)が指定値と一致する行だけを採用する(ドライバがエスケープを解釈しない場合の備え)。`getPrimaryKeys`はJDBCの仕様上パターンではないためエスケープしない。回帰テストとして、`ORDER_ITEMS`と`ORDERXITEMS`(`_`が互いにパターン一致)の混入がないこと(主キーの取り違えを含む)、全テーブル読み取り時に各テーブルのカラムが混ざらないこと、`schemaName`・`tableNames`の`%`・`_`がリテラルとして扱われること、ドライバが別テーブルの行を返しても照合で除外されることを追加した。 | 修正済み |
| R-02 | Major | `SchemaIntrospectionService`が`writeTableConfigDraft`の`ConfigValidationException`を捕捉し、新設の`SchemaDraftValidationException`(`SchemaIntrospectionException`のサブクラス、フィールド単位のエラーを保持)へ変換する。コントローラは422で、`errors`(`field`・`message`(ルール種別))を載せて返す。それ以外の実行時例外(DBの一意制約違反など)は変換せず伝播し(500)、`schema_introspection_failures_total`には、読み取り失敗・config-engineの拒否・書込み時の予期しない例外のすべてを計上する(以前のJavadocの記述と実装を一致させた)。サービス層・コントローラ層に、この経路のテストを追加した。 | 修正済み(下記[assumption]参照) |
| R-03 | Major | `OperatorContext.current()`が空(操作者を解決できない)の場合は、`canAccessScreen`を呼ばず、コントローラ自身が401(`SchemaIntrospectionUnauthorizedException`、`ProblemDetail`)を返す。操作者は解決済みでアクティブロールが未選択(null)の場合は、そのままC10へ渡し、C10の判定(fail closedで403)に従う。user-managementの`UserAuthorizer`(`requireUserAdmin`)と同じ流儀。テストは、未解決(401、C10が`true`を返すブートストラップ状態を想定しても、`verifyNoInteractions(permissionEngineApi, service)`)・ロール未選択(403、C10の戻り値を明示、`canAccessScreen(null, ...)`の呼び出しを検証)・権限なし(403)・許可(200)を、モックの既定値に頼らず検証する。実物のセキュリティ設定越しの401は、共有の認証テスト`AuthSecurityConfigTest`の「`/api/**`は既定で認証必須」のケースに、`POST /api/config/schema-introspection`を追加して検証する(Bearerなしで401、`auth.token.invalid`、`WWW-Authenticate: Bearer`)。 | 修正済み(結合テストの所在は下記参照) |
| R-04 | Minor | (1)読み取り全体の締切(接続確立から25秒)を、テーブルごとの読み取りの前に検査し、超過時は`SchemaIntrospectionException`でfail fastする。ソケット単位の`setNetworkTimeout`は維持する。1回のメタデータ呼び出しの途中では締切を検査できないため、最悪の所要時間は「締切+ソケット1回あたりの待ち」であり、`RdbmsMetadataReader`のJavadocに明記した。(2)接続取得のタイムアウト後に遅れて得られた接続は、`CompletableFuture`の原子的な完了を用いて、取得側のスレッドが閉じる(打ち切りと同時に得られていた場合は、待つ側が閉じる)。(3)遅延する`DataSource`で、接続取得のタイムアウトと、遅れて得られた接続が閉じられることを検証するテスト、読み取り締切の超過のテスト、接続失敗のテストを追加した。タイムアウトを差し替えられる、パッケージプライベートのコンストラクタを追加した(本番のコンストラクタは既定値の5秒・25秒のまま、`@Autowired`を明示)。 | 修正済み |
| R-05 | Minor | (1)公開の`readSchema`の成功経路を、実H2の接続を、`getDatabaseProductName`だけPostgreSQLと名乗らせるプロキシ越しに、実データで検証するテストを追加した。(2)MySQL/MariaDBの分岐(`schemaName`を、パターンではない`catalog`引数に渡し、`schema`パターンには`null`を渡すこと、`getPrimaryKeys`・`getColumns`の引数、テーブル名の`_`のエスケープ)を、`DatabaseMetaData`のモックで、両方言について検証するテストを追加した。実DB(Testcontainers)による検証は、本Boltでは追加していない(CIにコンテナの実行環境が定義されていないため。残る懸念を参照)。 | 修正済み(実DBによる検証を除く) |
| R-06 | Minor | `source-manifest.json`を、実際のコードに合わせて更新した(存在しない`ActiveRoleResolver`・`HeaderActiveRoleResolver`とそのテストの列挙を除き、今回の追加・変更分を反映)。`code-summary.md`・`unit-test-instructions.md`・`code-generation-plan.md`・`traceability.json`は、指示により編集していない(承認済みの内容、またはオーケストレーターの更新対象)。 | `source-manifest.json`は修正済み。ほかは、オーケストレーターの更新待ち |
| R-07 | Minor | `LogSanitizer`(`com.mastersmith.schema.util`)を新設し、クライアントが指定した`schemaName`・`tableNames`を、ログ(`LOG.info`・`LOG.warn`)と、それらを埋め込む例外メッセージ・`SchemaIntrospectionForbiddenException`のメッセージに出す前に、制御文字(ISO制御文字・行区切り・段落区切り・書式制御文字)を、`\u000a`形式の可視のエスケープに置き換える。単体テストと、実際のログ出力(`OutputCaptureExtension`)で改行による行の偽装ができないことを検証するコントローラのテストを追加した。 | 修正済み |

## [assumption] 判断が必要だった点(人間の確認待ち)

- [assumption] **R-02: 未対応の型の扱い(422とフィールド単位のエラー)**: レビューの選択肢のうち、「未対応の型のカラムのみをスキップして報告する」は、設計文書(rules.md BR2.9、functional-spec.md、C8)にない挙動の新設になるため採らず、BR2.9のfail-fast(422)に沿う「全体を拒否し、フィールド単位のエラーを返す」を選んだ。`errors`の形式(`field`・`message`)は、config-engineの`FieldError`のJavadocが定める「RFC 9457のerrors配列(field, message)へマッピングする」という既存の取り決めと、user-managementの422の形式に合わせた。`message`には、`FieldError.ruleType`(例: `unsupportedRdbmsType`)をそのまま入れる。
- [assumption] **R-02: どのテーブル・カラムの型が未対応かは、応答に含められない**: `writeTableConfigDraft`(C9)が送出する`ConfigValidationException`の`FieldError`は、`rawTypeName:POSTGRESQL`のようなフィールド名とルール種別だけを持ち、テーブル名・カラム名・型名を含まない(config-engine側の実装、`RdbmsTypeNormalizer`)。schema-introspectorでこれを補うには、config-engineの型の正規化表を、こちらで複製するか、C9契約(`FieldError`の拡張)を変更する必要があり、いずれも本Boltの範囲を超えるため、行っていない。利用者は、`tableNames`で対象を絞り込むことで、原因を特定できる。C9への追補(`FieldError`へのテーブル名・カラム名の追加)、または未対応の型のスキップの是非は、人間の判断事項として残す。
- [assumption] **R-02: DBの一意制約違反などの扱い**: `writeTableConfigDraft`の、`ConfigValidationException`以外の実行時例外(同時実行による一意制約違反など)は、C8に該当するレスポンス(403・422のみ)がなく、「入力の検証エラー」でもないため、422にはマップせず、変換せずに伝播した(500)。失敗としては、メトリクスに計上する。再実行は安全である(BR1.8により、既存テーブルはスキップされる)。
- [assumption] **R-01: 名前の照合は、大文字小文字を区別しない**: DBへ渡すエスケープ済みの名前による絞り込みは、DBの判定に従う(大文字小文字の扱いも、DBに従う)。結果の行の照合(備え)は、`equalsIgnoreCase`とした。MySQLの`lower_case_table_names`などで、DBが区別しない場合に、DBが一致と判定した行を落とさないためである。パターン文字(`_`・`%`)は、別の文字に一致するため、大文字小文字を無視しても、混入は防げる。
- [assumption] **R-01: `%`を含むスキーマ名・テーブル名は、リテラルとして扱う(拒否ではなく完全一致)**: レビューの「拒否または完全一致」のうち、完全一致を選んだ。該当する名前がなければ、全テーブル指定では空の結果、テーブル名の明示指定では422(存在しないテーブル、既存のBR2.9の挙動)になる。
- [assumption] **R-03: 401の応答の形式**: C8には401の定義がなく、Contract Designの追記(契約要約の「全エンドポイントに、認証フィルタが返す401・503を追加」)は、フィルタの401を指す。コントローラが、フィルタを通った後で、操作者を解決できない場合の401は、user-managementの`UserApiExceptionAdvice`と同じ形式(`title=Unauthorized`、`detail`は「認証情報を確認できませんでした。」、`application/problem+json`)に揃えた。`WWW-Authenticate`ヘッダーは付けていない(user-managementの実装に合わせた)。
- [assumption] **R-04: 読み取りの締切の検査は、テーブル単位**: NFR4.1の「読み取り25秒」を、接続確立から数える読み取り全体の締切として、テーブルごとの読み取りの前に検査する。1回のメタデータ呼び出しを、途中で打ち切る仕組み(別スレッドでの実行と中断)は、`DatabaseMetaData`の中断がドライバ依存で確実でないため、採らなかった。
- [assumption] **R-04: 接続取得の待ちの打ち切りは、取得中のスレッドを中断しない**: 従来の`future.cancel(true)`による中断を、`CompletableFuture`の完了の競合の解消(遅れて得られた接続を閉じる)に置き換えた。`getConnection`が固まったままの場合、専用のスレッドは、その呼び出しが返るまで残る(従来も、中断を無視するドライバでは同様)。

## 実行結果

| コマンド | 結果 |
|---|---|
| `./gradlew :backend:test --tests "com.mastersmith.schema.*"` | 成功。schemaのテストは、7クラス・53件(RdbmsMetadataReaderTest 27、SchemaIntrospectionControllerTest 9、SchemaIntrospectionServiceTest 6、LogSanitizerTest 4、SchemaIntrospectionRequestTest 3、RdbmsTableMetadataTest 2、SchemaIntrospectionResultTest 2)。失敗0・スキップ0。レビュー時点は6クラス・28件 |
| `./gradlew :backend:test :backend:spotlessCheck :backend:checkstyleMain :backend:checkstyleTest :backend:jacocoTestCoverageVerification` | 成功(バックエンド全体1414件、失敗0・スキップ0。他ユニットのコードは壊していない) |
| `AuthSecurityConfigTest`(`/api/config/schema-introspection`を追加した認証必須のケース) | 成功 |
| schemaパッケージの行カバレッジ(JaCoCo) | 93.4%(80%のフロアを満たす。`RdbmsMetadataReader` 143/163行、そのほかのクラスは全行) |

`spotlessCheck`は、成功した(以前の記録にあった既存ファイルの整形差分は、現在は解消している)。

## 残る懸念・引き継ぎ事項

- **業務データ用DataSourceを有効にした状態での、アプリ全体の起動(本Boltの範囲外、data-import-export(U8)の設定)**: `mastersmith.business-datasource.enabled=true`にして、`@SpringBootTest`でアプリ全体を起動しようとすると、`BusinessDataSourceConfig`が2つ目の`DataSource`Beanを定義するため、Spring Bootの内部設定DB用の自動構成が後退し、Flywayなどが業務データ用のDataSourceを使って失敗した(`jdbcUrl is required with driverClassName`。HikariCPは`jdbc-url`を要し、`url`が束縛されない点も影響している可能性がある)。このため、R-03(3)のうち「実物のセキュリティ設定を通した結合テスト」は、schema-introspector専用のフルコンテキストのテストではなく、共有の認証テスト(`AuthSecurityConfigTest`)への追加とした(R-03の是正案が許容する所在)。本番でenabled=trueにする前に、U8側で、内部設定DBのDataSourceを`@Primary`にする、または`spring.datasource`を明示的に構成するなどの対処が必要である(オーケストレーター・U8の担当への引き継ぎ事項)。
- **R-05の実DBによる検証**: MySQL/MariaDB/PostgreSQLの実際のドライバの挙動は、モックとH2でしか検証していない。Testcontainersなどによる検証は、CIの実行環境の定義(ci-pipelineステージ)の後に、追加を検討する。
- **`code-summary.md`・`unit-test-instructions.md`・`code-generation-plan.md`・`traceability.json`の更新**: 指示により編集していない。実際のコードは、C15の`OperatorContext`を読む実装であり、`ActiveRoleResolver`・`HeaderActiveRoleResolver`と`X-Active-Role-Id`ヘッダー方式は、既に存在しない(置き換え済み)。schemaのテストは、28件ではなく53件(7クラス)になった。実物のセキュリティ設定越しの401のテストは、`AuthSecurityConfigTest`にある。オーケストレーターの更新時に、これらを反映されたい。
- 設計文書(`contract-summary.md`のC8・`rules.md` BR2.9)への追記(未対応の型による422と`errors`、コントローラ自身による401)は、Inception・Functional Designの成果物の変更となるため、本Boltでは行っていない。レビュー指摘R-02の(2)に相当する、人間・オーケストレーターの対応事項である。
