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

package com.mastersmith.config.event;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ConfigChangedEvent#beforeValue()}/{@link ConfigChangedEvent#afterValue()}に用いる
 * エンティティスナップショットを構築するユーティリティ(entities.md ConfigChangedEvent beforeValue/afterValue)。
 *
 * <p>JPA管理下のエンティティ参照をイベントペイロードとしてそのまま保持すると、同一の永続コンテキスト内で後続の{@code
 * save}/マージ操作によりインプレースに書き換わる可能性がある(JPA一次キャッシュの仕様)。そのため、発行時点の属性値をイミュータブルな{@link
 * Map}へコピーしたスナップショットとして保持し、後続の永続化操作から独立させる。
 */
public final class ConfigChangeSnapshots {

  private ConfigChangeSnapshots() {}

  /** {@code tableConfig}が{@code null}の場合は{@code null}(新規作成時のbeforeValue、BR1.13)。 */
  public static Map<String, Object> of(TableConfig tableConfig) {
    if (tableConfig == null) {
      return null;
    }
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("tableConfigId", tableConfig.getTableConfigId());
    snapshot.put("schemaName", tableConfig.getSchemaName());
    snapshot.put("tableName", tableConfig.getTableName());
    snapshot.put("displayOrder", tableConfig.getDisplayOrder());
    snapshot.put("optimisticLockColumn", tableConfig.getOptimisticLockColumn());
    return snapshot;
  }

  /** {@code columnConfig}が{@code null}の場合は{@code null}(新規作成時のbeforeValue、BR1.13)。 */
  public static Map<String, Object> of(ColumnConfig columnConfig) {
    if (columnConfig == null) {
      return null;
    }
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("columnConfigId", columnConfig.getColumnConfigId());
    snapshot.put("tableConfigId", columnConfig.getTableConfigId());
    snapshot.put("columnName", columnConfig.getColumnName());
    snapshot.put("displayOrder", columnConfig.getDisplayOrder());
    snapshot.put("format", columnConfig.getFormat());
    snapshot.put("editorType", columnConfig.getEditorType());
    snapshot.put("validationRule", columnConfig.getValidationRule());
    snapshot.put("visibility", columnConfig.getVisibility());
    snapshot.put("choiceOptions", columnConfig.getChoiceOptions());
    snapshot.put("fkReference", columnConfig.getFkReference());
    snapshot.put("primaryKey", columnConfig.isPrimaryKey());
    return snapshot;
  }

  /** {@code translationEntry}が{@code null}の場合は{@code null}(新規作成時のbeforeValue、BR1.13)。 */
  public static Map<String, Object> of(TranslationEntry translationEntry) {
    if (translationEntry == null) {
      return null;
    }
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("i18nKey", translationEntry.getI18nKey());
    snapshot.put("locale", translationEntry.getLocale());
    snapshot.put("text", translationEntry.getText());
    return snapshot;
  }
}
