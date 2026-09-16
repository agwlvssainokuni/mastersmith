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

# Tech Stack Decisions — menu-navigation (U6)

本ユニット固有の新規技術選定はない。`team.md`(Feasibilityステージで確定済み)のプロジェクト全体技術スタックをそのまま採用する。

## 採用技術(既存決定の継承)

- **言語・フレームワーク**: Java 25 + Spring Boot(最新)。config-engine(U1)等の他serviceユニットと同一プロセス(embedded)で動作する。
- **ビルド**: Gradle(最新)。
- **永続化**: 内部設定DB(組込みH2、業務データ用RDBMSとは別接続)。`MenuItem`テーブルのマイグレーションはFlyway(audit-loggingユニットで導入済み)に従う。

## 本ユニット固有の実装方針

- **木構造の表現**: `MenuItem`の親子関係は自己参照外部キー(`parentMenuItemId`)で表現する。再帰的な階層構築・可視性判定(BR6.4)はアプリケーション層(Java)で行い、DB側の再帰クエリ(WITH RECURSIVE等)には依存しない(NFR3.1の想定規模ではアプリケーション層での再帰処理で十分、H2・PostgreSQL・MySQL/MariaDB間の再帰クエリ構文差異を避ける)。
- **activeRoleId解決**: schema-introspector・audit-loggingが既に確立した`ActiveRoleResolver`拡張点(`com.mastersmith.schema.security`パッケージ)をそのまま再利用する(functional-design-questions.md Q4=A)。

## 除外した選択肢

- 木構造の永続化にNested Set ModelやMaterialized Path等の専用パターンを採用する案は、NFR3.1の小規模な想定件数(数十〜百件)では単純な自己参照外部キー+アプリケーション層再帰で十分であり、書き込み(MenuItemの追加・移動)のたびに複雑な再計算が必要になるこれらのパターンは不採用とした。
