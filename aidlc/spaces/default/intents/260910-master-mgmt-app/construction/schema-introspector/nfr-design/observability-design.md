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

# Observability Design — schema-introspector (U2)

`nfr-requirements/observability-requirements.md`(NFR5.1)に基づく、schema-introspectorユニットの可観測性設計。

## NFR5.1: メトリクス・ログ・トレーシングの実装方式

### メトリクス

- Micrometer(Spring Boot標準の計測ファサード)経由でMeterRegistryへ登録し、OTELエクスポータ(アプリ共通のOTEL基盤設定)へエクスポートする。本ユニット固有のエクスポータ設定は追加しない。
- `nfr-requirements/observability-requirements.md`で定義した4メトリクス(`schema_introspection_duration_seconds`、`schema_introspection_tables_total`、`schema_introspection_generated_total`、`schema_introspection_failures_total`)を、サービス層の処理完了時点(成功・失敗いずれも)で記録する。

### ログ

- SLF4J + 構造化ログ(JSON、アプリ共通のログ出力設定)を用いる。
- リクエストIDはアプリ共通のMDC(Mapped Diagnostic Context)伝搬機構により自動付与される(本ユニット固有の実装は不要)。
- ログ出力のタイミング・内容は`observability-requirements.md`の表のとおり(実行開始/完了/失敗)。接続情報等の機微情報はログ用DTOに含めない設計とする(security-design.md NFR2.2)。

### トレーシング

- アプリ共通のMicrometer Tracing(またはSpring Cloud Sleuth相当)基盤にそのまま参加する。本ユニット固有のスパン設計は、コントローラ層の1スパン + config-engine内部呼び出しの子スパンの2階層で十分とし、追加のカスタムスパンは設けない。

### アラート・ダッシュボード

- `observability-requirements.md`確定のとおり、専用アラート・ダッシュボードは設けない。アプリ全体の監視基盤にメトリクス・ログが取り込まれることで足りるとする設計判断。
