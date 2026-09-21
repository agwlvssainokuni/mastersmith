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

package com.mastersmith.configio;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.document.ConfigDocument.AuxiliaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.PermissionScope;
import com.mastersmith.configio.document.ConfigDocument.PrimaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.RbacSection;
import com.mastersmith.configio.document.ConfigDocument.RoleEntry;
import com.mastersmith.configio.document.ConfigDocument.TableEntry;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 権限昇格・主権限0件のテスト(表形式。取り込みのAPI経由のエンドツーエンド。team.md Q6の追加の合格条件、code-generation-plan.md Step 19):
 * {@code RbacImportValidationMatrixTest}(permission-engineの検証専用メソッドの、
 * 権限マトリクス)を、取り込みのAPI・実際のpermission-engine・実際の内部設定DBで確認する。昇格の拒否(422。該当のエントリをすべて集める。失敗の分類
 * ESCALATION_DENIED。何も反映しない)・主権限0件の拒否 (RBAC_EMPTY)・初期状態の初回投入(昇格の判定を行わない)。
 *
 * <p>操作者の権限(取り込み開始時点): 管理者ロールは、設定管理画面=FULL、{@code schema_0000}=READ、{@code
 * schema_0001}=FULL、ただし{@code schema_0001.table_0000}=NONE(下位の拒否)、 {@code schema_0001}の補助権限=作成のみ可。
 */
class ConfigImportEscalationMatrixE2ETest extends ConfigIoIntegrationTestBase {

  private static final String TARGET = "role_target";
  private static final String S0 = "schema_0000";
  private static final String S1 = "schema_0001";

  private static PermissionScope schema(String s) {
    return new PermissionScope(ScopeType.SCHEMA, s, null, null);
  }

  private static PermissionScope table(String s, String t) {
    return new PermissionScope(ScopeType.TABLE, s, t, null);
  }

  private static PermissionScope column(String s, String t, String c) {
    return new PermissionScope(ScopeType.COLUMN, s, t, c);
  }

  /** 管理者の権限だけを持つ、基準の設定(schemaは、mechanical(small)。ロールは、管理者と、対象のロール)。 */
  private static ConfigDocument base(List<TableEntry> extraTables) {
    ConfigDocument m = ConfigDocumentFactory.mechanical(Spec.small());
    List<PrimaryPermissionEntry> primary = new ArrayList<>();
    primary.add(
        new PrimaryPermissionEntry(
            ADMIN_ROLE, schema(ConfigDocumentFactory.ADMIN_SCREEN_SCHEMA), PermissionLevel.FULL));
    primary.add(new PrimaryPermissionEntry(ADMIN_ROLE, schema(S0), PermissionLevel.READ));
    primary.add(new PrimaryPermissionEntry(ADMIN_ROLE, schema(S1), PermissionLevel.FULL));
    primary.add(
        new PrimaryPermissionEntry(ADMIN_ROLE, table(S1, "table_0000"), PermissionLevel.NONE));
    List<AuxiliaryPermissionEntry> auxiliary = new ArrayList<>();
    auxiliary.add(new AuxiliaryPermissionEntry(ADMIN_ROLE, schema(S1), true, null));
    List<TableEntry> tables = new ArrayList<>(m.schema().tables());
    tables.addAll(extraTables);
    return new ConfigDocument(
        1,
        null,
        null,
        new ConfigDocument.SchemaSection(tables, m.schema().translations()),
        m.menu(),
        new RbacSection(
            List.of(new RoleEntry(ADMIN_ROLE), new RoleEntry(TARGET)),
            List.of(),
            primary,
            auxiliary));
  }

  private static TableEntry newTable(String schemaName) {
    return new TableEntry(schemaName, "table_new", 9, null, List.of());
  }

  private static ConfigDocument withEntries(
      ConfigDocument base,
      List<PrimaryPermissionEntry> primary,
      List<AuxiliaryPermissionEntry> auxiliary) {
    List<PrimaryPermissionEntry> allPrimary = new ArrayList<>(base.rbac().primaryPermissions());
    allPrimary.addAll(primary);
    List<AuxiliaryPermissionEntry> allAuxiliary =
        new ArrayList<>(base.rbac().auxiliaryPermissions());
    allAuxiliary.addAll(auxiliary);
    return new ConfigDocument(
        1,
        null,
        null,
        base.schema(),
        base.menu(),
        new RbacSection(base.rbac().roles(), base.rbac().groups(), allPrimary, allAuxiliary));
  }

  private record Row(
      String name,
      PermissionScope scope,
      PermissionLevel level,
      boolean newTableInSchema0,
      boolean newTableInSchema1,
      boolean escalation) {
    @Override
    public String toString() {
      return name;
    }
  }

  private static Row row(
      String name, PermissionScope scope, PermissionLevel level, boolean escalation) {
    return new Row(name, scope, level, false, false, escalation);
  }

  static Stream<Arguments> primaryMatrix() {
    return Stream.of(
            // 操作者: schema_0000=READ
            row("s0 SCHEMA READ(同等)", schema(S0), PermissionLevel.READ, false),
            row("s0 SCHEMA FULL(昇格)", schema(S0), PermissionLevel.FULL, true),
            row("s0 SCHEMA NONE(降格)", schema(S0), PermissionLevel.NONE, false),
            row("s0 TABLE READ(継承)", table(S0, "table_0001"), PermissionLevel.READ, false),
            row("s0 TABLE FULL(昇格)", table(S0, "table_0001"), PermissionLevel.FULL, true),
            row(
                "s0 COLUMN FULL(昇格)",
                column(S0, "table_0000", "column_0000"),
                PermissionLevel.FULL,
                true),
            // 操作者: schema_0001=FULL、ただしtable_0000=NONE
            row("s1 SCHEMA FULL(同等)", schema(S1), PermissionLevel.FULL, false),
            row("s1 TABLE FULL(継承)", table(S1, "table_0001"), PermissionLevel.FULL, false),
            row(
                "s1 table_0000 READ(下位の拒否NONE。昇格)",
                table(S1, "table_0000"),
                PermissionLevel.READ,
                true),
            row("s1 table_0000 NONE(同等)", table(S1, "table_0000"), PermissionLevel.NONE, false),
            row(
                "s1 table_0000のCOLUMN READ(TABLEのNONEを継承。昇格)",
                column(S1, "table_0000", "column_0001"),
                PermissionLevel.READ,
                true),
            row(
                "s1 table_0001のCOLUMN FULL(継承)",
                column(S1, "table_0001", "column_0001"),
                PermissionLevel.FULL,
                false),
            // 設定管理画面の予約スキーマ
            row(
                "画面 FULL(同等)",
                schema(ConfigDocumentFactory.ADMIN_SCREEN_SCHEMA),
                PermissionLevel.FULL,
                false),
            row(
                "画面 READ(降格)",
                schema(ConfigDocumentFactory.ADMIN_SCREEN_SCHEMA),
                PermissionLevel.READ,
                false),
            // 取り込みで新規に作るテーブル(仮の識別。スキーマの設定にフォールバック)
            new Row(
                "新規テーブル(s0)TABLE READ",
                table(S0, "table_new"),
                PermissionLevel.READ,
                true,
                false,
                false),
            new Row(
                "新規テーブル(s0)TABLE FULL(昇格)",
                table(S0, "table_new"),
                PermissionLevel.FULL,
                true,
                false,
                true),
            new Row(
                "新規テーブル(s1)TABLE FULL",
                table(S1, "table_new"),
                PermissionLevel.FULL,
                false,
                true,
                false))
        .map(Arguments::of);
  }

  private ConfigDocument seedAndReturnBase(boolean newTableInS0, boolean newTableInS1)
      throws Exception {
    // 初回(ブートストラップ状態)は、新規のテーブルなしの基準を、ロールを持たない初期管理者が取り込む。
    operators.set("bootstrap-admin", null);
    MvcResult seeded = postImport(base(List.of()));
    assertThat(seeded.getResponse().getStatus())
        .as(seeded.getResponse().getContentAsString())
        .isEqualTo(200);
    actAs("matrix-admin", ADMIN_ROLE);
    List<TableEntry> extra = new ArrayList<>();
    if (newTableInS0) {
      extra.add(newTable(S0));
    }
    if (newTableInS1) {
      extra.add(newTable(S1));
    }
    return base(extra);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("primaryMatrix")
  void primaryPermissionEscalationThroughTheImportApiFollowsTheMatrix(Row row) throws Exception {
    ConfigDocument base = seedAndReturnBase(row.newTableInSchema0(), row.newTableInSchema1());
    ConfigDocument file =
        withEntries(
            base, List.of(new PrimaryPermissionEntry(TARGET, row.scope(), row.level())), List.of());
    long primaryBefore = jdbc.queryForObject("select count(*) from primary_permission", Long.class);

    MvcResult result = postImport(file);

    if (row.escalation()) {
      assertThat(result.getResponse().getStatus()).isEqualTo(422);
      JsonNode body =
          ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
      assertThat(body.get("code").stringValue()).isEqualTo("config.import.rbac.escalation");
      assertThat(messages(body)).containsExactly("config.import.rbac.escalation");
      assertThat(fields(body))
          .containsExactly(
              "rbac.primaryPermissions[" + (base.rbac().primaryPermissions().size()) + "]");
      // 何も反映されない(権限も、新規のテーブルも)。
      assertThat(jdbc.queryForObject("select count(*) from primary_permission", Long.class))
          .isEqualTo(primaryBefore);
      assertThat(jdbc.queryForObject("select count(*) from table_config", Long.class)).isEqualTo(4);
      assertThat(lastAudit("matrix-admin").getAfterValue())
          .containsEntry("failureCategory", "ESCALATION_DENIED");
    } else {
      assertThat(result.getResponse().getStatus())
          .as(result.getResponse().getContentAsString())
          .isEqualTo(200);
      assertThat(jdbc.queryForObject("select count(*) from primary_permission", Long.class))
          .isEqualTo(primaryBefore + 1);
      assertThat(lastAudit("matrix-admin").getOperationType()).isEqualTo("CONFIG_IMPORT_SUCCEEDED");
    }
  }

  private record AuxRow(
      String name,
      PermissionScope scope,
      Boolean create,
      Boolean delete,
      List<String> escalatedFields) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Arguments> auxiliaryMatrix() {
    String c = ".createAllowed";
    String d = ".deleteAllowed";
    return Stream.of(
            new AuxRow("s1 作成=可(同等)", schema(S1), true, null, List.of()),
            new AuxRow("s1 削除=可(昇格)", schema(S1), null, true, List.of(d)),
            new AuxRow("s1 作成=可・削除=可(削除のみ昇格)", schema(S1), true, true, List.of(d)),
            new AuxRow("s1 TABLE 作成=可(継承)", table(S1, "table_0001"), true, null, List.of()),
            new AuxRow("s1 TABLE 削除=可(昇格)", table(S1, "table_0001"), null, true, List.of(d)),
            new AuxRow("s0 作成=可(操作者に補助権限なし。昇格)", schema(S0), true, null, List.of(c)),
            new AuxRow("s0 作成=可・削除=可(両方昇格)", schema(S0), true, true, List.of(c, d)),
            new AuxRow("s0 作成=不可・削除=不可(降格)", schema(S0), false, false, List.of()),
            new AuxRow("s0 指定なし(判定の対象外)", schema(S0), null, null, List.of()))
        .map(Arguments::of);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("auxiliaryMatrix")
  void auxiliaryPermissionEscalationThroughTheImportApiResolvesCreateAndDeleteIndependently(
      AuxRow row) throws Exception {
    ConfigDocument base = seedAndReturnBase(false, false);
    ConfigDocument file =
        withEntries(
            base,
            List.of(),
            List.of(new AuxiliaryPermissionEntry(TARGET, row.scope(), row.create(), row.delete())));

    MvcResult result = postImport(file);

    if (row.escalatedFields().isEmpty()) {
      assertThat(result.getResponse().getStatus())
          .as(result.getResponse().getContentAsString())
          .isEqualTo(200);
    } else {
      assertThat(result.getResponse().getStatus()).isEqualTo(422);
      JsonNode body =
          ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
      int index = base.rbac().auxiliaryPermissions().size();
      assertThat(fields(body))
          .containsExactlyElementsOf(
              row.escalatedFields().stream()
                  .map(f -> "rbac.auxiliaryPermissions[" + index + "]" + f)
                  .toList());
    }
  }

  // ---- 全件を集める・自分自身への昇格・降格 ----

  @Test
  void
      everyEscalatingEntryIsCollectedInOneResponseAndTheCategoryIsEscalationDeniedEvenWithOtherErrors()
          throws Exception {
    ConfigDocument base = seedAndReturnBase(false, false);
    ConfigDocument file =
        withEntries(
            base,
            List.of(
                new PrimaryPermissionEntry(TARGET, schema(S0), PermissionLevel.FULL),
                new PrimaryPermissionEntry(TARGET, schema(S1), PermissionLevel.FULL),
                new PrimaryPermissionEntry(TARGET, table(S1, "table_0000"), PermissionLevel.FULL)),
            List.of(new AuxiliaryPermissionEntry(TARGET, schema(S0), true, true)));
    tools.jackson.databind.node.ObjectNode json =
        (tools.jackson.databind.node.ObjectNode) ConfigDocumentFactory.toJson(file).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) json.at("/schema/tables/0/columns/0"))
        .put("editorType", "no_such_type");

    MvcResult result = postImport(json);

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    JsonNode body = ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
    int p = base.rbac().primaryPermissions().size();
    int a = base.rbac().auxiliaryPermissions().size();
    assertThat(fields(body))
        .contains(
            "schema.tables[0].columns[0].editorType",
            "rbac.primaryPermissions[" + p + "]",
            "rbac.primaryPermissions[" + (p + 2) + "]",
            "rbac.auxiliaryPermissions[" + a + "].createAllowed",
            "rbac.auxiliaryPermissions[" + a + "].deleteAllowed")
        .doesNotContain("rbac.primaryPermissions[" + (p + 1) + "]");
    assertThat(body.get("code").stringValue()).isEqualTo("config.import.rbac.escalation");
    assertThat(lastAudit("matrix-admin").getAfterValue())
        .containsEntry("failureCategory", "ESCALATION_DENIED")
        .containsEntry("errorCount", 5);
  }

  @Test
  void anAdministratorCannotRaiseTheirOwnRoleAboveWhatTheyHold() throws Exception {
    ConfigDocument base = seedAndReturnBase(false, false);
    List<PrimaryPermissionEntry> primary = new ArrayList<>(base.rbac().primaryPermissions());
    primary.set(
        1,
        new PrimaryPermissionEntry(
            ADMIN_ROLE, schema(S0), PermissionLevel.FULL)); // 自分自身の READ → FULL
    ConfigDocument file =
        new ConfigDocument(
            1,
            null,
            null,
            base.schema(),
            base.menu(),
            new RbacSection(
                base.rbac().roles(),
                base.rbac().groups(),
                primary,
                base.rbac().auxiliaryPermissions()));

    MvcResult result = postImport(file);

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(
            fields(ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString())))
        .containsExactly("rbac.primaryPermissions[1]");
  }

  @Test
  void loweringOrRemovingPermissionsIsNeverAnEscalation() throws Exception {
    ConfigDocument base = seedAndReturnBase(false, false);
    List<PrimaryPermissionEntry> primary = new ArrayList<>(base.rbac().primaryPermissions());
    primary.set(1, new PrimaryPermissionEntry(ADMIN_ROLE, schema(S0), PermissionLevel.NONE));
    primary.remove(2); // schema_0001のFULLを除く
    ConfigDocument file =
        new ConfigDocument(
            1,
            null,
            null,
            base.schema(),
            base.menu(),
            new RbacSection(base.rbac().roles(), base.rbac().groups(), primary, List.of()));

    assertThat(postImport(file).getResponse().getStatus()).isEqualTo(200);
  }

  // ---- 主権限0件・初期状態 ----

  @Test
  void anImportWithNoPrimaryPermissionsIsRejectedAsRbacEmptyBothInAndOutOfTheBootstrapState()
      throws Exception {
    ConfigDocument empty =
        new ConfigDocument(
            1,
            null,
            null,
            base(List.of()).schema(),
            base(List.of()).menu(),
            new RbacSection(List.of(new RoleEntry(ADMIN_ROLE)), List.of(), List.of(), List.of()));

    operators.set("bootstrap-admin", null);
    MvcResult inBootstrap = postImport(empty);
    assertThat(inBootstrap.getResponse().getStatus()).isEqualTo(422);
    assertThat(
            ConfigDocumentFactory.JSON
                .readTree(inBootstrap.getResponse().getContentAsString())
                .get("code")
                .stringValue())
        .isEqualTo("config.import.rbac.empty");
    assertThat(lastAudit("bootstrap-admin").getAfterValue())
        .containsEntry("failureCategory", "RBAC_EMPTY");

    seedAndReturnBase(false, false);
    MvcResult afterBootstrap = postImport(empty);
    assertThat(afterBootstrap.getResponse().getStatus()).isEqualTo(422);
    assertThat(lastAudit("matrix-admin").getAfterValue())
        .containsEntry("failureCategory", "RBAC_EMPTY");
    // 主権限は、取り込み前のまま(初期状態の例外は、再び有効にならない)。
    assertThat(jdbc.queryForObject("select count(*) from primary_permission", Long.class))
        .isEqualTo(4);
  }

  @Test
  void theFirstImportInTheBootstrapStateMayGrantAnythingBecauseEscalationIsNotJudged()
      throws Exception {
    operators.set("bootstrap-admin", null);
    ConfigDocument file =
        withEntries(
            base(List.of(newTable(S0))),
            List.of(
                new PrimaryPermissionEntry(TARGET, schema(S0), PermissionLevel.FULL),
                new PrimaryPermissionEntry(
                    TARGET, column(S1, "table_0000", "column_0000"), PermissionLevel.FULL)),
            List.of(new AuxiliaryPermissionEntry(TARGET, schema(S0), true, true)));

    MvcResult result = postImport(file);

    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    assertThat(jdbc.queryForObject("select count(*) from primary_permission", Long.class))
        .isEqualTo(6);
  }

  // ---- 補助 ----

  private static List<String> fields(JsonNode body) {
    List<String> fields = new ArrayList<>();
    body.get("errors").forEach(e -> fields.add(e.get("field").stringValue()));
    return fields;
  }

  private static List<String> messages(JsonNode body) {
    List<String> messages = new ArrayList<>();
    body.get("errors").forEach(e -> messages.add(e.get("message").stringValue()));
    return messages;
  }

  private AuditLogEntry lastAudit(String userId) {
    List<AuditLogEntry> entries = importAuditEntriesOf(userId);
    assertThat(entries).isNotEmpty();
    return entries.get(0);
  }

  @Test
  void theMatrixFixtureIsSelfConsistent() {
    ConfigDocument base = base(List.of());
    assertThat(base.rbac().primaryPermissions()).hasSize(4);
    assertThat(base.schema().tables()).hasSize(4);
  }
}
