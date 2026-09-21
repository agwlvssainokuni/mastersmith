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

package com.mastersmith.configio.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.document.ConfigDocument.AuxiliaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.ColumnEntry;
import com.mastersmith.configio.document.ConfigDocument.FkRef;
import com.mastersmith.configio.document.ConfigDocument.GroupEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuSection;
import com.mastersmith.configio.document.ConfigDocument.PermissionScope;
import com.mastersmith.configio.document.ConfigDocument.PrimaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.RbacSection;
import com.mastersmith.configio.document.ConfigDocument.RoleEntry;
import com.mastersmith.configio.document.ConfigDocument.SchemaSection;
import com.mastersmith.configio.document.ConfigDocument.TableEntry;
import com.mastersmith.configio.document.ConfigDocument.TableRef;
import com.mastersmith.configio.parser.ImportErrorCollector;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * {@link ReferenceValidator}のテスト(BR9.13):
 * セクションをまたぐ参照が、ファイルの中で解決できること。1つの参照の種類につき、解決できる場合・できない場合を確認する。
 */
class ReferenceValidatorTest {

  private final ReferenceValidator validator = new ReferenceValidator();
  private static final Predicate<String> RESERVED = name -> name.startsWith("__system__:");

  private static ColumnEntry col(String name) {
    return new ColumnEntry(name, 0, null, "text", Map.of(), "visible", false, List.of(), null);
  }

  private static ColumnEntry fkCol(String name, FkRef fk) {
    return new ColumnEntry(name, 0, null, "select", Map.of(), "visible", false, List.of(), fk);
  }

  private static TableEntry table(String schema, String name, String lock, ColumnEntry... columns) {
    return new TableEntry(schema, name, 0, lock, List.of(columns));
  }

  private static ConfigDocument doc(
      List<TableEntry> tables, List<MenuEntry> menu, RbacSection rbac) {
    return new ConfigDocument(
        1, null, null, new SchemaSection(tables, List.of()), new MenuSection(menu), rbac);
  }

  private static RbacSection rbac(
      List<String> roles,
      List<GroupEntry> groups,
      List<PrimaryPermissionEntry> primary,
      List<AuxiliaryPermissionEntry> auxiliary) {
    return new RbacSection(roles.stream().map(RoleEntry::new).toList(), groups, primary, auxiliary);
  }

  private ImportErrorCollector run(ConfigDocument document) {
    ImportErrorCollector errors = new ImportErrorCollector();
    validator.validate(document, RESERVED, errors);
    return errors;
  }

  private static List<String> fields(ImportErrorCollector errors) {
    return errors.errors().stream().map(ImportValidationError::field).toList();
  }

  @Test
  void aFullyConsistentGeneratedDocumentHasNoReferenceErrors() {
    assertThat(run(ConfigDocumentFactory.mechanical(Spec.small())).hasErrors()).isFalse();
    assertThat(run(ConfigDocumentFactory.productProfile()).hasErrors()).isFalse();
    assertThat(run(ConfigDocumentFactory.libraryProfile()).hasErrors()).isFalse();
  }

  @Test
  void menuTargetMustBeATableInTheFile() {
    ConfigDocument document =
        doc(
            List.of(table("s_1", "t_1", null)),
            List.of(
                new MenuEntry("ok", 1, new TableRef("s_1", "t_1"), null),
                new MenuEntry(
                    "folder",
                    2,
                    null,
                    List.of(new MenuEntry("bad", 1, new TableRef("s_1", "t_missing"), null)))),
            rbac(List.of(), List.of(), List.of(), List.of()));

    ImportErrorCollector errors = run(document);

    assertThat(errors.errors())
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactly(
            tuple("menu.items[1].children[0].targetTable", ImportMessageKeys.REFERENCE_NOT_FOUND));
  }

  @Test
  void permissionScopesMustResolveInTheFileExceptReservedSchemaNames() {
    List<PrimaryPermissionEntry> primary =
        List.of(
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.SCHEMA, "s_1", null, null),
                PermissionLevel.READ),
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.SCHEMA, "s_missing", null, null),
                PermissionLevel.READ),
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.SCHEMA, "__system__:user-management", null, null),
                PermissionLevel.READ),
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.TABLE, "s_1", "t_1", null),
                PermissionLevel.READ),
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.TABLE, "s_1", "t_missing", null),
                PermissionLevel.READ),
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.COLUMN, "s_1", "t_1", "c_1"),
                PermissionLevel.READ),
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.COLUMN, "s_1", "t_1", "c_missing"),
                PermissionLevel.READ),
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.COLUMN, "s_1", "t_missing", "c_1"),
                PermissionLevel.READ));
    List<AuxiliaryPermissionEntry> auxiliary =
        List.of(
            new AuxiliaryPermissionEntry(
                "r", new PermissionScope(ScopeType.TABLE, "s_1", "t_1", null), true, null),
            new AuxiliaryPermissionEntry(
                "r", new PermissionScope(ScopeType.TABLE, "s_1", "t_missing", null), true, null));
    ConfigDocument document =
        doc(
            List.of(table("s_1", "t_1", null, col("c_1"))),
            List.of(),
            rbac(List.of("r"), List.of(), primary, auxiliary));

    ImportErrorCollector errors = run(document);

    assertThat(fields(errors))
        .containsExactly(
            "rbac.primaryPermissions[1].scope",
            "rbac.primaryPermissions[4].scope",
            "rbac.primaryPermissions[6].scope",
            "rbac.primaryPermissions[7].scope",
            "rbac.auxiliaryPermissions[1].scope");
  }

  @Test
  void reservedSchemaNamesAreNotInterpretedByThisUnitButDelegatedToThePredicate() {
    List<PrimaryPermissionEntry> primary =
        List.of(
            new PrimaryPermissionEntry(
                "r",
                new PermissionScope(ScopeType.SCHEMA, "__system__:x", null, null),
                PermissionLevel.READ));
    ConfigDocument document =
        doc(List.of(), List.of(), rbac(List.of("r"), List.of(), primary, List.of()));
    ImportErrorCollector accepted = new ImportErrorCollector();
    ImportErrorCollector rejected = new ImportErrorCollector();

    validator.validate(document, name -> true, accepted);
    validator.validate(document, name -> false, rejected);

    assertThat(accepted.hasErrors()).isFalse();
    assertThat(fields(rejected)).containsExactly("rbac.primaryPermissions[0].scope");
  }

  @Test
  void permissionAndGroupRolesMustBeRolesInTheFile() {
    ConfigDocument document =
        doc(
            List.of(),
            List.of(),
            rbac(
                List.of("r"),
                List.of(new GroupEntry("g", List.of("r", "r_missing"))),
                List.of(
                    new PrimaryPermissionEntry(
                        "r_missing",
                        new PermissionScope(ScopeType.SCHEMA, "__system__:x", null, null),
                        PermissionLevel.READ)),
                List.of(
                    new AuxiliaryPermissionEntry(
                        "r_missing",
                        new PermissionScope(ScopeType.SCHEMA, "__system__:x", null, null),
                        true,
                        null))));

    ImportErrorCollector errors = run(document);

    assertThat(fields(errors))
        .containsExactly(
            "rbac.groups[0].roleNames[1]",
            "rbac.primaryPermissions[0].roleName",
            "rbac.auxiliaryPermissions[0].roleName");
  }

  @Test
  void fkReferencesMustPointToTablesAndColumnsInTheFile() {
    List<TableEntry> tables =
        List.of(
            table("s_1", "t_ref", null, col("c_v"), col("c_l")),
            table(
                "s_1",
                "t_main",
                null,
                fkCol("ok", new FkRef("s_1", "t_ref", "c_v", "c_l")),
                fkCol("badTable", new FkRef("s_1", "t_missing", "c_v", "c_l")),
                fkCol("badSchema", new FkRef("s_missing", "t_ref", "c_v", "c_l")),
                fkCol("badValue", new FkRef("s_1", "t_ref", "c_missing", "c_l")),
                fkCol("badLabel", new FkRef("s_1", "t_ref", "c_v", "c_missing"))));

    ImportErrorCollector errors =
        run(doc(tables, List.of(), rbac(List.of(), List.of(), List.of(), List.of())));

    assertThat(fields(errors))
        .containsExactly(
            "schema.tables[1].columns[1].fkReference.referencedTableName",
            "schema.tables[1].columns[2].fkReference.referencedTableName",
            "schema.tables[1].columns[3].fkReference.referencedValueColumnName",
            "schema.tables[1].columns[4].fkReference.referencedLabelColumnName");
  }

  @Test
  void theOptimisticLockColumnMustBeAColumnOfThatTable() {
    List<TableEntry> tables =
        List.of(
            table("s_1", "t_ok", "c_1", col("c_1")),
            table("s_1", "t_bad", "c_other", col("c_1")),
            table("s_1", "t_none", null, col("c_1")));

    ImportErrorCollector errors =
        run(doc(tables, List.of(), rbac(List.of(), List.of(), List.of(), List.of())));

    assertThat(fields(errors)).containsExactly("schema.tables[1].optimisticLockColumn");
  }

  @Test
  void everyUnresolvedReferenceIsCollectedInOnePass() {
    ConfigDocument document =
        doc(
            List.of(
                table(
                    "s_1",
                    "t_1",
                    "c_missing",
                    fkCol("c", new FkRef("s_1", "t_missing", "a", "b")))),
            List.of(new MenuEntry("m", 1, new TableRef("s_1", "t_missing"), null)),
            rbac(
                List.of(),
                List.of(new GroupEntry("g", List.of("r_missing"))),
                List.of(),
                List.of()));

    assertThat(run(document).total()).isEqualTo(4);
  }
}
