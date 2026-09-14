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

# Tech Stack Decisions — schema-introspector (U2)

本ユニット固有の新規技術選定はない。`team.md`(Feasibilityステージで確定済み)のプロジェクト全体技術スタックをそのまま採用する。

## 採用技術(既存決定の継承)

- **言語・フレームワーク**: Java 25 + Spring Boot(最新)。config-engine(U1)等の他serviceユニットと同一プロセス(embedded)で動作する。
- **ビルド**: Gradle(最新)。
- **対象RDBMSアクセス**: 業務データ用RDBMS(PostgreSQL/MySQL/MariaDB)への読み取り専用アクセス。

## 本ユニット固有の実装方針

- **メタデータ読み取り方式**: JDBC標準の`java.sql.DatabaseMetaData`を用いて、対象RDBMSの方言に依らずポータブルにテーブル・カラム・主キー制約・NULL可否を取得する。方言固有のカタログクエリ(information_schemaへの直接SQL等)は、JDBC標準APIで取得できない情報がある場合にのみ限定的に用いる。
- **RDBMS方言の表現**: config-engineが既に定義する`RdbmsDialect`列挙(POSTGRESQL/MYSQL/MARIADB、`construction/config-engine/functional-design/rules.md` BR1.12参照)をそのまま再利用し、本ユニットで独自の方言列挙を新設しない(`functional-design/rules.md` BR2.3)。
- **根拠**: config-engineが方言正規化ロジック(`RdbmsTypeNormalizer`/`LogicalType`)を既に実装・保有しており、schema-introspectorはメタデータの読み取りとconfig-engineへの受け渡しに専念する設計(`functional-spec.md` W1)と整合させるため。

## 除外した選択肢

- 対象RDBMSごとに個別のメタデータ取得ライブラリ・方言固有のシステムカタログクエリを直接実装する方式は、JDBC標準APIでの実装より保守コストが高く、FR1.6(共通エンジン層への業務固有ハードコード禁止の精神を踏襲し、RDBMS固有ロジックを局所化する観点からも)不採用とした。
