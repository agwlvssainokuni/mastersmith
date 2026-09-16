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

# Performance Design — menu-navigation (U6)

`construction/menu-navigation/nfr-requirements/performance-requirements.md`(NFR1.1・NFR1.2)、および`nfr-design-questions.md`の確定回答に基づく、menu-navigationユニットの性能設計。

## `GET /api/menu`の実装方式(NFR1.1対応、Q1・Q2確定)

MenuItemツリーは内部設定DBから毎回読み取り、アプリケーション内でキャッシュしない(Q2確定=A、NFR3.1の想定規模では不要と判断)。各リクエストで以下の手順を踏む。

1. `MenuItemRepository`から全MenuItem(通常は数十〜百件、NFR3.1)を1回のクエリで一括取得する(N+1クエリを避ける。`parentMenuItemId`によるフィルタは行わず、全件取得後にアプリケーション層で木構造を組み立てる)
2. アプリケーション層(Java)で自己参照外部キーから木構造を組み立てる(`tech-stack-decisions.md`確定方針)
3. リーフ項目(`targetTableConfigId != null`)ごとに、(a) ConfigEngine側の存在確認、(b) `PermissionEngine.canAccessScreen`呼び出しを行う。いずれも逐次呼び出しとする(Q1確定=A、一括APIの追加は行わない)
4. フォルダ項目の可視性を配下リーフから再帰的に導出する(BR6.4)
5. 各階層を`order`昇順でソートする(BR6.6)

```java
// GET /api/menu の概念設計(実装はCode Generationで確定)
List<MenuItem> allItems = menuItemRepository.findAll(); // 1クエリで一括取得
MenuTree tree = MenuTreeBuilder.build(allItems); // アプリケーション層で木構造化
MenuTree filtered = tree.filterByPermission(activeRoleId, configEngineApi, permissionEngineApi); // 手順3・4
```

## 応答時間予算の内訳(NFR1.1対応、コールドキャッシュ残存リスクの明記)

`nfr-requirements/performance-requirements.md` NFR1.1が既に指摘したとおり、NFR3.1の想定規模(数十〜百件)で全リーフ項目に対し逐次で(a)(b)を呼び出す場合、最大約200回の呼び出しが1リクエスト内に発生しうる。(a)はConfigEngineの`ConfigCache`インメモリインデックス参照(O(1))、(b)はPermissionEngineの`PermissionCacheConfig`(Caffeine、TTL30秒)経由で、キャッシュヒット時は無視できるコスト。コールドキャッシュ時の最悪ケース(1件あたりpermission-engine側の50ms/p95目標を要する場合)は3秒予算を超過しうる既知の残存リスクであり、Q1確定によりこの設計のまま採用する(YAGNI、実測で問題が顕在化した場合にキャッシュのプリウォームまたは一括権限判定APIの追加を検討する)。

## `POST/PUT/DELETE /api/menu-items`の応答時間予算(NFR1.2対応)

単純なCRUD操作であり、`GET /api/menu`のような多段の他コンポーネント呼び出しは発生しない(認可判定1回、targetTableConfigId存在確認1回、DELETE時は子孫存在確認1回、いずれも単発)。5秒以内(NFR1.2)を十分に満たせると判断する。

## DBコネクションプール

内部設定DB(組込みDB、team.md Q12b)専用の既存HikariCP(Spring Boot標準、他の内部設定DB依存ユニットと共有)をそのまま利用する。本ユニット固有の接続プール分離・パラメータ調整は不要。

## Performance Anti-Requirements(除外事項、`performance-requirements.md`から継承)

- MenuItemツリーのアプリケーション内キャッシュは導入しない(Q2確定=A)。
- 数千件規模のMenuItem・5階層以上の深い階層に対する性能保証は本MVPスコープの対象外とする。
