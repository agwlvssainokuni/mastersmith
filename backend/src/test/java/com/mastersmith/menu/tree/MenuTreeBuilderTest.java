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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.dto.MenuItemView;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.permission.PermissionEngineApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MenuTreeBuilder}の単体テスト(unit-test-instructions.md「対象テストファイル一覧」)。{@link ConfigEngineApi}・{@link
 * PermissionEngineApi}をモックし、BR6.3〜BR6.7・BR6.9の主要な組み合わせケースをテーブル駆動で網羅する(team.md
 * インタビューQ6の追加合格条件)。認可拒否(negative-authorization)専用ケース(team.md Q8-c)として{@link
 * #excludesALeafWhenPermissionDenied()}・{@link #excludesAnAdminMenuEntryWhenPermissionDenied()}を含む。
 */
@ExtendWith(MockitoExtension.class)
class MenuTreeBuilderTest {

  private static final String ACTIVE_ROLE_ID = "role-1";

  @Mock private ConfigEngineApi configEngineApi;
  @Mock private PermissionEngineApi permissionEngineApi;

  private MenuTreeBuilder builder() {
    return new MenuTreeBuilder(configEngineApi, permissionEngineApi);
  }

  private static MenuItem leaf(String menuItemId, String parentId, String label, int order, String tableConfigId) {
    return new MenuItem(menuItemId, parentId, label, order, tableConfigId);
  }

  private static MenuItem folder(String menuItemId, String parentId, String label, int order) {
    return new MenuItem(menuItemId, parentId, label, order, null);
  }

  @Test
  void includesAVisibleLeafWhenTableConfigExistsAndPermissionIsGranted() {
    MenuItem item = leaf("leaf-1", null, "商品マスタ", 1, "table-config-1");
    when(configEngineApi.getTableConfigById("table-config-1")).thenReturn(null);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "table-config-1")).thenReturn(true);

    List<MenuItemView> result = builder().buildBusinessMenu(List.of(item), ACTIVE_ROLE_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).menuItemId()).isEqualTo("leaf-1");
    assertThat(result.get(0).targetTableConfigId()).isEqualTo("table-config-1");
    assertThat(result.get(0).children()).isEmpty();
  }

  @Test
  void excludesALeafWhenPermissionDenied() {
    // 認可拒否(negative-authorization)専用テスト(team.md Q8-c)。
    MenuItem item = leaf("leaf-1", null, "商品マスタ", 1, "table-config-1");
    when(configEngineApi.getTableConfigById("table-config-1")).thenReturn(null);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "table-config-1")).thenReturn(false);

    List<MenuItemView> result = builder().buildBusinessMenu(List.of(item), ACTIVE_ROLE_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void excludesALeafWhenTargetTableConfigIsMissing() {
    // BR6.7: 参照先TableConfig欠損時は実行時除外(fail fastにしない)。
    MenuItem item = leaf("leaf-1", null, "商品マスタ", 1, "table-config-missing");
    when(configEngineApi.getTableConfigById("table-config-missing"))
        .thenThrow(new TableConfigNotFoundException("not found"));

    List<MenuItemView> result = builder().buildBusinessMenu(List.of(item), ACTIVE_ROLE_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void includesAFolderWhenAtLeastOneChildIsVisible() {
    // BR6.4: 配下に1件でも表示可能なリーフがあればフォルダも表示する。
    MenuItem visibleFolder = folder("folder-1", null, "商品管理", 1);
    MenuItem visibleLeaf = leaf("leaf-1", "folder-1", "商品マスタ", 1, "table-config-1");
    MenuItem hiddenLeaf = leaf("leaf-2", "folder-1", "在庫マスタ", 2, "table-config-2");
    when(configEngineApi.getTableConfigById(any())).thenReturn(null);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "table-config-1")).thenReturn(true);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "table-config-2")).thenReturn(false);

    List<MenuItemView> result =
        builder().buildBusinessMenu(List.of(visibleFolder, visibleLeaf, hiddenLeaf), ACTIVE_ROLE_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).menuItemId()).isEqualTo("folder-1");
    assertThat(result.get(0).children()).extracting(MenuItemView::menuItemId).containsExactly("leaf-1");
  }

  @Test
  void excludesAFolderWhenAllChildrenAreHidden() {
    // BR6.4: 配下が全て非表示ならフォルダ自体も非表示。
    MenuItem hiddenFolder = folder("folder-1", null, "商品管理", 1);
    MenuItem hiddenLeaf = leaf("leaf-1", "folder-1", "商品マスタ", 1, "table-config-1");
    when(configEngineApi.getTableConfigById("table-config-1")).thenReturn(null);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "table-config-1")).thenReturn(false);

    List<MenuItemView> result = builder().buildBusinessMenu(List.of(hiddenFolder, hiddenLeaf), ACTIVE_ROLE_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void excludesAFolderThatHasNoChildrenAtAll() {
    // 空のフォルダ(子を一切持たない)も、配下が全て非表示という扱いで除外する。
    MenuItem emptyFolder = folder("folder-1", null, "空フォルダ", 1);

    List<MenuItemView> result = builder().buildBusinessMenu(List.of(emptyFolder), ACTIVE_ROLE_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void sortsSiblingsByOrderAscendingIncludingDuplicateValues() {
    // BR6.6: order昇順(重複時は安定ソート、入力順を保持)。
    MenuItem third = leaf("leaf-3", null, "C", 3, "table-config-3");
    MenuItem firstA = leaf("leaf-1a", null, "A1", 1, "table-config-1a");
    MenuItem firstB = leaf("leaf-1b", null, "A2", 1, "table-config-1b");
    when(configEngineApi.getTableConfigById(any())).thenReturn(null);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), any())).thenReturn(true);

    List<MenuItemView> result =
        builder().buildBusinessMenu(List.of(third, firstA, firstB), ACTIVE_ROLE_ID);

    assertThat(result)
        .extracting(MenuItemView::menuItemId)
        .containsExactly("leaf-1a", "leaf-1b", "leaf-3");
  }

  @Test
  void buildsANestedTreeAcrossMultipleLevels() {
    // BR6.1: N階層(制限なし)。2階層のフォルダ+リーフを組み立てられることを確認する。
    MenuItem rootFolder = folder("root", null, "業務メニュー", 1);
    MenuItem midFolder = folder("mid", "root", "商品管理", 1);
    MenuItem leafItem = leaf("leaf", "mid", "商品マスタ", 1, "table-config-1");
    when(configEngineApi.getTableConfigById("table-config-1")).thenReturn(null);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "table-config-1")).thenReturn(true);

    List<MenuItemView> result =
        builder().buildBusinessMenu(List.of(rootFolder, midFolder, leafItem), ACTIVE_ROLE_ID);

    assertThat(result).hasSize(1);
    MenuItemView root = result.get(0);
    assertThat(root.children()).hasSize(1);
    MenuItemView mid = root.children().get(0);
    assertThat(mid.children()).extracting(MenuItemView::menuItemId).containsExactly("leaf");
  }

  @ParameterizedTest
  @CsvSource({
    "admin-business-menu-config, config-import-export",
    "admin-user-management, user-management",
    "admin-audit-log, audit-log",
    "admin-config-management, config-import-export"
  })
  void includesAnAdminMenuEntryWhenPermissionIsGranted(String menuItemId, String screenKey) {
    // BR6.2・BR6.3-(3)〜(5): 管理メニュー4項目それぞれの予約screenKeyでの権限判定を網羅する。
    // buildAdminMenuは4項目すべてに対してcanAccessScreenを呼び出すため(他の項目は非表示でよい)、
    // 既定はfalseとし対象screenKeyのみtrueにする(未設定の組み合わせでのstrict-stubbing不一致を避ける)。
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), any())).thenReturn(false);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, screenKey)).thenReturn(true);

    List<MenuItemView> result = builder().buildAdminMenu(ACTIVE_ROLE_ID);

    assertThat(result).extracting(MenuItemView::menuItemId).contains(menuItemId);
  }

  @Test
  void excludesAnAdminMenuEntryWhenPermissionDenied() {
    // 認可拒否(negative-authorization)専用テスト(team.md Q8-c)。
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), any())).thenReturn(false);

    List<MenuItemView> result = builder().buildAdminMenu(ACTIVE_ROLE_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void includesAllFourAdminMenuEntriesWhenFullyPermitted() {
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), any())).thenReturn(true);

    List<MenuItemView> result = builder().buildAdminMenu(ACTIVE_ROLE_ID);

    assertThat(result)
        .extracting(MenuItemView::menuItemId)
        .containsExactly(
            "admin-business-menu-config",
            "admin-user-management",
            "admin-audit-log",
            "admin-config-management");
  }
}
