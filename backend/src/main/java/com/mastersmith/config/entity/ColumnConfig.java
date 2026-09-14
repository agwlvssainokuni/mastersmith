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

import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.model.FkReference;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.config.validation.ChoiceOrFkExclusive;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * TableConfigに属する1カラムの表示設定(entities.md ColumnConfig)。
 *
 * <p>表示名・バリデーションメッセージはi18nキーとして機械的に導出し(rules.md BR1.5, BR1.6)、テキストそのものは保持しない。{@code
 * validationRule}・{@code choiceOptions}・ {@code
 * fkReference}はJSON型カラムとして内部設定DBへ永続化する(tech-stack-decisions.md)。
 *
 * <p>必須プロパティの検証はTableConfigと同様、DB制約ではなく{@code ConfigValidator}に よるアプリケーション層のfail-fast検証(BR1.1,
 * BR1.3, BR1.4)が担う。
 */
@Entity
@Table(
    name = "column_config",
    uniqueConstraints = @UniqueConstraint(columnNames = {"table_config_id", "column_name"}))
@ChoiceOrFkExclusive
public class ColumnConfig {

  @Id
  @Column(name = "column_config_id", nullable = false, updatable = false, length = 36)
  private String columnConfigId;

  /**
   * 所属するTableConfigのID。JPAの{@code @ManyToOne}関連ではなく、単純な文字列FKとして
   * 保持する。ConfigEngineの読み取り経路(getColumnConfigs等)はtableConfigId文字列を
   * キーとして扱い、オブジェクトグラフ経由のナビゲーションを必要としないため、遅延ロード等の 複雑性を避けるための設計判断。
   */
  @NotBlank
  @Column(name = "table_config_id")
  private String tableConfigId;

  @NotBlank
  @Column(name = "column_name")
  private String columnName;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "format")
  private String format;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "editor_type")
  private EditorType editorType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "validation_rule")
  private ValidationRule validationRule;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "visibility", nullable = false)
  private Visibility visibility = Visibility.VISIBLE;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "choice_options")
  private List<ChoiceOption> choiceOptions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fk_reference")
  private FkReference fkReference;

  /**
   * 対象テーブルの主キー列かどうか(Contract Design追補C9、data-import-export/entities.md
   * CsvColumnDefinition.isPrimaryKey)。schema-introspectorが対象RDBMSのメタデータ読み取り時に
   * 判定した結果を{@link #ColumnConfig(String, String, EditorType, boolean)}経由でのみ設定でき、
   * setterは公開しない(rules.md BR1.14: writeTableConfigDraft経由の構築時にのみ設定され、
   * 手動編集・importConfigSet等の他経路からは変更不可能とする)。
   */
  @Column(name = "is_primary_key", nullable = false)
  private boolean primaryKey;

  protected ColumnConfig() {
    // JPA用
  }

  public ColumnConfig(String tableConfigId, String columnName, EditorType editorType) {
    this(tableConfigId, columnName, editorType, false);
  }

  /**
   * schema-introspector専用のwriteTableConfigDraft経由の構築コンストラクタ(BR1.14)。
   * isPrimaryKeyは他の経路(手動編集・importConfigSet)からは設定・変更できない。
   */
  public ColumnConfig(
      String tableConfigId, String columnName, EditorType editorType, boolean isPrimaryKey) {
    this.columnConfigId = UUID.randomUUID().toString();
    this.tableConfigId = tableConfigId;
    this.columnName = columnName;
    this.editorType = editorType;
    this.displayOrder = 0;
    this.visibility = Visibility.VISIBLE;
    this.validationRule = ValidationRule.empty();
    this.choiceOptions = new ArrayList<>();
    this.primaryKey = isPrimaryKey;
  }

  public String getColumnConfigId() {
    return columnConfigId;
  }

  public String getTableConfigId() {
    return tableConfigId;
  }

  public void setTableConfigId(String tableConfigId) {
    this.tableConfigId = tableConfigId;
  }

  public String getColumnName() {
    return columnName;
  }

  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public EditorType getEditorType() {
    return editorType;
  }

  public void setEditorType(EditorType editorType) {
    this.editorType = editorType;
  }

  public ValidationRule getValidationRule() {
    return validationRule;
  }

  public void setValidationRule(ValidationRule validationRule) {
    this.validationRule = validationRule;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public void setVisibility(Visibility visibility) {
    this.visibility = visibility;
  }

  public List<ChoiceOption> getChoiceOptions() {
    return choiceOptions;
  }

  public void setChoiceOptions(List<ChoiceOption> choiceOptions) {
    this.choiceOptions = choiceOptions;
  }

  public FkReference getFkReference() {
    return fkReference;
  }

  public void setFkReference(FkReference fkReference) {
    this.fkReference = fkReference;
  }

  public boolean isPrimaryKey() {
    return primaryKey;
  }

  /** BR1.5: 表示名i18nキーの機械的導出。 */
  public String labelI18nKey(String schemaName, String tableName) {
    return "table.%s.%s.%s.label".formatted(schemaName, tableName, columnName);
  }

  /** BR1.6: バリデーションメッセージi18nキーの機械的導出。 */
  public String validationMessageI18nKey(String schemaName, String tableName, String ruleType) {
    return "table.%s.%s.%s.validation.%s".formatted(schemaName, tableName, columnName, ruleType);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ColumnConfig other)) {
      return false;
    }
    return Objects.equals(columnConfigId, other.columnConfigId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(columnConfigId);
  }

  @Override
  public String toString() {
    return "ColumnConfig{columnConfigId='%s', tableConfigId='%s', columnName='%s', editorType=%s}"
        .formatted(columnConfigId, tableConfigId, columnName, editorType);
  }
}
