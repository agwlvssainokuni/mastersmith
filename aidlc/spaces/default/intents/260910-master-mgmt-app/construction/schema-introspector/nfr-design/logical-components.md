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

# Logical Components — schema-introspector (U2)

本ユニットのNFR設計を実現する論理コンポーネント構成、障害ドメイン、被害範囲(blast radius)の整理。インフラ設計(本プロジェクトではスコープ外、`aidlc-state.md`参照)へ引き継ぐための、コンポーネントレベルの整理を目的とする。

## 論理コンポーネント一覧

| コンポーネント | 責務 | 配置 |
|---|---|---|
| `SchemaIntrospectionController` | REST層(`POST /api/config/schema-introspection`)。認可判定の起点、リクエスト/レスポンスの変換 | embedded(Spring Bootの他コントローラと同一プロセス) |
| `SchemaIntrospectionService` | ドメインロジック(BR2.1〜BR2.10の実装、W1ワークフローの制御) | embedded |
| `RdbmsMetadataReader` | JDBC `DatabaseMetaData`を用いた対象RDBMSからのテーブル/カラム/主キー/NULL可否の読み取り | embedded |

いずれも新規のデプロイ単位ではなく、config-engine等と同じSpring Bootアプリケーション内のコンポーネント(Bean)として実装する(`unit-of-work.md` U2「デプロイモデル: embedded」)。

## 障害ドメイン(Failure Domain)

- `SchemaIntrospectionController`/`Service`/`RdbmsMetadataReader`の失敗は、当該HTTPリクエストのスコープに閉じる(同期処理、performance-design.md)。例外はコントローラ層で捕捉され、422/403のエラーレスポンスに変換される。
- 本ユニットの障害(対象RDBMS接続失敗等)が、同一プロセス内の他ユニット(list-engine、record-edit-engine等)の処理に波及することはない(スレッドローカルな例外処理であり、共有可変状態を持たないため)。
- 唯一の副作用はconfig-engineへの`writeTableConfigDraft`呼び出しだが、これは全読み取り完了後の1回のトランザクション呼び出しであり(BR2.9)、本ユニットの障害時にはそもそも呼び出されない。

## 被害範囲(Blast Radius)

- 業務データ用RDBMSへは読み取り専用アクセスのみ(BR2.1)であり、本ユニットの誤動作が業務データを損なうことはない。
- 書き込み先はconfig-engineの`TableConfig`/`ColumnConfig`のみであり、それも「既存設定があればスキップ」(BR2.7、config-engine側BR1.8)という非破壊的な設計のため、誤ったイントロスペクション実行が既存の手動設定を上書きすることはない。
- 以上より、本ユニットの被害範囲は「新規テーブルの初期ドラフトが期待通り生成されない」という機能的な失敗に限定され、データ破壊・他ユニットへの障害波及のリスクは低いと評価する。

## 共有リソース

- 業務データ用RDBMSへの接続(データソース)は、list-engine・record-edit-engine・data-import-export等、業務データにアクセスする他ユニットと共有する。本ユニット固有の追加コネクションプールは設けない(performance-design.md)。
- config-engineの内部設定DB接続はconfig-engineが所有し、本ユニットは`writeTableConfigDraft`のAPI呼び出しを通じてのみ間接的にアクセスする(直接のDB接続は持たない)。
