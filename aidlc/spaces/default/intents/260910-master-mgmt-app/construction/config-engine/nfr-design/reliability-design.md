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

# Reliability Design — config-engine (U1)

`construction/config-engine/nfr-requirements/reliability-requirements.md`（NFR4.1, NFR4.2）を実現する具体設計。

## fail-fast検証の実行フロー

1. アプリ起動シーケンス中、Spring Bootの`ApplicationRunner`（または同等の起動フック）として`ConfigModelStore`の初期化処理が実行される。
2. 内部設定DBから全TableConfig/ColumnConfig/TranslationEntryを読み込み、`ConfigValidator`がBR1.1〜BR1.4を検証する。
3. 検証に失敗した場合、`ConfigValidationException`を送出し、Spring Bootアプリケーションコンテキストの起動を失敗させる（`SpringApplication.run()`が例外を伝播し、プロセスが起動しない）。
4. 検証に成功した場合、`ConfigCache`へスナップショットを構築し、起動を継続する（Q1確定: 専用ヘルスインジケータは設けない。起動失敗自体が最も強いシグナルであるため）。

## 障害時の挙動

- **起動時fail-fast**: 上記のとおり、起動自体を中断する（回復可能エラーではなく致命的エラーとして扱う、`phases/construction.md` Error Handling方針に整合）。
- **実行時の内部設定DB接続断**: config-engineは起動時に全設定をキャッシュ済みのため、実行時の内部設定DB接続断は読み取り系操作（`getTableConfig`等）には影響しない。書き込み系操作（管理画面からの変更）のみ影響を受け、呼び出し元に例外を伝播する（サーキットブレーカー等のパターンは、内部設定DBが同一ホスト上の組込みDBであり外部ネットワーク越しの依存ではないため、本ユニットでは設計しない）。
- **circuit breaker / retry**: config-engineの消費者（list-engine等）からの呼び出しは同一プロセス内のJavaメソッド呼び出しであり、ネットワーク境界を越えないため、サーキットブレーカー・リトライパターンの対象外とする（`nfr-design.md`のFocus areasのうち本カテゴリはconfig-engineには適用しない）。

## バックアップ・リカバリ

- `nfr-requirements/reliability-requirements.md`のNFR4.2（H2ファイルモード永続化、日次バックアップ運用手順）を踏襲。具体的なバックアップ自動化はOperationフェーズ（現状スコープ外）で扱う。

## 楽観ロック（config-engine自身の設定データ）

- `traceability.json`のNFR4に記載のとおり、config-engine自身の設定データ（TableConfig/ColumnConfig/TranslationEntry）には楽観ロックを適用しない（単一管理者による低頻度操作を前提とする）。業務データに対する楽観ロック（FR6.3）はrecord-edit-engine（U11）の責務であり、config-engineは対象テーブルの楽観ロック対象列の有無（`optimisticLockColumn`）を提供するのみ。
