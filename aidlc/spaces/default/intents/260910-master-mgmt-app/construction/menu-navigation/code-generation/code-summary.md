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

# Code Summary — menu-navigation (U6)

## 実装内容

`backend/src/main/java/com/mastersmith/menu/`配下に、業務メニュー(N階層`MenuItem`)・管理メニュー(4項目ハードコード)の構成管理と、参照API(`GET /api/menu`)・CRUD API(`POST/PUT/DELETE /api/menu-items`、Contract Design追補)・C12内部インタフェース(`MenuStructureApi`、config-import-export向け、未実装コンシューマー)を実装した。

| ディレクトリ | 内容 |
|---|---|
| `entity/` | MenuItem(物理カラム`item_order`へのマッピング含む) |
| `repository/` | MenuItemRepository(Spring Data JPA) |
| `tree/` | AdminMenuDefinition(管理メニュー4項目のハードコード定義)、MenuTreeBuilder(木構造構築・権限フィルタ・可視性導出) |
| `service/` | MenuQueryService、MenuItemCommandService、MenuStructureApiImpl(C12契約実装) |
| `web/` | MenuController |
| `dto/` | MenuItemView、MenuItemInput、MenuResponse、MenuStructureEntry |
| `exception/` | MenuItemNotFoundException、MenuItemValidationException、MenuItemConflictException、MenuUnauthorizedException、MenuItemForbiddenException |
| (ルート) | MenuStructureApi(C12契約インタフェース) |

`backend/src/main/resources/db/migration/V3__create_menu_item.sql`: `menu_item`テーブル(物理FK制約なし)と`idx_menu_item_parent_menu_item_id`インデックス(NFR4.4のDELETE時子孫存在チェックを効率化)を追加。

## 主要な実装判断

- 管理メニュー4項目は固定`menuItemId`(`admin-business-menu-config`/`admin-user-management`/`admin-audit-log`/`admin-config-management`)を`AdminMenuDefinition`にハードコードし、`targetTableConfigId`は`null`とする(BR6.2、Q8=A、Plan Approval前提事項1)。
- `GET /api/menu`は401のみ(C3契約に403が宣言されていないため)、`/api/menu-items`は401・403を区別する(`ActiveRoleResolver`がactiveRoleIdを解決できない場合401、`canAccessScreen`がfalseの場合403、Plan Approval前提事項2)。
- `order`フィールドはSQL予約語のため物理カラム名`item_order`にマッピングし、Java/DTOフィールド名は`order`のまま維持する(Plan Approval前提事項3)。
- `parentMenuItemId`は既存ユニットと同じ「不透明な文字列参照」の慣例に従い、物理FK制約を設けない(Plan Approval前提事項4)。
- 権限判定は自前で持たず、`PermissionEngineApi.canAccessScreen`/`ConfigEngineApi.getTableConfigById`へ全面委譲する(project.md Mandated)。
- `MenuStructureApi`(C12)はconfig-import-export(U9、未実装)向けの契約所有者(menu-navigation)側の先行実装であり、config-engineの`getExportableConfigSet`/`importConfigSet`と同様のパターンを踏襲する。

## 実装中に発見・修正した不具合(オーケストレーターによる検証ディスパッチで発見)

- `MenuTreeBuilder.buildBusinessMenu`が`Collectors.groupingBy(MenuItem::getParentMenuItemId)`を使用しており、`parentMenuItemId`が`null`となるルート直下の項目で必ず`NullPointerException`(groupingByの分類関数はnullキーを許容しない)が発生する実装上のバグを検出し、`HashMap`ベースの手動グルーピング(`computeIfAbsent`)に置き換えて修正した。挙動・下流コードへの影響はない。
- `MenuTreeBuilderTest`の管理メニュー権限フィルタのテストケースでMockitoのstrict-stubbing違反(`PotentialStubbingProblem`)を検出し、他の同種テストと同じ「既定でfalseを返すスタブ+個別にtrueを返すスタブ」のパターンに揃えて修正した。

## テストカバレッジ

- テストクラス6(`MenuItemJpaTest`, `MenuTreeBuilderTest`, `MenuItemCommandServiceTest`, `MenuQueryServiceTest`, `MenuStructureApiImplTest`, `MenuControllerTest`)、テストケース49件
- 全テスト成功。`./gradlew :backend:checkstyleMain :backend:checkstyleTest :backend:compileJava :backend:compileTestJava`・`:backend:test :backend:jacocoTestCoverageVerification`(バックエンド全体、80%行カバレッジフロア充足)・`:backend:spotlessJavaCheck`(menu-navigation配下は違反なし。他ユニット(dataio等)の既存フォーマット違反は本ユニットのスコープ外として不変更)をいずれも確認済み

## 計画からの逸脱

- なし。全12ステップを計画どおり実施した(`code-generation-plan.md`のチェックボックスを参照)。
