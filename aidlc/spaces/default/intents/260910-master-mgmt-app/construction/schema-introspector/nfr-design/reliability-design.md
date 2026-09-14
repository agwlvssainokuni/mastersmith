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

# Reliability Design — schema-introspector (U2)

`nfr-requirements/reliability-requirements.md`(NFR4.1〜NFR4.2)、および`nfr-design-questions.md`の確定回答(Q2)に基づく、schema-introspectorユニットの信頼性設計。

## NFR4.1: fail-fast実行フロー

- サーキットブレーカー・リトライは適用しない(Q2確定)。対象RDBMSへの接続・メタデータ読み取りは単純な1回限りの試行とする。
- 同期実行(performance-design.md Q1確定)における無期限ブロッキングを避けるため、JDBC接続に明示的なタイムアウトを設定する:
  - 接続タイムアウト: 5秒
  - 読み取りタイムアウト: 25秒
  - 合計してNFR1.1の応答時間目標(30秒以内)の範囲に収まるよう設計する。
- IF 接続タイムアウト・読み取りタイムアウト・SQL例外のいずれかが発生 THEN 例外を捕捉し、部分的に読み取り済みのデータを破棄した上で422(ProblemDetails)を返す(BR2.9)。`writeTableConfigDraft`は一切呼び出さない。
- 自動リトライは行わない。管理者がエラーメッセージを確認した上で、必要に応じて手動で再実行する。

## NFR4.2: データ整合性・バックアップ(設計上の対応不要)

- 本ユニットは永続データを持たないため、独自のバックアップ・リカバリ設計は不要。
- config-engineへの書き込み(`writeTableConfigDraft`)は1回のトランザクション呼び出しであり、成功/失敗いずれかの結果を呼び出し元へ即座に返す(部分成功状態を残さない、config-engine側の`@Transactional`設計に委ねる)。
