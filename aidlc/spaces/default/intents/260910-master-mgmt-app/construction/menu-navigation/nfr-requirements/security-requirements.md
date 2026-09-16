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

# Security Requirements — menu-navigation (U6)

`inception/requirements-analysis/requirements.md`のNFR2、`functional-design/rules.md`のBR6.3・BR6.8、および`project.md` Mandatedに基づく、menu-navigationユニットのセキュリティ要件。

## NFR2.1: 認証

`GET /api/menu`・`POST/PUT/DELETE /api/menu-items`のいずれもBearer JWT認証を必須とする(authentication-service, U5が発行するアクセストークンをそのまま検証する。本ユニット固有の認証方式は追加しない)。

## NFR2.2: 認可(サーバー側実効権限の再検証)

- `GET /api/menu`: 各メニュー項目の表示可否は、`ActiveRoleResolver`(schema-introspector・audit-loggingと共有する既存の暫定拡張点)で解決したactiveRoleIdに対し、項目種別ごとに定まるscreenKeyで`PermissionEngine.canAccessScreen`をサーバー側で呼び出して判定する(BR6.3)。クライアント側UIの出し分けだけに依存しない(`project.md` Mandated)。
- `POST/PUT/DELETE /api/menu-items`: screenKey`"config-import-export"`で`canAccessScreen`をサーバー側で再検証する(BR6.8)。拒否時は403 Forbiddenを返す。

## NFR2.3: 機微情報の非記録・非出力

- `MenuItem`エンティティ(label・order・targetTableConfigId)は業務メニュー構成の設定情報であり、機微情報(認証情報・個人情報)を含まない。observability-requirements.mdの構造化ログにそのまま記録してよい。
- CRUD操作の失敗ログ(ERROR)にも、開発者向けスタックトレースではなく、フィールド単位のエラー内容を記録する(`project.md` Mandated: エラーハンドリングの区別)。

## NFR2.4: CIセキュリティゲート

`team.md`確定事項(SAST・シークレットスキャンをマージ前のCIブロッキングチェックとして導入)は全ユニット共通であり、本ユニット固有の追加ツール選定は行わない。依存関係の脆弱性スキャンは`team.md`確定事項により今回は対象外とする。

## NFR2.5: 入力検証

`POST/PUT/DELETE /api/menu-items`のリクエストボディ(`parentMenuItemId`・`label`・`order`・`targetTableConfigId`)は境界(コントローラ層)で検証する。`parentMenuItemId`・`targetTableConfigId`は参照整合性(存在確認)も含めて検証し、不正な場合は400 Bad Request(RFC 9457)を返す(BR6.8)。

## 脅威considerations(STRIDE、簡易)

| カテゴリ | 脅威 | 対策 |
|---|---|---|
| Spoofing | JWTなしでの呼び出し・偽装トークン | Bearer JWT必須(NFR2.1)。authentication-serviceのトークン検証に委譲 |
| Tampering | `/api/menu-items`リクエストボディの改ざんによる不正なメニュー構成注入 | TLS必須(HTTPS)。NFR2.5の入力検証・NFR2.2の認可再検証 |
| Repudiation | 誰がメニューを変更したか否認される | observability-requirements.mdの構造化ログに実行者(activeRoleId)・対象(menuItemId)を記録する。AuditLogging(改ざん不可なappend-onlyログ)への新規イベント発行は行わない方針とした(Q4確定=A、将来メニュー構成変更の完全な監査証跡が必要になった場合のフォローアップ事項として記録) |
| Information Disclosure | 権限のないメニュー項目の存在を推測されるレスポンス構造 | `GET /api/menu`は権限フィルタ後の結果のみを返し、非表示項目の存在自体をレスポンスに含めない(BR6.4) |
| Denial of Service | `/api/menu-items`への大量リクエストによる過負荷 | 管理者のみが呼び出せる低頻度操作(NFR2.2の認可判定で一般利用者を排除)。専用のレート制限は本MVPスコープでは設けない |
| Elevation of Privilege | 権限のない利用者がメニュー構成を変更 | NFR2.2のサーバー側実効権限再検証。権限昇格防止自体は`project.md` Forbidden(権限管理者の明示的操作を経ない昇格禁止)がPermissionEngine側で担保する |

## Compliance

本プロジェクトに適用される法規制・業界標準の指定は`project.md`に記載がなく、該当なし。
