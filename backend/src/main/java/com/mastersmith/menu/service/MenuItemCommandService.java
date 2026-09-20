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

import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.dto.MenuItemInput;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.exception.MenuItemConflictException;
import com.mastersmith.menu.exception.MenuItemNotFoundException;
import com.mastersmith.menu.exception.MenuItemValidationException;
import com.mastersmith.menu.repository.MenuItemRepository;
import org.springframework.stereotype.Service;

/**
 * 業務メニュー項目の作成(W2)・更新(W3)・削除(W4)を実装する(BR6.8)。認可判定(403)は{@link
 * com.mastersmith.menu.web.MenuController}が呼び出し前に行うため、本サービスは参照整合性検証とデータ操作のみを担う。
 */
@Service
public class MenuItemCommandService {

  private final MenuItemRepository repository;
  private final ConfigEngineApi configEngineApi;

  public MenuItemCommandService(MenuItemRepository repository, ConfigEngineApi configEngineApi) {
    this.repository = repository;
    this.configEngineApi = configEngineApi;
  }

  /** W2: 新規作成。 */
  public MenuItem create(MenuItemInput input) {
    validate(input, null);
    MenuItem entity =
        new MenuItem(
            input.parentMenuItemId(), input.label(), input.order(), input.targetTableConfigId());
    return repository.save(entity);
  }

  /** W3: 更新(対象存在確認、W2と同様の参照整合性検証)。 */
  public MenuItem update(String menuItemId, MenuItemInput input) {
    MenuItem existing = findExisting(menuItemId);
    validate(input, menuItemId);
    existing.setParentMenuItemId(input.parentMenuItemId());
    existing.setLabel(input.label());
    existing.setOrder(input.order());
    existing.setTargetTableConfigId(input.targetTableConfigId());
    return repository.save(existing);
  }

  /** W4: 削除(対象存在確認、子孫存在時はNFR4.4の既定方針により409で拒否)。 */
  public void delete(String menuItemId) {
    findExisting(menuItemId);
    if (repository.existsByParentMenuItemId(menuItemId)) {
      throw new MenuItemConflictException(
          "MenuItem has child item(s), delete is rejected: menuItemId=" + menuItemId);
    }
    repository.deleteById(menuItemId);
  }

  private MenuItem findExisting(String menuItemId) {
    return repository
        .findById(menuItemId)
        .orElseThrow(
            () -> new MenuItemNotFoundException("MenuItem not found: menuItemId=" + menuItemId));
  }

  /**
   * entities.md制約(参照整合性)の検証。{@code label}必須・{@code order}必須はBean Validation({@code
   * MenuItemInput}の注釈)がコントローラ境界で担うため、ここでは検証しない。
   */
  private void validate(MenuItemInput input, String selfMenuItemId) {
    String parentMenuItemId = input.parentMenuItemId();
    if (parentMenuItemId != null) {
      if (parentMenuItemId.equals(selfMenuItemId)) {
        // 木構造の循環を防ぐための防御的な追加検証(BR6.8には明記されないが、MenuTreeBuilderが
        // 自己参照によるループを組み立てないための必須条件)。
        throw new MenuItemValidationException(
            "parentMenuItemId must not reference itself: menuItemId=" + selfMenuItemId);
      }
      if (repository.findById(parentMenuItemId).isEmpty()) {
        throw new MenuItemValidationException(
            "parentMenuItemId does not exist: parentMenuItemId=" + parentMenuItemId);
      }
    }
    String targetTableConfigId = input.targetTableConfigId();
    if (targetTableConfigId != null) {
      try {
        configEngineApi.getTableConfigById(targetTableConfigId);
      } catch (TableConfigNotFoundException e) {
        throw new MenuItemValidationException(
            "targetTableConfigId does not exist: targetTableConfigId=" + targetTableConfigId);
      }
    }
  }
}
