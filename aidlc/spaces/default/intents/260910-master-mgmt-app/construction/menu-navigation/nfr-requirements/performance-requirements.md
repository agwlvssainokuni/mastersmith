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

# Performance Requirements — menu-navigation (U6)

`inception/requirements-analysis/requirements.md`のNFR1、および`nfr-requirements-questions.md`の確定回答に基づく、menu-navigationユニットの性能要件。

## NFR1.1: `GET /api/menu`の応答時間目標

- **Metric**: `GET /api/menu`の応答時間
- **Target**: 3秒以内
- **Percentile**: 95パーセンタイル
- **Load condition**: ログイン後トップ画面・サイドバーの初期表示に直結する高頻度パス。NFR3.1(scalability-requirements.md)の想定規模(MenuItem数十〜百件程度、階層深さ3〜4階層)を前提とする
- **Measurement method**: アプリケーション内メトリクス(Micrometer計装、observability-requirements.md参照)
- **根拠**: NFR1が定める一覧・詳細画面と同水準の目標をそのまま適用する(Q1確定=A)。トップ画面・サイドバーの初期表示体感を左右する高頻度パスであり、緩和の理由がない。
- **コスト内訳との整合性確認**: `functional-spec.md` W1(手順4a・4b)により、リーフ項目1件あたり(a) ConfigEngine側のtargetTableConfigId存在確認(BR6.7)、(b) PermissionEngine.canAccessScreen呼び出し(BR6.3)の最大2回の他コンポーネント呼び出しが発生する。NFR3.1の想定規模(数十〜百件)では、最大で約200回の呼び出しが1リクエスト内で発生しうる。以下の理由により3秒/p95予算内に収まると判断する。
  - (a)は同一プロセス内のJavaメソッド呼び出しであり(embedded配置、ネットワークRPCではない)、ConfigEngineが保持する`ConfigCache`のインメモリインデックス(`findTableConfigById`相当、`tableConfigId`→`TableConfig`のマップ)を参照するのみのO(1)操作であるため、無視できるコスト
  - (b)も同一プロセス内のJavaインタフェース呼び出しであり、PermissionEngineは`PermissionCacheConfig`(Caffeine、TTL30秒・最大5000エントリ)で判定結果をキャッシュしている(`permission-engine/nfr-design/performance-design.md`)。個別呼び出しの目標はpermission-engine側で50ms/p95(`permission-engine/nfr-design/observability-design.md`の`permission_check_duration_seconds`参照)と定義されており、キャッシュヒット時はこれを大幅に下回る。同一ロールの利用者が同一メニュー構成へ繰り返しアクセスする典型的な利用パターンでは、TTL期間内はキャッシュヒットが支配的になり、実効コストは低く保たれる
  - 仮に百件全てがキャッシュミス(コールドキャッシュ)した最悪ケースでは、1件あたり平均30ms(=3秒÷100件)の予算に対しpermission-engine側の50ms/p95目標を上回る可能性があり、この場合は3秒予算を超過しうる。これは既知のリスクとして受容し、実運用で問題が顕在化した場合は、キャッシュのプリウォーム(ログイン時の事前解決)または`GET /api/menu`専用の一括権限判定APIの追加をフォローアップ課題とする(現時点ではPermissionEngine側にAPI追加の判断は本ユニットのスコープ外)。

## NFR1.2: `POST/PUT/DELETE /api/menu-items`の応答時間目標

- **Metric**: `POST/PUT/DELETE /api/menu-items`(Contract Design追補)の応答時間
- **Target**: 5秒以内
- **Percentile**: 該当なし。管理者による低頻度実行が前提であり、統計的パーセンタイルではなく単一実行あたりの上限値として定義する
- **Load condition**: 業務メニュー設定画面からの低頻度な設定操作。同時実行は想定しない
- **Measurement method**: アプリケーション内メトリクス
- **根拠**: schema-introspectorの管理操作(30秒以内)ほどの重さはない単純なCRUD操作である一方、NFR1(3秒)ほど厳しくする必要もない中間的な目標として設定した(Q2確定=A)。

## Performance Anti-Requirements(除外事項)

- 「メニュー表示は速くなければならない」のような測定不能な目標は設定しない。NFR1.1のとおり、具体的な上限値・パーセンタイル・負荷条件を明記する。
- 数千件規模のMenuItem・5階層以上の深い階層に対する性能保証は本MVPスコープの対象外とする(Q3確定=A、scalability-requirements.mdの「既知の制約」参照)。
