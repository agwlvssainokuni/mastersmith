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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.dto.MenuStructureEntry;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.repository.MenuItemRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link MenuStructureApiImpl}(C12契約)の単体テスト。{@link ConfigEngineApi}はモックし、{@link
 * MenuItemRepository}は実際のH2に対して検証する(unit-test-instructions.mdの方針をMenuItemCommandServiceTestと同様に適用)。
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MenuStructureApiImplTest {

  private static final String EXISTING_TABLE_CONFIG_ID = "table-config-1";
  private static final String MISSING_TABLE_CONFIG_ID = "table-config-missing";

  @Autowired private MenuItemRepository repository;

  private ConfigEngineApi configEngineApi;
  private MenuStructureApiImpl api;

  @BeforeEach
  void setUp() {
    configEngineApi = mock(ConfigEngineApi.class);
    when(configEngineApi.getTableConfigById(EXISTING_TABLE_CONFIG_ID)).thenReturn(null);
    when(configEngineApi.getTableConfigById(MISSING_TABLE_CONFIG_ID))
        .thenThrow(new TableConfigNotFoundException("not found"));
    api = new MenuStructureApiImpl(repository, configEngineApi);
  }

  @Test
  void getExportableMenuStructureReturnsAllPersistedItemsAsFlatEntries() {
    MenuItem parent = repository.save(new MenuItem(null, "商品管理", 1, null));
    repository.save(new MenuItem(parent.getMenuItemId(), "商品マスタ", 1, EXISTING_TABLE_CONFIG_ID));

    List<MenuStructureEntry> exported = api.getExportableMenuStructure();

    assertThat(exported).hasSize(2);
    assertThat(exported)
        .extracting(MenuStructureEntry::menuItemId)
        .contains(parent.getMenuItemId());
  }

  @Test
  void importMenuStructureReplacesAllExistingItems() {
    repository.save(new MenuItem(null, "旧項目", 1, null));

    List<MenuStructureEntry> newItems =
        List.of(new MenuStructureEntry("new-1", null, "新項目", 1, EXISTING_TABLE_CONFIG_ID));

    api.importMenuStructure(newItems);

    List<MenuItem> all = repository.findAll();
    assertThat(all).extracting(MenuItem::getMenuItemId).containsExactly("new-1");
  }

  @Test
  void importMenuStructureThrowsConfigValidationExceptionWhenLabelIsBlank() {
    List<MenuStructureEntry> items = List.of(new MenuStructureEntry("item-1", null, " ", 1, null));

    assertThatThrownBy(() -> api.importMenuStructure(items))
        .isInstanceOf(ConfigValidationException.class);
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void importMenuStructureThrowsConfigValidationExceptionWhenParentReferenceIsMissing() {
    List<MenuStructureEntry> items =
        List.of(new MenuStructureEntry("item-1", "does-not-exist", "商品マスタ", 1, null));

    assertThatThrownBy(() -> api.importMenuStructure(items))
        .isInstanceOf(ConfigValidationException.class);
  }

  @Test
  void importMenuStructureThrowsConfigValidationExceptionWhenTargetTableConfigIdDoesNotExist() {
    List<MenuStructureEntry> items =
        List.of(new MenuStructureEntry("item-1", null, "商品マスタ", 1, MISSING_TABLE_CONFIG_ID));

    assertThatThrownBy(() -> api.importMenuStructure(items))
        .isInstanceOf(ConfigValidationException.class);
  }

  @Test
  void importMenuStructureThrowsConfigValidationExceptionOnDuplicateMenuItemIds() {
    List<MenuStructureEntry> items =
        List.of(
            new MenuStructureEntry("item-1", null, "A", 1, null),
            new MenuStructureEntry("item-1", null, "B", 2, null));

    assertThatThrownBy(() -> api.importMenuStructure(items))
        .isInstanceOf(ConfigValidationException.class);
  }

  @Test
  void importMenuStructurePreservesExistingDataWhenValidationFailsAtomically() {
    MenuItem existing = repository.save(new MenuItem(null, "既存項目", 1, null));
    List<MenuStructureEntry> invalidItems =
        List.of(new MenuStructureEntry("item-1", null, "", 1, null));

    assertThatThrownBy(() -> api.importMenuStructure(invalidItems))
        .isInstanceOf(ConfigValidationException.class);

    assertThat(repository.findById(existing.getMenuItemId())).isPresent();
  }
}
