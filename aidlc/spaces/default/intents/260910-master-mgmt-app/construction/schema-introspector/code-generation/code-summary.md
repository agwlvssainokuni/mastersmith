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
