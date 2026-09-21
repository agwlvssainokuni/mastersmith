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

import java.util.List;

/**
 * config-import-export向けの設定一式エクスポート(C9契約 getExportableConfigSetの戻り値型、functional-spec.md W5)。
 *
 * <p>内部設定DBから直接読んだ値の、他から変更できないスナップショットである(キャッシュ内の共有のインスタンスではない。config-engineのレビュー指摘R-07、config-import-export
 * NFR4.4)。全テーブル・カラム・翻訳(i18nキーを含み、表示名テキストそのものは、テーブル・カラムに含まない。テキストは、翻訳が保持する)を含む。
 */
public record ConfigExportSet(
    List<TableConfigSnapshot> tableConfigs,
    List<ColumnConfigSnapshot> columnConfigs,
    List<TranslationSnapshot> translationEntries) {

  public ConfigExportSet {
    tableConfigs = tableConfigs == null ? List.of() : List.copyOf(tableConfigs);
    columnConfigs = columnConfigs == null ? List.of() : List.copyOf(columnConfigs);
    translationEntries = translationEntries == null ? List.of() : List.copyOf(translationEntries);
  }
}
