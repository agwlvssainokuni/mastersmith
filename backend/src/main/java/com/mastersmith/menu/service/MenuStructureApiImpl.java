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

import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.exception.FieldError;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.menu.dto.MenuStructureEntry;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.repository.MenuItemRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MenuStructureApi}(C12契約)の実装。config-import-export(U9、本Bolt時点では未実装のコンシューマー)向けの内部API。
 *
 * <p>{@link com.mastersmith.menu.repository.MenuItemRepository}は{@code findAll}・{@code
 * findById}・{@code existsByParentMenuItemId}・{@code save}・{@code
 * deleteById}のみを宣言する(code-generation-plan.md Step 5)ため、{@code
 * importMenuStructure}の全件洗い替えは、一括削除・一括保存用のメソッドを追加せず、既存の宣言済みメソッドをループで呼び出すことで実現する。
 */
@Service
public class MenuStructureApiImpl implements MenuStructureApi {

  private final MenuItemRepository repository;
  private final ConfigEngineApi configEngineApi;

  public MenuStructureApiImpl(MenuItemRepository repository, ConfigEngineApi configEngineApi) {
    this.repository = repository;
    this.configEngineApi = configEngineApi;
  }

  @Override
  public List<MenuStructureEntry> getExportableMenuStructure() {
    return repository.findAll().stream()
        .map(
            item ->
                new MenuStructureEntry(
                    item.getMenuItemId(),
                    item.getParentMenuItemId(),
                    item.getLabel(),
                    item.getOrder(),
                    item.getTargetTableConfigId()))
        .toList();
  }

  @Override
  @Transactional
  public void importMenuStructure(List<MenuStructureEntry> items) {
    validate(items);
    for (MenuItem existing : repository.findAll()) {
      repository.deleteById(existing.getMenuItemId());
    }
    for (MenuStructureEntry item : items) {
      repository.save(
          new MenuItem(
              item.menuItemId(),
              item.parentMenuItemId(),
              item.label(),
              item.order(),
              item.targetTableConfigId()));
    }
  }

  /**
   * 設定定義自体の誤り(project.md Mandated)をfail fastで検出する。違反はすべて集約したうえで単一の例外として送出する(ConfigValidatorと同種の方針)。
   */
  private void validate(List<MenuStructureEntry> items) {
    List<FieldError> errors = new ArrayList<>();
    Set<String> menuItemIds =
        items.stream().map(MenuStructureEntry::menuItemId).collect(Collectors.toSet());
    Set<String> seen = new HashSet<>();
    for (MenuStructureEntry item : items) {
      String descriptor = "menuItem:" + item.menuItemId();
      if (item.menuItemId() == null || item.menuItemId().isBlank()) {
        errors.add(new FieldError(descriptor + ".menuItemId", "required"));
      } else if (!seen.add(item.menuItemId())) {
        errors.add(new FieldError(descriptor + ".menuItemId", "duplicateMenuItemId"));
      }
      if (item.label() == null || item.label().isBlank()) {
        errors.add(new FieldError(descriptor + ".label", "required"));
      }
      if (item.parentMenuItemId() != null) {
        if (item.parentMenuItemId().equals(item.menuItemId())) {
          errors.add(new FieldError(descriptor + ".parentMenuItemId", "selfReference"));
        } else if (!menuItemIds.contains(item.parentMenuItemId())) {
          errors.add(new FieldError(descriptor + ".parentMenuItemId", "referenceNotFound"));
        }
      }
      if (item.targetTableConfigId() != null) {
        try {
          configEngineApi.getTableConfigById(item.targetTableConfigId());
        } catch (TableConfigNotFoundException e) {
          errors.add(new FieldError(descriptor + ".targetTableConfigId", "referenceNotFound"));
        }
      }
    }
    if (!errors.isEmpty()) {
      throw new ConfigValidationException(errors);
    }
  }
}
