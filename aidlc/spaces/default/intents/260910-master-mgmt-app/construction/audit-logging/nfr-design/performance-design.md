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

# Performance Design — audit-logging (U7)

`construction/audit-logging/nfr-requirements/performance-requirements.md`(NFR1.1〜NFR1.3)、および`nfr-design-questions.md`の確定回答に基づく、audit-loggingユニットの性能設計。

## インデックス設計(NFR1.1・NFR1.2対応、Q2確定)

Flyway(具体的なバージョン選定はCode Generationで確定)のバージョン管理された移行スクリプトで、内部設定DB(組込みDB)の`audit_log_entry`テーブルに以下のインデックスを明示的に定義する。JPAエンティティの`@Index`アノテーション経由のDDL自動生成(Hibernate ddl-auto)には委ねない。

```sql
-- V{n}__create_audit_log_entry_indexes.sql(概念設計、実際のバージョン番号はCode Generationで確定)
CREATE INDEX idx_audit_log_entry_occurred_at ON audit_log_entry (occurred_at DESC);
CREATE INDEX idx_audit_log_entry_target_type_occurred_at ON audit_log_entry (target_type, occurred_at DESC);
```

- `idx_audit_log_entry_occurred_at`: `targetType`未指定時の`GET /api/audit-log`(BR7.9の既定並び順)を、範囲スキャンのみで完結させる。
- `idx_audit_log_entry_target_type_occurred_at`: `targetType`指定時(等価一致フィルタ、BR7.9)のクエリを、複合インデックスの先頭列(`target_type`)での絞り込み+末尾列(`occurred_at DESC`)でのソート済み範囲スキャンに解決させ、フィルタ+ソートの双方をインデックスのみで満たす。

この2つのインデックスにより、AuditLogEntryの行数がNFR3.2(年間数万〜数十万件)の想定で増加し続けても、`GET /api/audit-log`のクエリ実行計画はテーブル全体の件数に比例せず、インデックスを用いた範囲スキャンで完結する。

## ページング方式(NFR1.1関連、Q3確定)

C6契約の`page`パラメータ(整数、1始まり)をそのままオフセットとして扱い、単純な`LIMIT`/`OFFSET`によるページング実装とする。

```sql
-- 概念設計(targetType指定時)
SELECT * FROM audit_log_entry
WHERE target_type = :targetType
ORDER BY occurred_at DESC
LIMIT :pageSize OFFSET :offset
```

深いページ番号(大きいoffset)では性能がやや劣化しうるが、`scalability-requirements.md` NFR3.3の再検討トリガー(8万行到達)に達するまでの規模ではこの劣化は許容範囲と判断する。キーセット(カーソル)方式への切り替えは、8万行到達時の再検討事項(`scalability-design.md`参照)に含める。

## 応答時間予算(NFR1.1対応)

監査ログ閲覧画面は列単位の権限判定を行わない(BR7.10でscreenKey単位の1回のみ判定)ため、他の一覧画面(list-engine)のような列別権限フィルタリングのオーバーヘッドがない。`GET /api/audit-log`単体の応答時間予算は、上記インデックス設計を前提として、NFR1.1(3秒以内、95パーセンタイル)をそのまま満たせると判断する。

## DBコネクションプール(NFR1.1関連)

内部設定DB(組込みDB、team.md Q12b)専用の既存HikariCP(Spring Boot標準、他の内部設定DB依存ユニットと共有)をそのまま利用する。本ユニット固有の接続プール分離・パラメータ調整は不要(監査ログ閲覧は低頻度アクセス画面であり、NFR3.4参照)。

## Performance Anti-Requirements(除外事項、`performance-requirements.md`から継承)

- イベント購読からAuditLogEntry書き込みまでの内部処理時間には、個別の数値目標を設けない(NFR1.3)。
- キャッシュ層は設けない(閲覧結果は常に最新のAuditLogEntryを反映する必要があり、キャッシュの導入は要件上のメリットがない)。
