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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.dto.RbacImportSet.Auxiliary;
import com.mastersmith.permission.dto.RbacImportSet.Primary;
import com.mastersmith.permission.dto.RbacImportSet.Role;
import com.mastersmith.permission.dto.RbacImportSet.Scope;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link
 * RbacImportValidator}の、権限の対象の構造(必須・長さ・予約スキーマ名・補助権限のCOLUMN)の検証のテスト(昇格・主権限0件は、権限マトリクスのテストが担う)。
 */
class RbacImportValidatorStructureTest {

  private final RbacImportValidator validator =
      new RbacImportValidator(
          mock(PrimaryPermissionRepository.class), mock(AuxiliaryPermissionRepository.class));

  private List<ImportValidationError> validate(List<Primary> primary, List<Auxiliary> auxiliary) {
    return validator.validate(
        new RbacImportSet(List.of(new Role("r")), List.of(), primary, auxiliary), "actor", true);
  }

  private static Primary primary(Scope scope, PermissionLevel level) {
    return new Primary("r", scope, level);
  }

  @Test
  void reservedSchemaNamesOfTheSystemAreAccepted() {
    List<Primary> primary =
        ReservedScopes.SCREEN_SCHEMAS.values().stream()
            .map(
                name ->
                    primary(new Scope(ScopeType.SCHEMA, name, null, null), PermissionLevel.FULL))
            .toList();

    assertThat(validate(primary, List.of())).isEmpty();
  }

  @Test
  void anUnknownNameWithTheReservedPrefixIsRejected() {
    List<ImportValidationError> errors =
        validate(
            List.of(
                primary(
                    new Scope(ScopeType.SCHEMA, ReservedScopes.PREFIX + "unknown", null, null),
                    PermissionLevel.FULL)),
            List.of());

    assertThat(errors)
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactly(
            tuple(
                "primaryPermissions[0].scope.schemaName", ImportMessageKeys.RBAC_RESERVED_UNKNOWN));
  }

  @Test
  void aTableScopeWhoseSchemaLooksReservedIsNotInterpreted() {
    // 予約スキーマ名の検査は、SCHEMAの対象だけ(TABLE・COLUMNのschemaNameは、テーブルの所属で、意味を解釈しない)。
    assertThat(
            validate(
                List.of(
                    primary(
                        new Scope(ScopeType.TABLE, ReservedScopes.PREFIX + "x", "id", null),
                        PermissionLevel.READ)),
                List.of()))
        .isEmpty();
  }

  @Test
  void aBlankOrTooLongSchemaNameIsRejected() {
    List<ImportValidationError> errors =
        validate(
            List.of(
                primary(new Scope(ScopeType.SCHEMA, " ", null, null), PermissionLevel.READ),
                primary(
                    new Scope(ScopeType.SCHEMA, "s".repeat(256), null, null), PermissionLevel.READ),
                primary(
                    new Scope(ScopeType.SCHEMA, "s".repeat(255), null, null),
                    PermissionLevel.READ)),
            List.of());

    assertThat(errors)
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactly(
            tuple("primaryPermissions[0].scope.schemaName", ImportMessageKeys.FIELD_REQUIRED),
            tuple("primaryPermissions[1].scope.schemaName", ImportMessageKeys.FIELD_TOO_LONG));
    assertThat(errors.get(1).params()).containsEntry("max", 255);
  }

  @Test
  void aMissingScopeTypeOrLevelIsRejected() {
    List<ImportValidationError> errors =
        validate(
            List.of(
                primary(new Scope(null, "s", null, null), PermissionLevel.READ),
                primary(new Scope(ScopeType.SCHEMA, "s", null, null), null),
                new Primary("r", null, PermissionLevel.READ)),
            List.of());

    assertThat(errors)
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactly(
            tuple("primaryPermissions[0].scope.scopeType", ImportMessageKeys.FIELD_REQUIRED),
            tuple("primaryPermissions[1].level", ImportMessageKeys.FIELD_REQUIRED),
            tuple("primaryPermissions[2].scope.scopeType", ImportMessageKeys.FIELD_REQUIRED));
  }

  @Test
  void anAuxiliaryPermissionOnAColumnIsRejected() {
    List<ImportValidationError> errors =
        validate(
            List.of(primary(new Scope(ScopeType.SCHEMA, "s", null, null), PermissionLevel.READ)),
            List.of(
                new Auxiliary("r", new Scope(ScopeType.COLUMN, "s", "t", "c"), true, null),
                new Auxiliary("r", new Scope(ScopeType.TABLE, "s", "t", null), true, null)));

    assertThat(errors)
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactly(
            tuple("auxiliaryPermissions[0].scope", ImportMessageKeys.RBAC_AUXILIARY_COLUMN));
  }

  @Test
  void escalationIsNotJudgedForEntriesWithStructuralErrors() {
    // 構造の誤りのあるエントリは、昇格の判定の対象にしない(誤りは、構造の誤りとして、1件だけ報告する)。
    List<ImportValidationError> errors =
        validator.validate(
            new RbacImportSet(
                List.of(new Role("r")),
                List.of(),
                List.of(
                    primary(new Scope(ScopeType.SCHEMA, " ", null, null), PermissionLevel.FULL)),
                List.of()),
            "actor",
            false);

    assertThat(errors)
        .extracting(ImportValidationError::message)
        .containsExactly(ImportMessageKeys.FIELD_REQUIRED);
  }

  @Test
  void reservedScopesHelpers() {
    assertThat(ReservedScopes.isReservedSchemaName("__system__:user-management")).isTrue();
    assertThat(ReservedScopes.isReservedSchemaName("user-management")).isFalse();
    assertThat(ReservedScopes.isReservedSchemaName(null)).isFalse();
    assertThat(ReservedScopes.hasReservedPrefix("__system__:x")).isTrue();
    assertThat(ReservedScopes.hasReservedPrefix("x")).isFalse();
    assertThat(ReservedScopes.hasReservedPrefix(null)).isFalse();
  }
}
