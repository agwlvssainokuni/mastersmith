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

package com.mastersmith.permission.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PermissionResolver}の単体テスト(ConfigEngineApi・Repositoryをモック)。
 *
 * <p>rules.md BR3.4(主権限のスコープ階層解決: COLUMN→TABLE→SCHEMA)・BR3.5(補助権限のスコープ階層解決:
 * TABLE→SCHEMA)・BR3.6(全階層指定なし時の デフォルト)の権限マトリクス組み合わせケースをテーブル駆動(ATDD、実装に先立って洗い出したテストケース、team.md
 * Q4/Q5・plan Step2)で検証する。
 */
@ExtendWith(MockitoExtension.class)
class PermissionResolverTest {

  private static final String ROLE_ID = "role-1";
  private static final String TABLE_CONFIG_ID = "table-1";
  private static final String SCHEMA_NAME = "schema-1";

  @Mock private ConfigEngineApi configEngineApi;
  @Mock private PrimaryPermissionRepository primaryPermissionRepository;
  @Mock private AuxiliaryPermissionRepository auxiliaryPermissionRepository;

  private PermissionResolver resolver;

  private PermissionResolver resolver() {
    if (resolver == null) {
      resolver =
          new PermissionResolver(
              configEngineApi, primaryPermissionRepository, auxiliaryPermissionRepository);
    }
    return resolver;
  }

  // ---- BR3.4/BR3.6: 主権限のスコープ階層解決(テーブル駆動) ----

  static Stream<Arguments> primaryPermissionMatrixCases() {
    return Stream.of(
        Arguments.of(
            "COLUMN明示設定あり(最も詳細な階層が優先される)",
            PermissionLevel.FULL,
            PermissionLevel.READ,
            PermissionLevel.READ,
            PermissionLevel.FULL),
        Arguments.of(
            "TABLEのみ明示設定あり(COLUMNは指定なし)",
            null,
            PermissionLevel.READ,
            PermissionLevel.FULL,
            PermissionLevel.READ),
        Arguments.of(
            "SCHEMAのみ明示設定あり(COLUMN・TABLEは指定なし)",
            null,
            null,
            PermissionLevel.FULL,
            PermissionLevel.FULL),
        Arguments.of("すべて指定なし(BR3.6デフォルトNONE)", null, null, null, PermissionLevel.NONE));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("primaryPermissionMatrixCases")
  void resolvesPrimaryPermissionAccordingToScopeHierarchy(
      String caseName,
      PermissionLevel columnLevel,
      PermissionLevel tableLevel,
      PermissionLevel schemaLevel,
      PermissionLevel expectedLevel) {
    ColumnConfig columnConfig = new ColumnConfig(TABLE_CONFIG_ID, "price", EditorType.DECIMAL);
    String columnConfigId = columnConfig.getColumnConfigId();
    lenient()
        .when(configEngineApi.findColumnConfigById(columnConfigId))
        .thenReturn(Optional.of(columnConfig));
    lenient()
        .when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    stubPrimary(ScopeType.COLUMN, columnConfigId, columnLevel);
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, tableLevel);
    stubPrimary(ScopeType.SCHEMA, SCHEMA_NAME, schemaLevel);
    lenient()
        .when(
            auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
                ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
                ROLE_ID, ScopeType.SCHEMA, SCHEMA_NAME))
        .thenReturn(Optional.empty());

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.COLUMN, columnConfigId);

    assertThat(result.level()).isEqualTo(expectedLevel);
  }

  @Test
  void resolvesPrimaryPermissionWhenCalledDirectlyAtTableScope() {
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, PermissionLevel.FULL);
    lenient()
        .when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    lenient()
        .when(
            auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
                ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID))
        .thenReturn(Optional.empty());

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID);

    assertThat(result.level()).isEqualTo(PermissionLevel.FULL);
  }

  @Test
  void fallsBackToSchemaWhenCalledDirectlyAtTableScopeAndTableIsUnset() {
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, null);
    when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    stubPrimary(ScopeType.SCHEMA, SCHEMA_NAME, PermissionLevel.READ);
    lenient()
        .when(
            auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
                ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID))
        .thenReturn(Optional.empty());
    lenient()
        .when(
            auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
                ROLE_ID, ScopeType.SCHEMA, SCHEMA_NAME))
        .thenReturn(Optional.empty());

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID);

    assertThat(result.level()).isEqualTo(PermissionLevel.READ);
  }

  @Test
  void resolvesPrimaryPermissionWhenCalledDirectlyAtSchemaScope() {
    stubPrimary(ScopeType.SCHEMA, SCHEMA_NAME, PermissionLevel.FULL);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.SCHEMA, SCHEMA_NAME);

    assertThat(result.level()).isEqualTo(PermissionLevel.FULL);
  }

  @Test
  void schemaScopeWithNoRowDefaultsToNone() {
    stubPrimary(ScopeType.SCHEMA, SCHEMA_NAME, null);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.SCHEMA, SCHEMA_NAME);

    assertThat(result.level()).isEqualTo(PermissionLevel.NONE);
    assertThat(result.canCreate()).isFalse();
    assertThat(result.canDelete()).isFalse();
  }

  @Test
  void columnNotResolvableInConfigEngineStopsHierarchyWalkAndDefaultsToNone() {
    // scopeRefが指すColumnConfigがconfig-engine側に存在しない(データ不整合)場合、
    // COLUMN階層でのPrimaryPermission検索のみ行い、それ以上の階層探索(TABLE/SCHEMA)は
    // 打ち切ってBR3.6のデフォルトへフォールバックする。
    when(configEngineApi.findColumnConfigById("unknown-column")).thenReturn(Optional.empty());
    stubPrimary(ScopeType.COLUMN, "unknown-column", null);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.COLUMN, "unknown-column");

    assertThat(result.level()).isEqualTo(PermissionLevel.NONE);
  }

  @Test
  void tableNotResolvableInConfigEngineSkipsSchemaFallbackAndDefaultsToNone() {
    // TABLEスコープのscopeRefがconfig-engine側のTableConfigとして解決できない(データ不整合)場合、
    // SCHEMA階層への解決を諦め、BR3.6のデフォルトへフォールバックする(security-design.mdの
    // 「障害伝播方針」は内部設定DBアクセス障害を対象とし、本ケースのようなデータ不整合による
    // 解決不能はBR3.6のデフォルト経路として扱う)。
    stubPrimary(ScopeType.TABLE, "unknown-table", null);
    when(configEngineApi.getTableConfigById("unknown-table"))
        .thenThrow(new TableConfigNotFoundException("not found"));

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.TABLE, "unknown-table");

    assertThat(result.level()).isEqualTo(PermissionLevel.NONE);
  }

  // ---- BR3.5/BR3.6: 補助権限のスコープ階層解決 ----

  @Test
  void resolvesAuxiliaryPermissionExplicitlySetAtTableLevel() {
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, PermissionLevel.READ);
    lenient()
        .when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    stubAuxiliary(ScopeType.TABLE, TABLE_CONFIG_ID, true, false);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID);

    assertThat(result.canCreate()).isTrue();
    assertThat(result.canDelete()).isFalse();
  }

  @Test
  void fallsBackAuxiliaryPermissionFromTableToSchemaWhenTableFieldIsUnset() {
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, PermissionLevel.READ);
    when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    // TABLE行自体は存在するがcreateAllowed(指定なし=null)。SCHEMA側の明示値へフォールバックする。
    stubAuxiliary(ScopeType.TABLE, TABLE_CONFIG_ID, null, null);
    stubAuxiliary(ScopeType.SCHEMA, SCHEMA_NAME, true, true);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID);

    assertThat(result.canCreate()).isTrue();
    assertThat(result.canDelete()).isTrue();
  }

  @Test
  void auxiliaryPermissionDefaultsToDenyWhenNoRowIsSetAtAnyLevel() {
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, PermissionLevel.READ);
    when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    stubAuxiliary(ScopeType.TABLE, TABLE_CONFIG_ID, null, null);
    stubAuxiliary(ScopeType.SCHEMA, SCHEMA_NAME, null, null);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID);

    assertThat(result.canCreate()).isFalse();
    assertThat(result.canDelete()).isFalse();
  }

  @Test
  void auxiliaryPermissionAtColumnScopeSkipsColumnLevelAndStartsAtTheParentTable() {
    // AuxiliaryPermissionはCOLUMN階層を対象としない(entities.md)。COLUMNスコープで呼び出された
    // 場合でも、親TABLEのtableConfigIdへマップした上でTABLE→SCHEMAの順に解決する。
    ColumnConfig columnConfig = new ColumnConfig(TABLE_CONFIG_ID, "price", EditorType.DECIMAL);
    String columnConfigId = columnConfig.getColumnConfigId();
    when(configEngineApi.findColumnConfigById(columnConfigId))
        .thenReturn(Optional.of(columnConfig));
    lenient()
        .when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    stubPrimary(ScopeType.COLUMN, columnConfigId, null);
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, null);
    stubPrimary(ScopeType.SCHEMA, SCHEMA_NAME, null);
    stubAuxiliary(ScopeType.TABLE, TABLE_CONFIG_ID, true, false);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.COLUMN, columnConfigId);

    assertThat(result.canCreate()).isTrue();
    assertThat(result.canDelete()).isFalse();
  }

  @Test
  void auxiliaryPermissionFieldsResolveIndependentlyAcrossDifferentLevels() {
    // createAllowedはTABLE階層で、deleteAllowedはSCHEMA階層で、それぞれ独立に解決されうる
    // (rules.md BR3.5: createAllowed/deleteAllowedは独立に解決する)。
    stubPrimary(ScopeType.TABLE, TABLE_CONFIG_ID, PermissionLevel.READ);
    when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig(TABLE_CONFIG_ID, SCHEMA_NAME, "products", 0, null));
    stubAuxiliary(ScopeType.TABLE, TABLE_CONFIG_ID, true, null);
    stubAuxiliary(ScopeType.SCHEMA, SCHEMA_NAME, false, true);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.TABLE, TABLE_CONFIG_ID);

    assertThat(result.canCreate()).isTrue();
    assertThat(result.canDelete()).isTrue();
  }

  @Test
  void auxiliaryPermissionAtSchemaScopeOnlyChecksSchemaLevel() {
    stubPrimary(ScopeType.SCHEMA, SCHEMA_NAME, PermissionLevel.FULL);
    stubAuxiliary(ScopeType.SCHEMA, SCHEMA_NAME, true, true);

    EffectivePermission result = resolver().resolve(ROLE_ID, ScopeType.SCHEMA, SCHEMA_NAME);

    assertThat(result.canCreate()).isTrue();
    assertThat(result.canDelete()).isTrue();
  }

  private void stubPrimary(ScopeType scopeType, String scopeRef, PermissionLevel level) {
    lenient()
        .when(
            primaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
                ROLE_ID, scopeType, scopeRef))
        .thenReturn(
            level == null
                ? Optional.empty()
                : Optional.of(new PrimaryPermission(ROLE_ID, scopeType, scopeRef, level)));
  }

  private void stubAuxiliary(
      ScopeType scopeType, String scopeRef, Boolean createAllowed, Boolean deleteAllowed) {
    lenient()
        .when(
            auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
                ROLE_ID, scopeType, scopeRef))
        .thenReturn(
            Optional.of(
                new AuxiliaryPermission(
                    ROLE_ID, scopeType, scopeRef, createAllowed, deleteAllowed)));
  }
}
