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

# Reliability Design — menu-navigation (U6)

`construction/menu-navigation/nfr-requirements/reliability-requirements.md`(NFR4.1〜NFR4.4)、および`nfr-design-questions.md` Q3確定に基づく、menu-navigationユニットの信頼性設計。

## 参照整合性欠損時の挙動(NFR4.1対応)

`GET /api/menu`実行時、リーフ項目の`targetTableConfigId`が指すTableConfigが存在しない場合、`performance-design.md`の手順3(a)で当該MenuItemを結果から実行時に除外する(BR6.7)。アプリ全体のfail fast(起動時検証)は行わない。

## `/api/menu-items`の失敗時挙動(NFR4.3対応)

リクエストボディ検証エラー(400)・認可拒否(403)・対象不存在(404)は、いずれもfail fastで即座にエラーを返す。自動リトライは行わない。

## DELETE時の子孫MenuItem整合性(NFR4.4対応、Q3確定)

子孫MenuItemを持つ項目に対する`DELETE /api/menu-items/{menuItemId}`は、409 Conflict(RFC 9457)で拒否し、カスケード削除は行わない(NFR4.4の既定方針)。子孫の存在確認は、削除対象の`menuItemId`を`parentMenuItemId`に持つMenuItemが1件でも存在するかをDBへ問い合わせる`EXISTS`クエリ相当で実装する(Q3確定=A、事前の全件ロードは行わない)。

```java
// DELETE /api/menu-items/{menuItemId} の概念設計
if (menuItemRepository.existsByParentMenuItemId(menuItemId)) {
    throw new ConflictException(); // 409 Conflictへマッピング(RFC 9457)
}
menuItemRepository.deleteById(menuItemId);
```

管理者は先に子を削除・付け替えてから親を削除する運用とする(データの意図しない一括消失を防ぐfail-safeな既定挙動)。

## Reliability Anti-Requirements(除外事項、`reliability-requirements.md`から継承)

- 複数管理者による`/api/menu-items`への同時変更に対する明示的な排他制御(楽観ロック等)は行わない(稀な競合は許容されるリスクとして受け入れる)。
- サーキットブレーカー・リトライ・タイムアウト設定等の耐障害性パターンは導入しない(ConfigEngine・PermissionEngineへの呼び出しは同一プロセス内であり、ネットワーク越しの外部呼び出しを持たない)。
