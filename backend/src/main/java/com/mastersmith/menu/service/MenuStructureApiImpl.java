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

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.menu.dto.MenuImportItem;
import com.mastersmith.menu.dto.MenuStructureEntry;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.repository.MenuItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MenuStructureApi}(C12契約)の実装。config-import-export(U9)向けの内部API。
 *
 * <p>取り込みは、検証({@link #validateMenuStructure})と反映({@link
 * #applyMenuStructure})に分かれる。反映は、全置換(既存を一括で削除し、入力を新しいIDで採番して追加する。挿入は{@link
 * EntityManager#persist}でJDBCのバッチにまとめる。BR9.14)で、呼び出し元のトランザクションに参加し(伝播{@code
 * MANDATORY})、自身ではコミットしない。メニューは、キャッシュを持たず、イベントも発行しないため、確定後の動作は空である。
 */
@Service
public class MenuStructureApiImpl implements MenuStructureApi {

  private final MenuItemRepository repository;

  @PersistenceContext private EntityManager entityManager;

  /**
   * @param configEngineApi 旧{@code
   *     importMenuStructure}が、遷移先のテーブルの実在の検証に用いた。取り込みの検証と反映の分割で、実在は、反映の順序(schemaが先)で満たされるため、
   *     使わなくなったが、コンストラクターのシグネチャは、既存の単体テストの構築との互換のため、残す
   */
  public MenuStructureApiImpl(MenuItemRepository repository, ConfigEngineApi configEngineApi) {
    this.repository = repository;
  }

  /** {@link EntityManager}を差し替える(単体テスト用)。 */
  public void setEntityManager(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
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
  public List<ImportValidationError> validateMenuStructure(List<MenuImportItem> items) {
    List<ImportValidationError> errors = new ArrayList<>();
    Set<String> positions = new HashSet<>();
    Map<String, MenuImportItem> byPosition = new HashMap<>();
    for (MenuImportItem item : items) {
      if (item.position() == null || item.position().isBlank()) {
        errors.add(ImportValidationError.of("", ImportMessageKeys.FIELD_REQUIRED));
        continue;
      }
      if (!positions.add(item.position())) {
        errors.add(ImportValidationError.of(item.position(), ImportMessageKeys.FIELD_DUPLICATE));
        continue;
      }
      byPosition.put(item.position(), item);
    }
    Set<String> hasChildren = new HashSet<>();
    for (MenuImportItem item : items) {
      if (item.parentPosition() != null) {
        hasChildren.add(item.parentPosition());
      }
    }
    for (MenuImportItem item : items) {
      if (item.position() == null || item.position().isBlank()) {
        continue;
      }
      validateItem(item, byPosition, hasChildren, errors);
    }
    return errors;
  }

  private static void validateItem(
      MenuImportItem item,
      Map<String, MenuImportItem> byPosition,
      Set<String> hasChildren,
      List<ImportValidationError> errors) {
    String position = item.position();
    if (item.label() == null || item.label().isBlank()) {
      errors.add(ImportValidationError.of(position + ".label", ImportMessageKeys.FIELD_REQUIRED));
    }
    if (item.order() == null) {
      errors.add(ImportValidationError.of(position + ".order", ImportMessageKeys.FIELD_REQUIRED));
    }
    if (item.parentPosition() != null && !parentChainIsValid(item, byPosition)) {
      errors.add(
          ImportValidationError.of(position + ".parent", ImportMessageKeys.MENU_PARENT_INVALID));
    }
    // 階層の規則: 遷移先のテーブルを持つ項目(リーフ)は、子を持たない(BR6.3)。
    if (item.leaf() && hasChildren.contains(position)) {
      errors.add(ImportValidationError.of(position, ImportMessageKeys.MENU_LEAF_HAS_CHILDREN));
    }
  }

  /** 親が、一覧の中に存在し、自己参照・循環がないか(親をたどって、自分に戻らず、ルートに着く)。 */
  private static boolean parentChainIsValid(
      MenuImportItem item, Map<String, MenuImportItem> byPosition) {
    Set<String> visited = new HashSet<>();
    visited.add(item.position());
    String current = item.parentPosition();
    while (current != null) {
      if (!visited.add(current)) {
        return false;
      }
      MenuImportItem parent = byPosition.get(current);
      if (parent == null) {
        return false;
      }
      current = parent.parentPosition();
    }
    return true;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ApplyResult applyMenuStructure(List<MenuImportItem> items) {
    Objects.requireNonNull(entityManager, "entityManager");
    // 検証の段階で、リーフのテーブルのIDは、schemaの反映(先に行う)の結果から解決されている。存在の確認は、ここでは、しない
    // (キャッシュは、未確定の内容を持たないため、config-engineのキャッシュでは、反映中の新しいテーブルを確認できない)。
    long existing = repository.count();
    repository.deleteAllItems();
    entityManager.flush();
    entityManager.clear();

    Map<String, String> idByPosition = new HashMap<>();
    for (MenuImportItem item : items) {
      idByPosition.put(item.position(), UUID.randomUUID().toString());
    }
    for (MenuImportItem item : items) {
      String parentId =
          item.parentPosition() == null ? null : idByPosition.get(item.parentPosition());
      String target = item.leaf() ? requireTarget(item) : null;
      entityManager.persist(
          new MenuItem(
              idByPosition.get(item.position()), parentId, item.label(), item.order(), target));
    }
    entityManager.flush();

    return new ApplyResult(
        Map.of(ImportSections.MENU, new SectionCounts(items.size(), 0, Math.toIntExact(existing))),
        PostCommit.NONE);
  }

  private static String requireTarget(MenuImportItem item) {
    if (item.targetTableConfigId() == null || item.targetTableConfigId().isBlank()) {
      throw new IllegalStateException(
          "leaf menu item has no resolved target table: position=" + item.position());
    }
    return item.targetTableConfigId();
  }
}
