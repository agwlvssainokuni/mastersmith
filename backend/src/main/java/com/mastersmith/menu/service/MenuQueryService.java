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

import com.mastersmith.menu.dto.MenuResponse;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.repository.MenuItemRepository;
import com.mastersmith.menu.tree.MenuTreeBuilder;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * {@code GET /api/menu}(W1)の中核処理。{@link MenuItemRepository#findAll()}(1クエリで一括取得、performance-design.md)から{@link
 * MenuTreeBuilder}を呼び出し、businessMenu・adminMenuを組み立てる。
 */
@Service
public class MenuQueryService {

  private final MenuItemRepository repository;
  private final MenuTreeBuilder treeBuilder;

  public MenuQueryService(MenuItemRepository repository, MenuTreeBuilder treeBuilder) {
    this.repository = repository;
    this.treeBuilder = treeBuilder;
  }

  /** BR6.5: 権限のあるメニューが1件もない場合も、businessMenu・adminMenuとも空配列としてそのまま返す。 */
  public MenuResponse getMenu(String activeRoleId) {
    List<MenuItem> allItems = repository.findAll();
    return new MenuResponse(
        treeBuilder.buildBusinessMenu(allItems, activeRoleId), treeBuilder.buildAdminMenu(activeRoleId));
  }
}
