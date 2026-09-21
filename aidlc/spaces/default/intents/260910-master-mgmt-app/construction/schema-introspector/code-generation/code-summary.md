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

# Code Summary — schema-introspector (U2)

承認済み`code-generation-plan.md`(全14 Step)に基づき、schema-introspectorユニットを実装した。

## 作成/変更ファイル

### 新規作成(本体コード)

| ファイル | 内容 |
|---|---|
| `backend/src/main/java/com/mastersmith/schema/dto/SchemaIntrospectionRequest.java` | C8リクエストボディ(record: schemaName, tableNames) |
| `backend/src/main/java/com/mastersmith/schema/dto/SchemaIntrospectionResult.java` | C8レスポンスボディ(record: generatedTableConfigIds) |
| `backend/src/main/java/com/mastersmith/schema/dto/RdbmsTableMetadata.java` | 内部読み取りモデル(record: schemaName, tableName, columns) |
| `backend/src/main/java/com/mastersmith/schema/dto/RdbmsColumnMetadata.java` | 内部読み取りモデル(record: columnName, rawTypeName, isPrimaryKey, nullable) |
| `backend/src/main/java/com/mastersmith/schema/security/ActiveRoleResolver.java` | 認可拡張点インタフェース(BR2.8) |
| `backend/src/main/java/com/mastersmith/schema/security/HeaderActiveRoleResolver.java` | `X-Active-Role-Id`ヘッダー読み取りの暫定実装(authentication-service未実装への対応) |
| `backend/src/main/java/com/mastersmith/schema/rdbms/RdbmsMetadataReader.java` | JDBC `DatabaseMetaData`によるスキーマ読み取り(方言判定、5秒接続/25秒読み取りタイムアウト、BR2.1/2.3/2.4/2.10) |
| `backend/src/main/java/com/mastersmith/schema/rdbms/RdbmsSchemaSnapshot.java` | RdbmsMetadataReaderの内部読み取り結果の中間表現 |
| `backend/src/main/java/com/mastersmith/schema/service/SchemaIntrospectionService.java` | ビジネスロジック(C9契約型への変換、`ConfigEngineApi.writeTableConfigDraft`呼び出し、BR2.2/2.5/2.6/2.7/2.9) |
| `backend/src/main/java/com/mastersmith/schema/web/SchemaIntrospectionController.java` | `POST /api/config/schema-introspection`(C8)、サーバー側での`canAccessScreen`再検証(BR2.8)、403/422のProblemDetailマッピング |
| `backend/src/main/java/com/mastersmith/schema/exception/SchemaIntrospectionException.java` | 読み取り失敗時例外(422、BR2.9) |
| `backend/src/main/java/com/mastersmith/schema/exception/SchemaIntrospectionForbiddenException.java` | 権限拒否時例外(403) |

### 新規作成(テスト、計29件)

| ファイル | 対応レイヤー |
|---|---|
| `backend/src/test/java/com/mastersmith/schema/dto/SchemaIntrospectionRequestTest.java` | データモデル層(Step 4) |
| `backend/src/test/java/com/mastersmith/schema/dto/SchemaIntrospectionResultTest.java` | データモデル層(Step 4) |
| `backend/src/test/java/com/mastersmith/schema/dto/RdbmsTableMetadataTest.java` | データモデル層(Step 4) |
| `backend/src/test/java/com/mastersmith/schema/security/HeaderActiveRoleResolverTest.java` | 認可拡張点(Step 6) |
| `backend/src/test/java/com/mastersmith/schema/rdbms/RdbmsMetadataReaderTest.java` | メタデータ読み取り層、H2実データ統合テスト(Step 8) |
| `backend/src/test/java/com/mastersmith/schema/service/SchemaIntrospectionServiceTest.java` | ビジネスロジック層(Step 10) |
| `backend/src/test/java/com/mastersmith/schema/web/SchemaIntrospectionControllerTest.java` | RESTコントローラ層、200/403/422検証(Step 12) |

### 変更

| ファイル | 変更内容 |
|---|---|
| `backend/build.gradle.kts` | `spring-boot-starter-web`・`spring-boot-starter-webmvc-test`を追加(schema-introspectorが本コードベースで最初にRESTコントローラを必要とするUnitのため) |
| `backend/src/main/java/com/mastersmith/permission/PermissionEngineApi.java` | Javadocの`consumers`列挙に`schema-introspector`を追加(NFR Designレビュー指摘R-02対応、計画の前提修正) |

## 主要な実装判断

- **認可基盤の暫定実装**: `ActiveRoleResolver`/`HeaderActiveRoleResolver`は、authentication-service(U5、Bolt 7相当)未実装への対応として、リクエストヘッダーから直接ロールIDを読み取る暫定実装とした。認可判定自体(`PermissionEngineApi.canAccessScreen`)はサーバー側で必ず再検証しており、`project.md`のMandated要件(実効権限の再検証)は満たしている。
- **`@ConditionalOnProperty`ガード**: `SchemaIntrospectionService`・`SchemaIntrospectionController`にも、`RdbmsMetadataReader`・`CsvExportService`(data-import-export)と同じ`mastersmith.business-datasource.enabled=true`ガードを付与し、既定の`enabled=false`でもアプリが正常起動することを維持した。
- **NULL可否の非伝搬**: `RdbmsColumnMetadata.nullable`はBR2.10により内部モデルには保持するが、`ConfigEngineApi`へ渡す`ColumnDraftEntry`へは伝搬しない。
- **fail-fast**: メタデータ読み取り失敗時は`SchemaIntrospectionException`(422)を送出し、`writeTableConfigDraft`を一切呼び出さない(BR2.9)。

## 計画からの逸脱

- **`@WebMvcTest`のパッケージ移動**: 使用しているSpring Bootのバージョンでは`@WebMvcTest`が`org.springframework.boot.webmvc.test.autoconfigure`へ移動し、Jackson自動設定もJackson-3系(`tools.jackson`)の別モジュールに分離されている。Jackson-2/3のBean型不一致を避けるため、コントローラテストではリクエストJSONを`ObjectMapper`の自動注入ではなくリテラルで構築した。
- **HTTPステータス定数**: このSpringバージョンでは`HttpStatus.UNPROCESSABLE_ENTITY`が非推奨であり、C8契約の文言(「422 Unprocessable Content」)とも一致する`HttpStatus.UNPROCESSABLE_CONTENT`を採用した。
- **`spotlessCheck`の既存の失敗**: `./gradlew :backend:spotlessCheck`は本Unitと無関係な既存21ファイルで失敗する(`git stash`によるクリーンベースラインでも同一の失敗を確認済み。本Boltより前から存在するフォーマッタバージョンのドリフト)。本Unitが作成・変更したファイルはこれに含まれない。`checkstyleMain`/`checkstyleTest`と全テストスイート(jacocoの80%行カバレッジゲート含む)はグリーン。

## テストカバレッジ概要

- `./gradlew :backend:test --tests "com.mastersmith.schema.*"` → 29/29件成功
- リポジトリ全体の`./gradlew :backend:test`もグリーン
- `schema`パッケージの行カバレッジ ≈89%(80%フロアを上回る)

## Assumptions & Open Questions

- `ActiveRoleResolver`/`HeaderActiveRoleResolver`の暫定実装は、authentication-service(U5、Bolt 7相当)の実装時にJWT検証ベースの実装へ置き換える引き継ぎ事項として残す。それまでの間、`X-Active-Role-Id`ヘッダーは信頼された内部呼び出し経路(将来のAPI Gateway/BFF層等)からのみ設定される前提とする。

## 更新(2026-09-21): 操作者取得の置き換えと、レビュー指摘(iteration 1、NOT-READY)への修正

**上の記述のうち、`ActiveRoleResolver`・`HeaderActiveRoleResolver`・`HeaderActiveRoleResolverTest`に関するものは、次のとおり置き換わっている。** authentication-service(U5)のCode Generationで、これらの暫定実装を削除し、`SchemaIntrospectionController`が、共有契約C15の`com.mastersmith.common.security.OperatorContext`を読む実装になった。`X-Active-Role-Id`ヘッダーを信頼する経路は残っていない。

本ユニットは、Code Generationのレビュー導入前に作成されたため、初回のアーキテクチャレビューで、重要度の高い指摘3件と細かい指摘4件を受けた。次のとおり修正した(詳細・`[assumption]`は`code-generation-notes.md`)。

| 指摘 | 対処 |
|---|---|
| R-01(名前がパターン検索のままで、別テーブルの列が混入) | LIKEパターンのエスケープ(`getSearchStringEscape`)と、結果の`TABLE_NAME`・`TABLE_SCHEM`の照合を追加。`_`・`%`の回帰テストを追加 |
| R-02(未対応の型で500になる) | `ConfigValidationException`を`SchemaDraftValidationException`に変換し、BR2.9のfail-fastに沿って全体を422で拒否(`errors`にfieldとmessage)。どのテーブル・カラムかは、応答に含められない(`[assumption]`) |
| R-03(未認証とロール未選択の区別がない) | 操作者そのものが解決できなければ、`canAccessScreen`を呼ばずに401。ロール未選択(null)はC10へ渡して403(機能設計BR5.12、user-managementの流儀に合わせた)。実際のフィルタチェーン越しの401は`AuthSecurityConfigTest`で確認 |
| R-04〜R-07(細かい指摘) | 読み取り全体の締切をテーブル単位で検査、遅れて得られた接続を閉じる、MySQL/MariaDBの分岐と`readSchema`の成功経路のテスト追加、ログ・例外メッセージのサニタイズ(`LogSanitizer`)、`source-manifest.json`を実コードに合わせて更新 |

- テスト: schemaパッケージは7クラス・53件(修正前は28件)。プロジェクト全体は1,414件、失敗0。行カバレッジは全体93.3%、schemaパッケージ93.4%(フロア80%)。
- 残る懸念: 実RDBMS(PostgreSQL・MySQL/MariaDB)での検証は未実施(H2とモック)。data-import-export(U8)の`BusinessDataSourceConfig`が2つ目の`DataSource`Beanを定義するため、`mastersmith.business-datasource.enabled=true`で全体を起動すると、内部設定DB用の自動構成が後退してFlywayが失敗する(U8側で内部設定DBのDataSourceを`@Primary`にするなどの対処が必要。別途の課題)。
- `unit-test-instructions.md`・`code-generation-plan.md`は、承認済みの内容のため編集していない。ヘッダー方式・テスト件数の記述は、この更新が正である。
