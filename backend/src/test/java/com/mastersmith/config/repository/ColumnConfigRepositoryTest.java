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

package com.mastersmith.config.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link ColumnConfigRepository}の統合テスト(組込みH2)。基本CRUD・検索メソッドの動作確認。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ColumnConfigRepositoryTest {

  @Autowired private ColumnConfigRepository repository;

  @Test
  void savesAndFindsById() {
    ColumnConfig saved = repository.save(new ColumnConfig("table-1", "name", EditorType.TEXT));

    assertThat(repository.findById(saved.getColumnConfigId())).isPresent();
  }

  @Test
  void findsAllColumnsForATableConfigId() {
    repository.save(new ColumnConfig("table-1", "name", EditorType.TEXT));
    repository.save(new ColumnConfig("table-1", "price", EditorType.DECIMAL));
    repository.save(new ColumnConfig("table-2", "unrelated", EditorType.TEXT));

    List<ColumnConfig> forTable1 = repository.findByTableConfigId("table-1");

    assertThat(forTable1)
        .hasSize(2)
        .extracting(ColumnConfig::getColumnName)
        .containsExactlyInAnyOrder("name", "price");
  }

  @Test
  void findByTableConfigIdReturnsEmptyListWhenNoneExist() {
    assertThat(repository.findByTableConfigId("no-such-table")).isEmpty();
  }
}
