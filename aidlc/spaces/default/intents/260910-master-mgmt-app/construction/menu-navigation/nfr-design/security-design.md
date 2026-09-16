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

# Security Design — menu-navigation (U6)

`construction/menu-navigation/nfr-requirements/security-requirements.md`(NFR2.1〜NFR2.5)に基づく、menu-navigationユニットのセキュリティ設計。

## 認可アーキテクチャ(NFR2.2対応)

`GET /api/menu`・`POST/PUT/DELETE /api/menu-items`のいずれも、`ActiveRoleResolver`(schema-introspector・audit-loggingと共有する既存の拡張点)でactiveRoleIdを解決したうえで、項目種別ごとに定まるscreenKeyで`PermissionEngine.canAccessScreen`を同期呼び出しする(`functional-design/rules.md` BR6.3・BR6.8)。

```java
// AuditLogController等と同様のパターン(概念設計)
// GET /api/menu: リーフ項目ごとに個別のscreenKeyで判定
if (!permissionEngineApi.canAccessScreen(activeRoleId, leaf.getTargetTableConfigId())) {
    // 当該項目を結果から除外(BR6.3、エラーにはしない)
}

// /api/menu-items: 固定screenKey "config-import-export" で判定
if (!permissionEngineApi.canAccessScreen(activeRoleId, "config-import-export")) {
    throw new AccessDeniedException(); // 403 Forbiddenへマッピング(RFC 9457)
}
```

予約`screenKey`"config-import-export"は、schema-introspectorが既に自身の画面(業務メニュー設定画面)に対して用いているものと同一であり、本ユニット側で新規のscreenKey定義は行わない(functional-design-questions.md Q1確定=A、選び直し)。

## MenuItem CRUD APIの入力検証(NFR2.5対応)

`POST/PUT/DELETE /api/menu-items`のリクエストボディは境界(コントローラ層)で検証する。

- `label`: 必須、空文字不可
- `order`: 必須、整数
- `parentMenuItemId`: 任意。指定時は既存MenuItemの存在を確認(存在しなければ400)
- `targetTableConfigId`: 任意。指定時はConfigEngine側の存在を確認(存在しなければ400)

いずれも不正な場合は400 Bad Request(RFC 9457形式のProblemDetails)で拒否する。

## 機微情報の非記録・非出力(NFR2.3対応)

`MenuItem`(label・order・targetTableConfigId)は業務メニュー構成の設定情報であり、認証情報・個人情報を含まない。`observability-design.md`の構造化ログにそのまま記録してよい。CRUD操作の失敗ログ(ERROR)も、開発者向けスタックトレースではなくフィールド単位のエラー内容(検証エラーの対象フィールド等)を記録する。

## エラーレスポンスの情報開示制御(NFR2.5対応)

すべてのエラーレスポンス(400・403・404・409)は、RFC 9457形式のProblemDetailsとし、開発者向けのスタックトレースや内部実装詳細(SQL文、内部設定DBの接続情報等)を含めない。

```yaml
# ProblemDetails例(409 Conflict、reliability-design.md NFR4.4参照)
type: about:blank
title: Conflict
status: 409
detail: 子項目を持つメニュー項目は削除できません。先に子項目を削除または付け替えてください。
```

## Security Anti-Requirements(除外事項、`security-requirements.md`から継承)

- 本ユニット固有の追加CIゲートはない。team.md既定(SAST・シークレットスキャンをマージ前のCIブロッキングチェック)をそのまま適用する。
- `/api/menu-items`への専用レート制限は本MVPスコープでは設けない(認可判定で一般利用者を排除する低頻度操作のため)。
