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

package com.mastersmith.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import java.util.UUID;

/**
 * 対象RDBMS上の1テーブルに対応する表示設定(entities.md TableConfig)。
 *
 * <p>表示名はi18nキー({@code table.{schemaName}.{tableName}.label})として schemaName/
 * tableNameから機械的に導出し、テキストそのものは保持しない(TranslationEntryが言語別 テキストを保持する。rules.md BR1.5)。
 *
 * <p>必須プロパティ(schemaName, tableName)の検証はJPAのDB制約ではなく、{@code
 * ConfigValidator}によるアプリケーション層のfail-fast検証(BR1.1, BR1.2)が担う。これは
 * schema-introspector等が生成した不正なドラフトを起動時・インポート時に検知するという 要件を満たすため、DBレベルでは緩く保持し、業務ロジック層で一元的に検証する設計判断による
 * (project.md Mandated: 設定定義自体の誤りは起動時・設定読込時に検知しfail fastする)。
 */
@Entity
@Table(
    name = "table_config",
    uniqueConstraints = @UniqueConstraint(columnNames = {"schema_name", "table_name"}))
public class TableConfig {

  @Id
  @Column(name = "table_config_id", nullable = false, updatable = false, length = 36)
  private String tableConfigId;

  @NotBlank
  @Column(name = "schema_name")
  private String schemaName;

  @NotBlank
  @Column(name = "table_name")
  private String tableName;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  /** 楽観ロック対象列の明示設定。nullの場合は楽観ロック非対象(BR1.7)。 */
  @Column(name = "optimistic_lock_column")
  private String optimisticLockColumn;

  protected TableConfig() {
    // JPA用
  }

  public TableConfig(String schemaName, String tableName) {
    this(UUID.randomUUID().toString(), schemaName, tableName, 0, null);
  }

  public TableConfig(
      String tableConfigId,
      String schemaName,
      String tableName,
      int displayOrder,
      String optimisticLockColumn) {
    this.tableConfigId = tableConfigId;
    this.schemaName = schemaName;
    this.tableName = tableName;
    this.displayOrder = displayOrder;
    this.optimisticLockColumn = optimisticLockColumn;
  }

  public String getTableConfigId() {
    return tableConfigId;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public void setSchemaName(String schemaName) {
    this.schemaName = schemaName;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }

  public String getOptimisticLockColumn() {
    return optimisticLockColumn;
  }

  public void setOptimisticLockColumn(String optimisticLockColumn) {
    this.optimisticLockColumn = optimisticLockColumn;
  }

  /** BR1.5: 表示名i18nキーの機械的導出。 */
  public String labelI18nKey() {
    return "table.%s.%s.label".formatted(schemaName, tableName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TableConfig other)) {
      return false;
    }
    return Objects.equals(tableConfigId, other.tableConfigId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(tableConfigId);
  }

  @Override
  public String toString() {
    return "TableConfig{tableConfigId='%s', schemaName='%s', tableName='%s'}"
        .formatted(tableConfigId, schemaName, tableName);
  }
}
