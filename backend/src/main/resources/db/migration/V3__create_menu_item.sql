-- Copyright 2026 agwlvssainokuni
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- menu-navigation(U6): MenuItem(entities.md)の永続化先。管理メニュー4項目は
-- MenuItemとして永続化せずアプリケーションコードにハードコードする(BR6.2)ため、
-- 本テーブルは業務メニュー(N階層)のみを保持する。
--
-- parent_menu_item_idは自己参照だが、物理FK制約は設けない(entities.mdの「不透明な
-- 文字列参照」の慣例、code-generation-plan.md「前提事項4」)。存在確認はアプリケーション
-- 層(MenuItemCommandService)で行う。
--
-- item_orderという物理カラム名を用いるのは、orderがSQL予約語のため
-- (code-generation-plan.md「前提事項3」)。Javaフィールド名・DTOフィールド名は
-- entities.md/契約どおり`order`のまま`@Column(name = "item_order")`でマッピングする。
--
-- idx_menu_item_parent_menu_item_id: DELETE時の子孫MenuItem存在確認
-- (existsByParentMenuItemId、NFR4.4)を効率化する。
CREATE TABLE menu_item (
    menu_item_id VARCHAR(36) NOT NULL,
    parent_menu_item_id VARCHAR(36),
    label VARCHAR(255) NOT NULL,
    item_order INTEGER NOT NULL,
    target_table_config_id VARCHAR(36),
    CONSTRAINT pk_menu_item PRIMARY KEY (menu_item_id)
);

CREATE INDEX idx_menu_item_parent_menu_item_id ON menu_item (parent_menu_item_id);
