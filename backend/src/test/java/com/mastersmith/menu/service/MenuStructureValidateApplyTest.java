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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.menu.dto.MenuImportItem;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.repository.MenuItemRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * menu-navigationの、取り込みの検証({@code validateMenuStructure})と反映({@code
 * applyMenuStructure})のテスト(実際の組込みH2・実際のJPA)。構造の規則(階層・親の参照・表示名)の検証、全置換と再採番、
 * 件数、確定後の動作が空であること(メニューは、キャッシュを持たない)を確認する(BR9.10・BR9.14)。
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MenuStructureValidateApplyTest {

  @Autowired private MenuItemRepository repository;
  @Autowired private EntityManager entityManager;

  private MenuStructureApiImpl api;

  @BeforeEach
  void setUp() {
    api = new MenuStructureApiImpl(repository, mock(ConfigEngineApi.class));
    api.setEntityManager(entityManager);
  }

  private static MenuImportItem folder(String position, String parent, String label, int order) {
    return new MenuImportItem(position, parent, label, order, false, null);
  }

  private static MenuImportItem leaf(
      String position, String parent, String label, int order, String tableId) {
    return new MenuImportItem(position, parent, label, order, true, tableId);
  }

  // ---- 検証 ----

  @Test
  void aValidTreeHasNoErrorsEvenWhenLeafTargetsArePendingNewTables() {
    List<MenuImportItem> items =
        List.of(
            folder("menu.items[0]", null, "f_1", 1),
            leaf("menu.items[0].children[0]", "menu.items[0]", "l_1", 1, null),
            leaf("menu.items[1]", null, "l_2", 2, "table-id"));

    assertThat(api.validateMenuStructure(items)).isEmpty();
  }

  @Test
  void collectsAllStructuralErrorsWithTheGivenPositions() {
    List<MenuImportItem> items =
        List.of(
            folder("p[0]", null, " ", 1),
            new MenuImportItem("p[1]", null, "l_1", null, true, "t"),
            leaf("p[2]", "p[1]", "l_2", 1, "t"),
            folder("p[3]", "p[missing]", "f_3", 1),
            folder("p[0]", null, "dup", 1));

    List<ImportValidationError> errors = api.validateMenuStructure(items);

    assertThat(errors)
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactlyInAnyOrder(
            tuple("p[0].label", ImportMessageKeys.FIELD_REQUIRED),
            tuple("p[1].order", ImportMessageKeys.FIELD_REQUIRED),
            tuple("p[1]", ImportMessageKeys.MENU_LEAF_HAS_CHILDREN),
            tuple("p[3].parent", ImportMessageKeys.MENU_PARENT_INVALID),
            tuple("p[0]", ImportMessageKeys.FIELD_DUPLICATE));
  }

  @Test
  void selfReferenceAndCyclesAreRejected() {
    List<MenuImportItem> items =
        List.of(
            folder("a", "a", "self", 1),
            folder("b", "c", "cycle_b", 1),
            folder("c", "b", "cycle_c", 1));

    assertThat(api.validateMenuStructure(items))
        .extracting(ImportValidationError::field)
        .containsExactlyInAnyOrder("a.parent", "b.parent", "c.parent");
  }

  @Test
  void aBlankPositionIsReportedAsRequired() {
    List<ImportValidationError> errors =
        api.validateMenuStructure(List.of(folder(" ", null, "x", 1)));

    assertThat(errors)
        .extracting(ImportValidationError::message)
        .containsExactly(ImportMessageKeys.FIELD_REQUIRED);
  }

  @Test
  void validationNeverWrites() {
    repository.save(new MenuItem(null, "keep", 1, null));

    api.validateMenuStructure(List.of(folder("x", null, "new", 1)));

    assertThat(repository.count()).isEqualTo(1);
  }

  // ---- 反映 ----

  @Test
  void replacesEverythingWithNewIdsAndPreservesTheHierarchy() {
    MenuItem oldRoot = repository.save(new MenuItem(null, "old_root", 1, null));
    repository.save(new MenuItem(oldRoot.getMenuItemId(), "old_child", 1, "table-old"));
    entityManager.flush();
    entityManager.clear();

    ApplyResult result =
        api.applyMenuStructure(
            List.of(
                folder("r", null, "new_root", 1),
                leaf("r.c0", "r", "new_child_a", 1, "table-a"),
                leaf("r.c1", "r", "new_child_b", 2, "table-b"),
                leaf("s", null, "new_leaf", 2, "table-c")));
    entityManager.flush();
    entityManager.clear();

    List<MenuItem> stored = repository.findAll();
    assertThat(stored).hasSize(4);
    assertThat(stored).extracting(MenuItem::getLabel).doesNotContain("old_root", "old_child");
    MenuItem root =
        stored.stream().filter(i -> i.getLabel().equals("new_root")).findFirst().orElseThrow();
    // 再採番: 以前のIDは復元されない(BR9.14)。
    assertThat(root.getMenuItemId()).isNotEqualTo(oldRoot.getMenuItemId());
    assertThat(root.getParentMenuItemId()).isNull();
    assertThat(root.getTargetTableConfigId()).isNull();
    assertThat(stored.stream().filter(i -> root.getMenuItemId().equals(i.getParentMenuItemId())))
        .extracting(MenuItem::getLabel)
        .containsExactlyInAnyOrder("new_child_a", "new_child_b");
    assertThat(result.sections().get(ImportSections.MENU)).isEqualTo(new SectionCounts(4, 0, 2));
  }

  @Test
  void anEmptyListDeletesEveryItem() {
    repository.save(new MenuItem(null, "gone", 1, null));
    entityManager.flush();

    ApplyResult result = api.applyMenuStructure(List.of());
    entityManager.flush();

    assertThat(repository.count()).isZero();
    assertThat(result.sections().get(ImportSections.MENU)).isEqualTo(new SectionCounts(0, 0, 1));
  }

  @Test
  void theSameLabelDeletedAndAddedInOneApplyIsAccepted() {
    repository.save(new MenuItem(null, "same", 1, null));
    entityManager.flush();

    api.applyMenuStructure(List.of(folder("x", null, "same", 1)));
    entityManager.flush();

    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void postCommitDoesNothingBecauseMenuHasNoCacheAndPublishesNoEvents() {
    ApplyResult result = api.applyMenuStructure(List.of(folder("x", null, "a", 1)));

    result.postCommit().invalidateCaches().run();
    result.postCommit().publishEvents().run();
  }

  @Test
  void aLeafWithoutAResolvedTargetIsAnInternalError() {
    assertThatThrownBy(() -> api.applyMenuStructure(List.of(leaf("x", null, "a", 1, null))))
        .isInstanceOf(IllegalStateException.class);
  }
}
