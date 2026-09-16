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

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link ConfigChangeSnapshots}の単体テスト。 */
class ConfigChangeSnapshotsTest {

  @Test
  void ofReturnsNullForNullTableConfig() {
    assertThat(ConfigChangeSnapshots.of((TableConfig) null)).isNull();
  }

  @Test
  void ofBuildsAnImmutableSnapshotOfTableConfigFields() {
    TableConfig tableConfig = new TableConfig("t1", "public", "products", 2, "updated_at");

    Map<String, Object> snapshot = ConfigChangeSnapshots.of(tableConfig);

    assertThat(snapshot)
        .containsEntry("tableConfigId", "t1")
        .containsEntry("schemaName", "public")
        .containsEntry("tableName", "products")
        .containsEntry("displayOrder", 2)
        .containsEntry("optimisticLockColumn", "updated_at");

    // 後続のsetterによる変更がスナップショットへ波及しない(fire-and-forgetイベント発行後の
    // インプレース変更からの独立、ConfigChangeSnapshotsクラスjavadoc参照)。
    tableConfig.setSchemaName("changed");
    assertThat(snapshot).containsEntry("schemaName", "public");
  }

  @Test
  void ofReturnsNullForNullColumnConfig() {
    assertThat(ConfigChangeSnapshots.of((ColumnConfig) null)).isNull();
  }

  @Test
  void ofBuildsAnImmutableSnapshotOfColumnConfigFields() {
    ColumnConfig columnConfig = new ColumnConfig("t1", "name", EditorType.TEXT, true);

    Map<String, Object> snapshot = ConfigChangeSnapshots.of(columnConfig);

    assertThat(snapshot)
        .containsEntry("columnConfigId", columnConfig.getColumnConfigId())
        .containsEntry("tableConfigId", "t1")
        .containsEntry("columnName", "name")
        .containsEntry("editorType", EditorType.TEXT)
        .containsEntry("primaryKey", true);
  }

  @Test
  void ofReturnsNullForNullTranslationEntry() {
    assertThat(ConfigChangeSnapshots.of((TranslationEntry) null)).isNull();
  }

  @Test
  void ofBuildsAnImmutableSnapshotOfTranslationEntryFields() {
    TranslationEntry translationEntry =
        new TranslationEntry("table.public.products.label", "ja", "商品");

    Map<String, Object> snapshot = ConfigChangeSnapshots.of(translationEntry);

    assertThat(snapshot)
        .containsEntry("i18nKey", "table.public.products.label")
        .containsEntry("locale", "ja")
        .containsEntry("text", "商品");

    // 後続のsetTextによる変更がスナップショットへ波及しない。
    translationEntry.setText("changed");
    assertThat(snapshot).containsEntry("text", "商品");
  }
}
