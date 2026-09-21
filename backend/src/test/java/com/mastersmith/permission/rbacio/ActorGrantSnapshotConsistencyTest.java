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

package com.mastersmith.permission.rbacio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.dto.RbacImportSet.Scope;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.resolver.PermissionResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 取り込みの昇格の判定が用いる{@link ActorGrantSnapshot}(メモリ上の解決)が、既存の{@link
 * PermissionResolver}(問い合わせでの解決。rules.md BR3.4・BR3.5・BR3.6)と、
 * 既存のテーブル・カラムのすべての階層・割当の組み合わせで、同じ実効権限になることの確認(解決の規則を、二重に持つことによる食い違いの防止)。
 */
class ActorGrantSnapshotConsistencyTest {

  private static final String ACTOR = "actor-role";
  private static final String SCHEMA = "schema_0001";
  private static final String TABLE_ID = "table-id-0001";
  private static final String COLUMN_ID = "column-id-0001";

  private record Grants(
      String label, List<PrimaryPermission> primary, List<AuxiliaryPermission> auxiliary) {
    @Override
    public String toString() {
      return label;
    }
  }

  private static final PermissionLevel[] LEVELS = {
    null, PermissionLevel.FULL, PermissionLevel.READ, PermissionLevel.NONE
  };
  private static final Boolean[] FLAGS = {null, true, false};

  static Stream<Arguments> allCombinations() {
    List<Arguments> cases = new ArrayList<>();
    for (PermissionLevel schemaLevel : LEVELS) {
      for (PermissionLevel tableLevel : LEVELS) {
        for (PermissionLevel columnLevel : LEVELS) {
          for (Boolean schemaCreate : FLAGS) {
            for (Boolean tableCreate : FLAGS) {
              for (Boolean tableDelete : FLAGS) {
                List<PrimaryPermission> primary = new ArrayList<>();
                addPrimary(primary, ScopeType.SCHEMA, SCHEMA, schemaLevel);
                addPrimary(primary, ScopeType.TABLE, TABLE_ID, tableLevel);
                addPrimary(primary, ScopeType.COLUMN, COLUMN_ID, columnLevel);
                List<AuxiliaryPermission> auxiliary = new ArrayList<>();
                if (schemaCreate != null) {
                  auxiliary.add(
                      new AuxiliaryPermission(ACTOR, ScopeType.SCHEMA, SCHEMA, schemaCreate, null));
                }
                if (tableCreate != null || tableDelete != null) {
                  auxiliary.add(
                      new AuxiliaryPermission(
                          ACTOR, ScopeType.TABLE, TABLE_ID, tableCreate, tableDelete));
                }
                cases.add(Arguments.of(new Grants("grants", primary, auxiliary)));
              }
            }
          }
        }
      }
    }
    return cases.stream();
  }

  private static void addPrimary(
      List<PrimaryPermission> rows, ScopeType type, String ref, PermissionLevel level) {
    if (level != null) {
      rows.add(new PrimaryPermission(ACTOR, type, ref, level));
    }
  }

  @ParameterizedTest
  @MethodSource("allCombinations")
  void inMemoryResolutionAgreesWithTheExistingResolverForExistingScopes(Grants grants) {
    ConfigEngineApi configEngine = mock(ConfigEngineApi.class);
    when(configEngine.findColumnConfigById(COLUMN_ID))
        .thenReturn(Optional.of(new ColumnConfig(TABLE_ID, "c", EditorType.TEXT)));
    when(configEngine.getTableConfigById(TABLE_ID))
        .thenReturn(new TableConfig(TABLE_ID, SCHEMA, "t", 0, null));
    PrimaryPermissionRepository primaryRepository = mock(PrimaryPermissionRepository.class);
    AuxiliaryPermissionRepository auxiliaryRepository = mock(AuxiliaryPermissionRepository.class);
    when(primaryRepository.findByRoleId(ACTOR)).thenReturn(grants.primary());
    when(auxiliaryRepository.findByRoleId(ACTOR)).thenReturn(grants.auxiliary());
    when(primaryRepository.findByRoleIdAndScopeTypeAndScopeRef(anyString(), any(), anyString()))
        .thenAnswer(
            invocation ->
                grants.primary().stream()
                    .filter(
                        r ->
                            r.getScopeType() == invocation.getArgument(1)
                                && r.getScopeRef().equals(invocation.getArgument(2)))
                    .findFirst());
    when(auxiliaryRepository.findByRoleIdAndScopeTypeAndScopeRef(anyString(), any(), anyString()))
        .thenAnswer(
            invocation ->
                grants.auxiliary().stream()
                    .filter(
                        r ->
                            r.getScopeType() == invocation.getArgument(1)
                                && r.getScopeRef().equals(invocation.getArgument(2)))
                    .findFirst());
    PermissionResolver resolver =
        new PermissionResolver(configEngine, primaryRepository, auxiliaryRepository);
    ActorGrantSnapshot snapshot =
        ActorGrantSnapshot.load(ACTOR, primaryRepository, auxiliaryRepository);

    Object[][] scopes = {
      {ScopeType.SCHEMA, SCHEMA, new Scope(ScopeType.SCHEMA, SCHEMA, null, null)},
      {ScopeType.TABLE, TABLE_ID, new Scope(ScopeType.TABLE, SCHEMA, TABLE_ID, null)},
      {ScopeType.COLUMN, COLUMN_ID, new Scope(ScopeType.COLUMN, SCHEMA, TABLE_ID, COLUMN_ID)},
    };
    for (Object[] scope : scopes) {
      EffectivePermission expected =
          resolver.resolve(ACTOR, (ScopeType) scope[0], (String) scope[1]);
      Scope importScope = (Scope) scope[2];

      assertThat(snapshot.effectiveLevel(importScope)).isEqualTo(expected.level());
      // 補助権限は、TABLE→SCHEMA(COLUMNを対象としない)。COLUMNの起点でも、TABLEから解決される。
      assertThat(snapshot.canCreate(importScope)).isEqualTo(expected.canCreate());
      assertThat(snapshot.canDelete(importScope)).isEqualTo(expected.canDelete());
    }
  }

  @org.junit.jupiter.api.Test
  void anEmptySnapshotDeniesEverythingFailClosed() {
    ActorGrantSnapshot empty = ActorGrantSnapshot.empty();
    Scope scope = new Scope(ScopeType.COLUMN, SCHEMA, TABLE_ID, COLUMN_ID);

    assertThat(empty.effectiveLevel(scope)).isEqualTo(PermissionLevel.NONE);
    assertThat(empty.canCreate(scope)).isFalse();
    assertThat(empty.canDelete(scope)).isFalse();
  }
}
