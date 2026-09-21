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

# Tech Stack Decisions — config-import-export (U9)

`team.md`(Code Style)で確定済みの技術スタック(Java 25 + Spring Boot 4.1.1 + Gradle)、および、`nfr-requirements-questions.md`の確定回答に基づく、config-import-exportユニットの技術選定。本ユニットは、新しい技術を導入しない。

## 言語・フレームワーク

- **Java 25 + Spring Boot 4.1.1**: `team.md`で確定済み。REST API(`spring-boot-starter-web`)、トランザクション(`spring-boot-starter-data-jpa`、Spring Frameworkのトランザクション管理)を使う。
- **バリデーション**: 設定ファイルの検証は、機能設計(BR9.6〜BR9.13)の順序で、本ユニットと、各ユニットの検証専用メソッドが行う。利用者の入力データの検証エラーではなく、設定の定義の誤りであるため、`spring-boot-starter-validation`(Bean Validation)は、リクエストの本体の構造の検証には使わず、検証の誤りは、位置(JSONの位置)とi18nキーで、全件を集めて返す(BR9.7)。

## JSONの処理

- **Jackson(`com.fasterxml.jackson.core:jackson-databind`)**: 既存のバックエンドの依存(build.gradle.kts)をそのまま使う。設定ファイルの読み取りは、リクエスト本体を、木構造(`JsonNode`)として読み、位置つきの誤り(JSON上の位置)を、全件集める。書き出しは、ConfigDocument(entities.md)の値オブジェクトを、JSONへ直列化する。
- **読み取りの制限**: リクエスト本体の大きさ・入れ子の深さ・重複するプロパティに、本ユニット独自の制限は設けない。Jackson・Spring Bootの既定に従う(Q2=C。NFR2.2)。
- **未知のプロパティ**: 無視する(BR9.8)。`FAIL_ON_UNKNOWN_PROPERTIES`は無効にする。

## 永続化・トランザクション

- **内部設定DB(H2、ファイルモード)**: 取り込みの反映は、1つのトランザクションで行う(NFR4.1)。3つのユニット(config-engine・menu-navigation・permission-engine)は、同じ内部設定DBを、同じトランザクションの管理(Spring)で共有しているため、呼び出し元のトランザクションに参加させることで、1つのトランザクションにまとめられる。
- **エクスポートの読み取り**: 読み取り専用のトランザクションで、スナップショットの分離を使う(NFR4.4)。H2の分離レベルの指定は、NFR Designで確認して確定する。
- **コミット後の副作用**: キャッシュの再構築とイベントの発行は、トランザクション同期(`afterCommit`)で行う(NFR4.2)。

## イベント・監査

- **Springのアプリケーションイベント**: 取り込みの監査イベント(ConfigImportExecutedEvent)を、既存のイベントと同じ流儀で発行する。audit-loggingが購読する(fire-and-forget、NFR4.5)。

## 可観測性

- **Spring Boot Actuator・Micrometer**: 標準の計装のみ(NFR5.1)。取り込み・エクスポート固有のメトリクスは出さない(Q5=B)。
- **構造化ログ**: 既存のログの設定(JSON、リクエストID)に従い、開始・終了のINFOのログを出す(NFR5.2)。

## テスト

- **JUnit 5・Spring Bootのテスト・Mockito**: 既存のバックエンドのテストの構成に従う。テストの種類(単体・統合・設定ファイルの契約・複数プロファイル横断・安全失敗・認可拒否)は、`team.md`のTesting Postureに従い、Code Generationで確定する(NFR8.1)。

## NFR8.1: 保守性(業務固有名の非ハードコードと、テストの合格条件)

```
NFR8.1: 保守性とテストの合格条件
要件(a): 本ユニットのコードに、特定業務固有のテーブル名・カラム名・業務ルールをハードコードしない(functional-design rules.md BR9.20、NFR8)。設定ファイルの中の名前は、データとして扱い、意味を解釈しない
要件(b): 80%の行カバレッジを、CIでのマージ前実行で確保する(NFR8、team.md Testing Posture)
要件(c): team.mdの設定駆動に特有の必須のテスト種別のうち、本ユニットに該当するものを含める。(1)不正・不完全な設定ファイルでの安全失敗・バリデーションのテスト(422で拒否し、内部設定DBを変えないこと)、(2)2種類以上の業務ドメインの設定プロファイルによる、エクスポート→インポートの横断のテスト、(3)権限が不足する操作(401・403)を拒否することを狙った認可拒否のテスト
要件(d): 権限昇格の拒否・主権限が0件の拒否(BR9.11・BR9.12)は、権限判定に関わるため、主要な組み合わせを網羅する表形式(table-driven)のテストを、追加の合格条件とする(permission-engineの検証専用メソッドとの結合を含む)
根拠: NFR8、team.md Testing Posture(Q6・Q8)、project.md Mandated
```

- 監査ログの完全性テスト(更新・作成・削除の全操作が記録されること)は、MVPのスコープでは必須としない(team.md)。ただし、取り込みの監査イベントが、成功・失敗のそれぞれで、1回の取り込みにつき1件発行されること(BR9.16)は、本ユニットのテストで確認する。

## 導入しないもの

- 取り込み・エクスポート固有のメトリクスのライブラリ・設定(Q5=B)。
- リクエスト本体の大きさの制限の独自の実装、JSONの読み取りの独自の制限(Q2=C)。
- 取り込みの排他ロック、および、反映時の権限の再確認の仕組み(Q3=A)。
- 取り込みのトランザクションの独自のタイムアウト(Q4=B、Q6=A)。
- ファイルのストリーミング処理(想定規模は数MB以下。NFR3.1)。
