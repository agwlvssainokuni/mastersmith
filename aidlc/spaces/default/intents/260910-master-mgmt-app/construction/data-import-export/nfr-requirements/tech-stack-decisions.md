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

# Tech Stack Decisions — data-import-export (U8)

`nfr-requirements-questions.md`の確定回答に基づく、data-import-exportユニット固有の技術選定。全体の技術スタック(Java 25 + Spring Boot、Gradle等)は`requirements.md`のConstraintsで確定済みであり、ここでは本ユニット固有の追加選定のみを扱う。

## CSVパースライブラリ: Apache Commons CSV

- **選定**: Apache Commons CSV
- **理由**: ストリーミング読み書きに標準対応しており、BR8.10(ストリーミング処理)の実装に適する。Apache Commonsエコシステムに属し、社内で他プロジェクトでの採用実績がある想定でメンテナンス性が高い
- **対抗案として検討したもの**:
  - OpenCSV: BOM対応・アノテーションベースマッピングを持つが、本ユニットは動的なColumnConfig(config-engine由来)に基づく列マッピングが必要なため、アノテーションベースの静的マッピング機能は活用しない
  - 自前実装(java.io/java.nio): ライブラリ依存を避けられるが、CSVのクォーティング・エスケープ処理(BR8.1のCSV形式)を正しく実装するコストが高く、既存の検証済みライブラリを使う方が信頼性が高い
- **実装上の注意**: BR8.1のUTF-8 BOM付き出力は、Apache Commons CSV自体は明示的なBOM書き込み機能を持たないため、出力ストリームの先頭にBOMバイト列(`EF BB BF`)を明示的に書き込む実装が必要
- **根拠**: `nfr-requirements-questions.md` Q7確定(A)

## 一時バッファの実装方式: アプリケーションメモリ上のリスト

- **選定**: CSVインポートの検証済み行データは、常にアプリケーションメモリ上のリスト(`java.util.List`等)にバッファする
- **理由**: NFR1想定規模(最大10万行)であれば、変換後の軽量な行データ(列数×10万行のプリミティブ値)のメモリ影響は限定的(`rules.md` BR8.10のnotes、`performance-requirements.md` NFR1.3参照)
- **対抗案として検討したもの**: 一時テーブル方式(RDBMSの一時テーブルへ変換後データを書き込み、全行検証後にまとめて本テーブルへ反映)。より大規模なデータ量に対応できるが、MVPスコープでは複雑さに見合わないと判断
- **拡張余地**: 将来、NFR1想定規模を大きく超えるデータ量への対応が必要になった場合、一時テーブル方式への切り替えを検討する
- **根拠**: `nfr-requirements-questions.md` Q8確定(A)
