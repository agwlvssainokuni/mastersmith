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

package com.mastersmith.menu.tree;

import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.dto.MenuItemView;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.permission.PermissionEngineApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 全MenuItem(フラットな一覧)から木構造を組み立て、権限フィルタ・可視性導出を適用する(functional-spec.md W1手順3〜7、performance-design.md
 * 「W1の実装方式」)。
 *
 * <p>リーフ項目({@code targetTableConfigId != null})は、(a) ConfigEngine側の存在確認(BR6.7)、(b) {@code
 * PermissionEngine.canAccessScreen}(BR6.3-(1))の順に判定し、いずれかを満たさなければ結果から除外する。フォルダ項目({@code
 * targetTableConfigId == null})自体への個別の権限判定は行わず、配下(子孫)の可視性から再帰的に導出する(BR6.4: 配下に1件でも表示可能な項目があれば表示、全て非表示なら除外)。各階層は{@code
 * order}昇順でソートする(BR6.6、重複時は安定ソートに委ねる)。
 *
 * <p>管理メニュー4項目({@link AdminMenuDefinition})は、対応する予約screenKeyで{@code canAccessScreen}を呼び出しtrueの項目のみを返す(BR6.2・BR6.3-(3)〜(5))。
 */
@Component
public class MenuTreeBuilder {

  private final ConfigEngineApi configEngineApi;
  private final PermissionEngineApi permissionEngineApi;

  public MenuTreeBuilder(ConfigEngineApi configEngineApi, PermissionEngineApi permissionEngineApi) {
    this.configEngineApi = configEngineApi;
    this.permissionEngineApi = permissionEngineApi;
  }

  /** businessMenu(BR6.1)を組み立てる。{@code allItems}は{@link com.mastersmith.menu.repository.MenuItemRepository#findAll()}の結果を渡す。 */
  public List<MenuItemView> buildBusinessMenu(List<MenuItem> allItems, String activeRoleId) {
    // ルート直下の項目はparentMenuItemId == nullのため、null keyを許容しないCollectors.groupingByではなく
    // (null keyをclassifierが返すとNullPointerExceptionになる)、null keyを扱えるHashMapを直接組み立てる。
    Map<String, List<MenuItem>> childrenByParent = new HashMap<>();
    for (MenuItem item : allItems) {
      childrenByParent.computeIfAbsent(item.getParentMenuItemId(), key -> new ArrayList<>()).add(item);
    }
    List<MenuItem> roots = childrenByParent.getOrDefault(null, List.of());
    return buildVisibleSiblings(roots, childrenByParent, activeRoleId);
  }

  /** adminMenu(BR6.2)を組み立てる。 */
  public List<MenuItemView> buildAdminMenu(String activeRoleId) {
    return AdminMenuDefinition.ENTRIES.stream()
        .filter(entry -> permissionEngineApi.canAccessScreen(activeRoleId, entry.screenKey()))
        .map(entry -> new MenuItemView(entry.menuItemId(), entry.label(), null, List.of()))
        .toList();
  }

  private List<MenuItemView> buildVisibleSiblings(
      List<MenuItem> siblings, Map<String, List<MenuItem>> childrenByParent, String activeRoleId) {
    return siblings.stream()
        // BR6.6: order昇順。Stream#sortedは安定ソート(重複時の同点順序は入力順を保持)。
        .sorted(Comparator.comparingInt(MenuItem::getOrder))
        .map(item -> buildVisibleNode(item, childrenByParent, activeRoleId))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<MenuItemView> buildVisibleNode(
      MenuItem item, Map<String, List<MenuItem>> childrenByParent, String activeRoleId) {
    if (!item.isFolder()) {
      return buildVisibleLeaf(item, activeRoleId);
    }
    List<MenuItem> children = childrenByParent.getOrDefault(item.getMenuItemId(), List.of());
    List<MenuItemView> visibleChildren = buildVisibleSiblings(children, childrenByParent, activeRoleId);
    if (visibleChildren.isEmpty()) {
      // BR6.4: 配下が全て非表示(子を持たない場合を含む)ならフォルダ自体も非表示。
      return Optional.empty();
    }
    return Optional.of(new MenuItemView(item.getMenuItemId(), item.getLabel(), null, visibleChildren));
  }

  private Optional<MenuItemView> buildVisibleLeaf(MenuItem item, String activeRoleId) {
    if (!targetTableConfigExists(item.getTargetTableConfigId())) {
      // BR6.7: 参照先TableConfig欠損時は実行時除外(エラーにしない)。
      return Optional.empty();
    }
    if (!permissionEngineApi.canAccessScreen(activeRoleId, item.getTargetTableConfigId())) {
      // BR6.3-(1): リーフ項目はtargetTableConfigIdをscreenKeyとして権限判定する。
      return Optional.empty();
    }
    return Optional.of(
        new MenuItemView(item.getMenuItemId(), item.getLabel(), item.getTargetTableConfigId(), List.of()));
  }

  private boolean targetTableConfigExists(String tableConfigId) {
    try {
      configEngineApi.getTableConfigById(tableConfigId);
      return true;
    } catch (TableConfigNotFoundException e) {
      return false;
    }
  }
}
