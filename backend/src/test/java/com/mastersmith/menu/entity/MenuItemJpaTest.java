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

package com.mastersmith.menu.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** MenuItemのJPAマッピングテスト(INSERT後の読み取り往復、code-generation-plan.md Step4)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MenuItemJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsARootLevelFolderItem() {
    // entities.md: parentMenuItemId/targetTableConfigIdともnullで永続化できる(ルート直下のフォルダ項目)。
    MenuItem folder = new MenuItem(null, "業務メニュー", 1, null);

    entityManager.persist(folder);
    entityManager.flush();
    entityManager.clear();

    MenuItem reloaded = entityManager.find(MenuItem.class, folder.getMenuItemId());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getParentMenuItemId()).isNull();
    assertThat(reloaded.getLabel()).isEqualTo("業務メニュー");
    assertThat(reloaded.getOrder()).isEqualTo(1);
    assertThat(reloaded.getTargetTableConfigId()).isNull();
    assertThat(reloaded.isFolder()).isTrue();
  }

  @Test
  void savesAndReloadsALeafItemWithParentAndTargetTableConfigId() {
    // order列はSQL予約語のためitem_orderへマッピングされる(code-generation-plan.md「前提事項3」)が、
    // Javaフィールド名はorderのまま読み書きできることを確認する。
    MenuItem parent = new MenuItem(null, "商品管理", 1, null);
    entityManager.persist(parent);

    MenuItem leaf = new MenuItem(parent.getMenuItemId(), "商品マスタ", 2, "table-config-1");
    entityManager.persist(leaf);
    entityManager.flush();
    entityManager.clear();

    MenuItem reloaded = entityManager.find(MenuItem.class, leaf.getMenuItemId());
    assertThat(reloaded.getParentMenuItemId()).isEqualTo(parent.getMenuItemId());
    assertThat(reloaded.getOrder()).isEqualTo(2);
    assertThat(reloaded.getTargetTableConfigId()).isEqualTo("table-config-1");
    assertThat(reloaded.isFolder()).isFalse();
  }

  @Test
  void generatesAUniqueMenuItemIdWhenNotExplicitlyProvided() {
    MenuItem first = new MenuItem(null, "A", 1, null);
    MenuItem second = new MenuItem(null, "B", 2, null);

    assertThat(first.getMenuItemId()).isNotBlank();
    assertThat(second.getMenuItemId()).isNotBlank();
    assertThat(first.getMenuItemId()).isNotEqualTo(second.getMenuItemId());
  }

  @Test
  void updatesMutableFieldsAndPersistsTheChange() {
    // TableConfigと同様、MenuItemはCRUD対象のため主要属性にsetterを公開する(AuditLogEntryとは異なりappend-onlyではない)。
    MenuItem item = new MenuItem(null, "旧ラベル", 1, null);
    entityManager.persist(item);
    entityManager.flush();

    item.setLabel("新ラベル");
    item.setOrder(9);
    item.setTargetTableConfigId("table-config-2");
    entityManager.flush();
    entityManager.clear();

    MenuItem reloaded = entityManager.find(MenuItem.class, item.getMenuItemId());
    assertThat(reloaded.getLabel()).isEqualTo("新ラベル");
    assertThat(reloaded.getOrder()).isEqualTo(9);
    assertThat(reloaded.getTargetTableConfigId()).isEqualTo("table-config-2");
  }
}
