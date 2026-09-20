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

package com.mastersmith.dataio.dto;

import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.model.ValidationRule;

/**
 * CSVの列とconfig-engineのColumnConfigを対応付ける、エクスポート/インポート処理内部で組み立てる 一時的な列定義(entities.md
 * CsvColumnDefinition)。config-engineの{@code ColumnConfig} ({@code getColumnConfigs})から導出し、永続化しない。
 *
 * @param columnName CSVヘッダーおよびDB上のカラム名(ColumnConfig.columnNameと同一)
 * @param editorType 型変換・バリデーションの基準とするエディタ種別
 * @param validationRule config-engineのColumnConfig.validationRuleをそのまま引き継ぐ(BR8.5)
 * @param visibility エクスポート対象列の絞り込みに用いる(BR8.2)
 * @param isPrimaryKey 対象テーブルの主キー列かどうか(インポート時のINSERT/UPDATE判定、BR8.3)
 */
public record CsvColumnDefinition(
    String columnName,
    EditorType editorType,
    ValidationRule validationRule,
    Visibility visibility,
    boolean isPrimaryKey) {

  public CsvColumnDefinition {
    if (columnName == null || columnName.isBlank()) {
      throw new IllegalArgumentException("columnName must not be blank");
    }
    if (editorType == null) {
      throw new IllegalArgumentException("editorType must not be null");
    }
    validationRule = validationRule == null ? ValidationRule.empty() : validationRule;
    visibility = visibility == null ? Visibility.VISIBLE : visibility;
  }
}
