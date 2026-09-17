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

package com.mastersmith.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mastersmith.menu.dto.MenuItemView;
import com.mastersmith.menu.dto.MenuResponse;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.repository.MenuItemRepository;
import com.mastersmith.menu.tree.MenuTreeBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link MenuQueryService}の単体テスト({@link MenuItemRepository}・{@link MenuTreeBuilder}をモック)。 */
@ExtendWith(MockitoExtension.class)
class MenuQueryServiceTest {

  private static final String ACTIVE_ROLE_ID = "role-1";

  @Mock private MenuItemRepository repository;
  @Mock private MenuTreeBuilder treeBuilder;

  @Test
  void combinesBusinessMenuAndAdminMenuFromTheTreeBuilder() {
    List<MenuItem> allItems = List.of(new MenuItem("item-1", null, "商品マスタ", 1, "table-config-1"));
    MenuItemView businessItem = new MenuItemView("item-1", "商品マスタ", "table-config-1", List.of());
    MenuItemView adminItem = new MenuItemView("admin-user-management", "ユーザ管理", null, List.of());
    when(repository.findAll()).thenReturn(allItems);
    when(treeBuilder.buildBusinessMenu(allItems, ACTIVE_ROLE_ID)).thenReturn(List.of(businessItem));
    when(treeBuilder.buildAdminMenu(ACTIVE_ROLE_ID)).thenReturn(List.of(adminItem));

    MenuQueryService service = new MenuQueryService(repository, treeBuilder);
    MenuResponse response = service.getMenu(ACTIVE_ROLE_ID);

    assertThat(response.businessMenu()).containsExactly(businessItem);
    assertThat(response.adminMenu()).containsExactly(adminItem);
  }

  @Test
  void returnsEmptyMenusWhenNoPermittedItemsExist() {
    // BR6.5: 権限のあるメニューが1件もない場合も空配列としてそのまま返す。
    when(repository.findAll()).thenReturn(List.of());
    when(treeBuilder.buildBusinessMenu(List.of(), ACTIVE_ROLE_ID)).thenReturn(List.of());
    when(treeBuilder.buildAdminMenu(ACTIVE_ROLE_ID)).thenReturn(List.of());

    MenuQueryService service = new MenuQueryService(repository, treeBuilder);
    MenuResponse response = service.getMenu(ACTIVE_ROLE_ID);

    assertThat(response.businessMenu()).isEmpty();
    assertThat(response.adminMenu()).isEmpty();
  }
}
