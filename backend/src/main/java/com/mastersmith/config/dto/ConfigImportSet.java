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

package com.mastersmith.config.dto;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import java.util.List;

/**
 * config-import-export向けの設定一式インポート(C9契約 importConfigSetの引数型、 functional-spec.md W5)。
 *
 * <p>BR1.1〜BR1.4の検証にすべて合格した場合のみ内部設定DBへ反映する(all-or-nothing、 BR1.11)。
 */
public record ConfigImportSet(
    List<TableConfig> tableConfigs,
    List<ColumnConfig> columnConfigs,
    List<TranslationEntry> translationEntries) {

  public ConfigImportSet {
    tableConfigs = tableConfigs == null ? List.of() : List.copyOf(tableConfigs);
    columnConfigs = columnConfigs == null ? List.of() : List.copyOf(columnConfigs);
    translationEntries = translationEntries == null ? List.of() : List.copyOf(translationEntries);
  }
}
