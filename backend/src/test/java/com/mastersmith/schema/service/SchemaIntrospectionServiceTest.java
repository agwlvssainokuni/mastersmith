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

package com.mastersmith.schema.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mastersmith.config.dto.TableConfigDraft;
import com.mastersmith.config.rdbms.RdbmsDialect;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.schema.dto.RdbmsColumnMetadata;
import com.mastersmith.schema.dto.RdbmsTableMetadata;
import com.mastersmith.schema.dto.SchemaIntrospectionRequest;
import com.mastersmith.schema.dto.SchemaIntrospectionResult;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import com.mastersmith.schema.rdbms.RdbmsMetadataReader;
import com.mastersmith.schema.rdbms.RdbmsSchemaSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SchemaIntrospectionService}の単体テスト(BR2.5, BR2.9, BR2.10)。{@link RdbmsMetadataReader}・{@link
 * ConfigEngineApi}をモックして結合を切り離す。
 */
@ExtendWith(MockitoExtension.class)
class SchemaIntrospectionServiceTest {

  @Mock private RdbmsMetadataReader metadataReader;
  @Mock private ConfigEngineApi configEngineApi;

  private SchemaIntrospectionService service;

  @BeforeEach
  void setUp() {
    service =
        new SchemaIntrospectionService(metadataReader, configEngineApi, new SimpleMeterRegistry());
  }

  @Test
  void convertsReadMetadataToDraftAndReturnsGeneratedIds() {
    RdbmsTableMetadata items =
        new RdbmsTableMetadata(
            "shop",
            "items",
            List.of(
                new RdbmsColumnMetadata("sku", "varchar(50)", true, false),
                new RdbmsColumnMetadata("name", "varchar(100)", false, true)));
    when(metadataReader.readSchema("shop", List.of()))
        .thenReturn(new RdbmsSchemaSnapshot(RdbmsDialect.POSTGRESQL, List.of(items)));
    when(configEngineApi.writeTableConfigDraft(any())).thenReturn(List.of("table-config-1"));

    SchemaIntrospectionResult result =
        service.introspect(new SchemaIntrospectionRequest("shop", null));

    assertThat(result.generatedTableConfigIds()).containsExactly("table-config-1");
    ArgumentCaptor<TableConfigDraft> draftCaptor = ArgumentCaptor.forClass(TableConfigDraft.class);
    verify(configEngineApi).writeTableConfigDraft(draftCaptor.capture());
    TableConfigDraft draft = draftCaptor.getValue();
    assertThat(draft.dialect()).isEqualTo(RdbmsDialect.POSTGRESQL);
    assertThat(draft.tables()).hasSize(1);
    assertThat(draft.tables().get(0).schemaName()).isEqualTo("shop");
    assertThat(draft.tables().get(0).tableName()).isEqualTo("items");
    assertThat(draft.tables().get(0).columns()).hasSize(2);
    assertThat(draft.tables().get(0).columns().get(0).columnName()).isEqualTo("sku");
    assertThat(draft.tables().get(0).columns().get(0).rawTypeName()).isEqualTo("varchar(50)");
    assertThat(draft.tables().get(0).columns().get(0).isPrimaryKey()).isTrue();
  }

  @Test
  void doesNotCallWriteTableConfigDraftWhenMetadataReadFails() {
    when(metadataReader.readSchema("shop", List.of()))
        .thenThrow(new SchemaIntrospectionException("connection failed"));

    assertThatThrownBy(() -> service.introspect(new SchemaIntrospectionRequest("shop", null)))
        .isInstanceOf(SchemaIntrospectionException.class);

    verifyNoInteractions(configEngineApi);
  }

  @Test
  void passesExplicitTableNamesThroughToTheReaderAndStillWritesOnceWhenEmpty() {
    when(metadataReader.readSchema("shop", List.of("items")))
        .thenReturn(new RdbmsSchemaSnapshot(RdbmsDialect.MYSQL, List.of()));
    when(configEngineApi.writeTableConfigDraft(any())).thenReturn(List.of());

    SchemaIntrospectionResult result =
        service.introspect(new SchemaIntrospectionRequest("shop", List.of("items")));

    verify(metadataReader).readSchema("shop", List.of("items"));
    verify(configEngineApi).writeTableConfigDraft(any());
    assertThat(result.generatedTableConfigIds()).isEmpty();
  }
}
