/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastersmith.menu.dto;

import java.util.List;

/**
 * C3契約の{@code MenuItem}スキーマ(contract-summary.md)にそのまま対応するレスポンス表現。
 *
 * <p>{@code GET /api/menu}のbusinessMenu/adminMenu配列の各ノード(children込みの木構造)と、{@code
 * POST/PUT /api/menu-items}の作成・更新結果(childrenは空配列)の両方に用いる。C3契約は{@code order}をレスポンスに含めないため
 * (作成・更新時の入力にのみ現れる)、本レコードにも{@code order}は含めない。
 *
 * @param menuItemId メニュー項目のID(管理メニューの場合は{@link
 *     com.mastersmith.menu.tree.AdminMenuDefinition}の固定ID)
 * @param label 表示名
 * @param targetTableConfigId 遷移先TableConfigのID。フォルダ・管理メニュー項目はnull
 * @param children 子項目(フォルダの場合のみ非空になりうる)。作成・更新結果では常に空配列
 */
public record MenuItemView(
    String menuItemId, String label, String targetTableConfigId, List<MenuItemView> children) {

  public MenuItemView {
    children = children == null ? List.of() : List.copyOf(children);
  }
}
