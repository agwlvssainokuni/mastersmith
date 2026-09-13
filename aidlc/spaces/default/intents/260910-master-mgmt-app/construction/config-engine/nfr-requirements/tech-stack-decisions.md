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

# Tech Stack Decisions — config-engine (U1)

`team.md`で確定済みの全体技術スタック（Java 25 + Spring Boot + Gradle、内部設定DB=H2）を前提に、config-engineユニット固有の技術選定を記録する。

## 基盤技術（既存決定の踏襲）

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 + Spring Boot（最新） | `team.md`確定事項（Feasibilityステージで決定済み） |
| ビルドツール | Gradle（最新） | 同上 |
| 内部設定DB | H2（組込みDB、ファイルモード） | `team.md`確定事項＋本ステージNFR-REL-2確定回答（Q3） |

## config-engine固有の技術選定

### 設定データのキャッシュ機構

- **選定**: Spring標準の`ConcurrentHashMap`ベースのアプリケーション内メモリキャッシュ（または同等のSpring `@Cacheable`抽象化。外部キャッシュサーバー、Caffeine等の追加ライブラリは導入しない）
- **根拠**: NFR1.2確定回答（Q2=A）。起動時に全設定を読み込む方式であり、想定データ量（数十〜百テーブル、Q5確定）は小規模なため、専用キャッシュライブラリの複雑性は不要と判断する。単一インスタンス内で完結し、外部プロセス間でのキャッシュ共有は要件としない。

### バリデーションルール・選択肢定義のデータ形式

- **選定**: `validationRule`（`functional-design-questions.md` Q3確定: 構造化データ）・`choiceOptions`・`fkReference`は、内部設定DB（H2）上ではJSON型カラム（またはJSON文字列として保存するTEXT/CLOBカラム）として永続化し、アプリケーション層ではJacksonによりJavaオブジェクトへデシリアライズする
- **根拠**: `functional-design/entities.md`で確定した各属性は構造化データ（object/array）であり、H2はネイティブJSON型をサポートするため、追加の正規化テーブル設計は不要と判断する

### fail-fast検証の実装方式

- **選定**: Jakarta Bean Validation（`jakarta.validation`）のアノテーションベース検証を基本としつつ、`rules.md`のBR1.1〜BR1.4・BR1.12のような複合ルール（select/radioのchoiceOptions/fkReference排他等）はカスタムバリデータ（`ConstraintValidator`実装）で補完する
- **根拠**: Spring Bootとの親和性が高く、`ConfigValidationException`への変換（BR1.11のフィールド単位エラー情報）もBean Validationの`ConstraintViolation`から機械的にマッピングできる

## Code Generationへの申し送り事項

- 具体的なライブラリバージョン（Jackson, Jakarta Validation等のバージョン）は、Spring Boot（最新）が依存管理するバージョンに従う（`team.md`のフォーマッタ・リンタ節と同様、詳細な版指定は技術詳細確定時に行う）。
- `functional-spec.md`のAssumptions & Open Questionsで指摘した、TableConfig/ColumnConfig/TranslationEntryのフィールド単位編集用REST APIの具体的なエンドポイント定義（Contract Designへの追補）は、Code Generation着手前に解決する必要がある（未解決のまま実装に着手しない）。
