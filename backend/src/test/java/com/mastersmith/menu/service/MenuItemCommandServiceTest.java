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

import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.dto.MenuItemInput;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.exception.MenuItemConflictException;
import com.mastersmith.menu.exception.MenuItemNotFoundException;
import com.mastersmith.menu.exception.MenuItemValidationException;
import com.mastersmith.menu.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link MenuItemCommandService}の単体テスト(unit-test-instructions.md「モック/スタブ方針」: {@link
 * ConfigEngineApi}はモックし、{@link
 * MenuItemRepository}は実際のH2に対して検証する)。作成・更新の正常系とバリデーションエラー、削除の正常系と子孫存在時の{@link
 * MenuItemConflictException}(NFR4.4)を確認する(code-generation-plan.md Step 6)。
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MenuItemCommandServiceTest {

  private static final String EXISTING_TABLE_CONFIG_ID = "table-config-1";
  private static final String MISSING_TABLE_CONFIG_ID = "table-config-missing";

  @Autowired private MenuItemRepository repository;

  private ConfigEngineApi configEngineApi;
  private MenuItemCommandService service;

  @BeforeEach
  void setUp() {
    configEngineApi = mock(ConfigEngineApi.class);
    when(configEngineApi.getTableConfigById(EXISTING_TABLE_CONFIG_ID)).thenReturn(null);
    when(configEngineApi.getTableConfigById(MISSING_TABLE_CONFIG_ID))
        .thenThrow(new TableConfigNotFoundException("not found"));
    service = new MenuItemCommandService(repository, configEngineApi);
  }

  @Test
  void createsARootLevelLeafWhenInputIsValid() {
    MenuItem created =
        service.create(new MenuItemInput(null, "商品マスタ", 1, EXISTING_TABLE_CONFIG_ID));

    assertThat(created.getMenuItemId()).isNotBlank();
    assertThat(repository.findById(created.getMenuItemId())).isPresent();
  }

  @Test
  void createsAChildItemWhenParentMenuItemIdExists() {
    MenuItem parent = repository.save(new MenuItem(null, "商品管理", 1, null));

    MenuItem created =
        service.create(
            new MenuItemInput(parent.getMenuItemId(), "商品マスタ", 1, EXISTING_TABLE_CONFIG_ID));

    assertThat(created.getParentMenuItemId()).isEqualTo(parent.getMenuItemId());
  }

  @Test
  void createThrowsValidationExceptionWhenParentMenuItemIdDoesNotExist() {
    MenuItemInput input = new MenuItemInput("does-not-exist", "商品マスタ", 1, null);

    assertThatThrownBy(() -> service.create(input)).isInstanceOf(MenuItemValidationException.class);
  }

  @Test
  void createThrowsValidationExceptionWhenTargetTableConfigIdDoesNotExist() {
    MenuItemInput input = new MenuItemInput(null, "商品マスタ", 1, MISSING_TABLE_CONFIG_ID);

    assertThatThrownBy(() -> service.create(input)).isInstanceOf(MenuItemValidationException.class);
  }

  @Test
  void updatesAnExistingMenuItem() {
    MenuItem existing = repository.save(new MenuItem(null, "旧ラベル", 1, null));

    MenuItem updated =
        service.update(existing.getMenuItemId(), new MenuItemInput(null, "新ラベル", 2, null));

    assertThat(updated.getLabel()).isEqualTo("新ラベル");
    assertThat(updated.getOrder()).isEqualTo(2);
  }

  @Test
  void updateThrowsNotFoundExceptionWhenMenuItemDoesNotExist() {
    MenuItemInput input = new MenuItemInput(null, "新ラベル", 1, null);

    assertThatThrownBy(() -> service.update("does-not-exist", input))
        .isInstanceOf(MenuItemNotFoundException.class);
  }

  @Test
  void updateThrowsValidationExceptionWhenParentMenuItemIdReferencesItself() {
    MenuItem existing = repository.save(new MenuItem(null, "自己参照", 1, null));
    MenuItemInput input = new MenuItemInput(existing.getMenuItemId(), "自己参照", 1, null);

    assertThatThrownBy(() -> service.update(existing.getMenuItemId(), input))
        .isInstanceOf(MenuItemValidationException.class);
  }

  @Test
  void updateThrowsValidationExceptionWhenTargetTableConfigIdDoesNotExist() {
    MenuItem existing = repository.save(new MenuItem(null, "商品マスタ", 1, EXISTING_TABLE_CONFIG_ID));
    MenuItemInput input = new MenuItemInput(null, "商品マスタ", 1, MISSING_TABLE_CONFIG_ID);

    assertThatThrownBy(() -> service.update(existing.getMenuItemId(), input))
        .isInstanceOf(MenuItemValidationException.class);
  }

  @Test
  void deletesALeafMenuItemThatHasNoChildren() {
    MenuItem existing = repository.save(new MenuItem(null, "商品マスタ", 1, EXISTING_TABLE_CONFIG_ID));

    service.delete(existing.getMenuItemId());

    assertThat(repository.findById(existing.getMenuItemId())).isEmpty();
  }

  @Test
  void deleteThrowsNotFoundExceptionWhenMenuItemDoesNotExist() {
    assertThatThrownBy(() -> service.delete("does-not-exist"))
        .isInstanceOf(MenuItemNotFoundException.class);
  }

  @Test
  void deleteThrowsConflictExceptionWhenChildMenuItemExists() {
    // NFR4.4: 子孫MenuItemを持つ項目の削除は409相当の例外で拒否する(既定方針)。
    MenuItem parent = repository.save(new MenuItem(null, "商品管理", 1, null));
    repository.save(new MenuItem(parent.getMenuItemId(), "商品マスタ", 1, EXISTING_TABLE_CONFIG_ID));

    assertThatThrownBy(() -> service.delete(parent.getMenuItemId()))
        .isInstanceOf(MenuItemConflictException.class);
    assertThat(repository.findById(parent.getMenuItemId())).isPresent();
  }
}
