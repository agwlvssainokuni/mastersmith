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

# Observability Requirements — menu-navigation (U6)

`inception/requirements-analysis/requirements.md`のNFR5、および`nfr-requirements-questions.md`の確定回答(Q4)に基づく、menu-navigationユニットの可観測性要件。

## NFR5.1: メトリクス・ログ・トレーシング方針(Q4確定回答: A)

OTEL基盤へエクスポートする(NFR5)。

### メトリクス

| メトリクス名 | 種別 | 説明 |
|---|---|---|
| `menu_navigation.get_menu.duration` | Histogram | `GET /api/menu`の応答時間 |
| `menu_navigation.get_menu.error_count` | Counter | `GET /api/menu`のエラー件数(累積) |
| `menu_navigation.menu_items_crud.duration` | Histogram | `POST/PUT/DELETE /api/menu-items`の応答時間 |
| `menu_navigation.menu_items_crud.error_count` | Counter | `/api/menu-items`のエラー件数(累積、400/403/404別) |

### ログ

構造化ログ(JSON、リクエストID付与、NFR5)として以下を記録する(Q4確定=A)。

| タイミング | レベル | 内容 |
|---|---|---|
| `/api/menu-items`作成・更新・削除の実行 | INFO | 実行者(activeRoleId)、対象(menuItemId)、操作種別 |
| `/api/menu-items`の失敗(400/403/404) | ERROR | 失敗理由、リクエストID |

パスワード・接続文字列等の機微情報はログに含めない(security-requirements.md NFR2.3)。`GET /api/menu`自体は参照系であり、個別のINFOログは出力しない(高頻度パスのためログ量を抑制する)。

### トレーシング

他ユニットと同様、W3C Trace Contextによるトレースコンテキスト伝搬に参加する。`GET /api/menu`のHTTPスパンから`PermissionEngine.canAccessScreen`呼び出しまでを1つのトレースとして追跡できるようにする。

## NFR5.2: アラート・ダッシュボード方針

- `GET /api/menu`のエラー率が一定閾値(アプリ全体共通のエラー率監視基準に従う)を超えた場合にアラートする。トップ画面・サイドバーの初期表示に直結する高頻度パスであるため、アプリ全体のエラー監視ダッシュボードに含める。
- `/api/menu-items`は低頻度の管理操作であり、専用の常時監視アラートは設定しない。失敗ログ(ERROR)はアプリケーション全体のエラーログ監視の一部として捕捉される。
