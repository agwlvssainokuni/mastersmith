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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import java.util.UUID;

/**
 * 業務メニュー(N階層)を構成する1項目(entities.md MenuItem)。フォルダ(中間階層、{@code
 * targetTableConfigId == null})とリーフ({@code targetTableConfigId != null})の両方を同じエンティティで表す。
 *
 * <p>管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)は本エンティティとして永続化せず、{@link
 * com.mastersmith.menu.tree.AdminMenuDefinition}にハードコードする(BR6.2)。
 *
 * <p>{@code order}はSQL予約語のため、物理カラム名は{@code item_order}とする(code-generation-plan.md
 * 「前提事項3」)。Javaフィールド名はentities.md/契約どおり{@code order}のまま。
 *
 * <p>{@code parentMenuItemId}/{@code targetTableConfigId}はいずれも不透明な文字列参照であり、物理外部キー制約は設けない
 * (entities.md制約、code-generation-plan.md「前提事項4」)。存在確認はアプリケーション層({@link
 * com.mastersmith.menu.service.MenuItemCommandService}等)が行う。
 */
@Entity
@Table(
    name = "menu_item",
    indexes = @Index(name = "idx_menu_item_parent_menu_item_id", columnList = "parent_menu_item_id"))
public class MenuItem {

  @Id
  @Column(name = "menu_item_id", nullable = false, updatable = false, length = 36)
  private String menuItemId;

  @Column(name = "parent_menu_item_id", length = 36)
  private String parentMenuItemId;

  @NotBlank
  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "item_order", nullable = false)
  private int order;

  @Column(name = "target_table_config_id", length = 36)
  private String targetTableConfigId;

  protected MenuItem() {
    // JPA用
  }

  /** 新規作成用(menuItemIdはUUIDで自動採番する)。 */
  public MenuItem(String parentMenuItemId, String label, int order, String targetTableConfigId) {
    this(UUID.randomUUID().toString(), parentMenuItemId, label, order, targetTableConfigId);
  }

  public MenuItem(
      String menuItemId,
      String parentMenuItemId,
      String label,
      int order,
      String targetTableConfigId) {
    this.menuItemId = menuItemId;
    this.parentMenuItemId = parentMenuItemId;
    this.label = label;
    this.order = order;
    this.targetTableConfigId = targetTableConfigId;
  }

  public String getMenuItemId() {
    return menuItemId;
  }

  public String getParentMenuItemId() {
    return parentMenuItemId;
  }

  public void setParentMenuItemId(String parentMenuItemId) {
    this.parentMenuItemId = parentMenuItemId;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public String getTargetTableConfigId() {
    return targetTableConfigId;
  }

  public void setTargetTableConfigId(String targetTableConfigId) {
    this.targetTableConfigId = targetTableConfigId;
  }

  /** {@code targetTableConfigId}を持たない中間階層項目かどうか(BR6.3-(2)、BR6.4)。 */
  public boolean isFolder() {
    return targetTableConfigId == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MenuItem other)) {
      return false;
    }
    return Objects.equals(menuItemId, other.menuItemId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(menuItemId);
  }

  @Override
  public String toString() {
    return "MenuItem{menuItemId='%s', parentMenuItemId='%s', label='%s', order=%d, targetTableConfigId='%s'}"
        .formatted(menuItemId, parentMenuItemId, label, order, targetTableConfigId);
  }
}
