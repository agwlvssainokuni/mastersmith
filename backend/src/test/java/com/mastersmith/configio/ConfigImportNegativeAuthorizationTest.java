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

import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * 認可拒否(negative-authorization)専用テスト(team.md Q8-c、code-generation-plan.md Step 18):
 * 権限が不足する操作を、確実に拒否すること。実際のpermission-engine・実際の内部設定DBで、401(操作者を解決できない)・
 * 403(権限なし。アクティブロール未選択・実在しないロール・NONE・別の画面の権限だけを含む)を、エクスポート・インポートの両方で確認し、拒否された要求が、内部設定DBを変えない・監査ログに行を作らないこと、
 * 初期状態の例外(主権限が0件の間だけ、ロールのない初期管理者も、到達できる)が、初回の取り込みで、終了することを確認する。
 */
class ConfigImportNegativeAuthorizationTest extends ConfigIoIntegrationTestBase {

  private static final String VIEWER = "role_viewer";
  private static final String SCREEN_READER = "role_screen_reader";
  private static final String SCREEN_NONE = "role_screen_none";

  /** 管理者(設定管理画面のFULL)・閲覧者(業務スキーマのREAD。設定管理画面の権限なし)・画面のREADだけのロール・画面がNONEのロールを持つ設定。 */
  private static ConfigDocument rbacFixture() {
    ConfigDocument base =
        ConfigDocumentFactory.withAdministrator(
            ConfigDocumentFactory.mechanical(Spec.small()), ADMIN_ROLE);
    List<ConfigDocument.RoleEntry> roles = new ArrayList<>(base.rbac().roles());
    roles.add(new ConfigDocument.RoleEntry(VIEWER));
    roles.add(new ConfigDocument.RoleEntry(SCREEN_READER));
    roles.add(new ConfigDocument.RoleEntry(SCREEN_NONE));
    List<ConfigDocument.PrimaryPermissionEntry> primary =
        new ArrayList<>(base.rbac().primaryPermissions());
    primary.add(
        new ConfigDocument.PrimaryPermissionEntry(
            VIEWER,
            new ConfigDocument.PermissionScope(ScopeType.SCHEMA, "schema_0000", null, null),
            PermissionLevel.READ));
    primary.add(
        new ConfigDocument.PrimaryPermissionEntry(
            SCREEN_READER,
            new ConfigDocument.PermissionScope(
                ScopeType.SCHEMA, ConfigDocumentFactory.ADMIN_SCREEN_SCHEMA, null, null),
            PermissionLevel.READ));
    primary.add(
        new ConfigDocument.PrimaryPermissionEntry(
            SCREEN_NONE,
            new ConfigDocument.PermissionScope(
                ScopeType.SCHEMA, ConfigDocumentFactory.ADMIN_SCREEN_SCHEMA, null, null),
            PermissionLevel.NONE));
    return new ConfigDocument(
        base.formatVersion(),
        base.exportedAt(),
        base.appVersion(),
        base.schema(),
        base.menu(),
        new ConfigDocument.RbacSection(
            roles, base.rbac().groups(), primary, base.rbac().auxiliaryPermissions()));
  }

  private void seed() throws Exception {
    MvcResult seeded = postImport(rbacFixture());
    assertThat(seeded.getResponse().getStatus())
        .as(seeded.getResponse().getContentAsString())
        .isEqualTo(200);
  }

  private long auditRowCount() {
    return jdbc.queryForObject("select count(*) from audit_log_entry", Long.class);
  }

  private long tableCount() {
    return jdbc.queryForObject("select count(*) from table_config", Long.class);
  }

  private MvcResult importRequest() throws Exception {
    return mockMvc
        .perform(
            MockMvcRequestBuilders.post(IMPORT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ConfigDocumentFactory.toJson(rbacFixture()).toString()))
        .andReturn();
  }

  // ---- 401 ----

  @Test
  void withoutAnOperatorBothEndpointsAre401AndNothingIsWritten() throws Exception {
    seed();
    operators.clear();
    long audits = auditRowCount();
    long tables = tableCount();

    MvcResult export = getExport();
    MvcResult imported = importRequest();

    assertThat(export.getResponse().getStatus()).isEqualTo(401);
    assertThat(imported.getResponse().getStatus()).isEqualTo(401);
    assertThat(auditRowCount()).isEqualTo(audits);
    assertThat(tableCount()).isEqualTo(tables);
  }

  // ---- 403 ----

  @ParameterizedTest(name = "ロール未選択・{0}")
  @ValueSource(strings = {"", "  "})
  void anUnselectedActiveRoleIsForbiddenOnceRbacIsConfigured(String blank) throws Exception {
    seed();
    operators.set("unselected-user", blank);

    assertThat(getExport().getResponse().getStatus()).isEqualTo(403);
    assertThat(importRequest().getResponse().getStatus()).isEqualTo(403);
    assertThat(importAuditEntriesOf("unselected-user")).isEmpty();
  }

  @Test
  void aNullActiveRoleIsForbiddenOnceRbacIsConfiguredBecauseTheBootstrapExceptionHasEnded()
      throws Exception {
    seed();
    operators.set("initial-admin", null);

    assertThat(getExport().getResponse().getStatus()).isEqualTo(403);
    assertThat(importRequest().getResponse().getStatus()).isEqualTo(403);
    assertThat(tableCount()).isEqualTo(4);
  }

  @Test
  void aRoleThatDoesNotExistIsForbidden() throws Exception {
    seed();
    operators.set("ghost-user", "role-id-that-does-not-exist");

    assertThat(getExport().getResponse().getStatus()).isEqualTo(403);
    assertThat(importRequest().getResponse().getStatus()).isEqualTo(403);
  }

  @ParameterizedTest(name = "権限のないロール: {0}")
  @ValueSource(strings = {VIEWER, SCREEN_NONE})
  void aRoleWithoutTheScreenPermissionIsForbiddenOnBothEndpointsAndLeavesNoTrace(String roleName)
      throws Exception {
    seed();
    actAs("unprivileged-" + roleName, roleName);
    long audits = auditRowCount();
    long tables = tableCount();

    MvcResult export = getExport();
    MvcResult imported = importRequest();

    assertThat(export.getResponse().getStatus()).isEqualTo(403);
    assertThat(imported.getResponse().getStatus()).isEqualTo(403);
    assertThat(export.getResponse().getContentAsString())
        .doesNotContain("formatVersion")
        .doesNotContain("schema_0000");
    assertThat(importAuditEntriesOf("unprivileged-" + roleName)).isEmpty();
    assertThat(auditRowCount()).isEqualTo(audits);
    assertThat(tableCount()).isEqualTo(tables);
  }

  @Test
  void anUnreadableBodyFromAnUnprivilegedRoleIs403NotA422AndAddsNoAuditRow() throws Exception {
    seed();
    actAs("unprivileged-malformed", VIEWER);
    long audits = auditRowCount();

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post(IMPORT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"formatVersion\":"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    assertThat(auditRowCount()).isEqualTo(audits);
  }

  // ---- 許可(拒否の裏返し。拒否が、権限のある側を巻き込まないこと) ----

  @Test
  void aReadOnlyPermissionOnTheScreenIsEnoughToReachBothEndpointsUnlessEscalationIsInvolved()
      throws Exception {
    seed();
    actAs("screen-reader", SCREEN_READER);

    assertThat(getExport().getResponse().getStatus()).isEqualTo(200);
    // インポートは、到達できる(403ではない)。この設定は、操作者(READ)の権限を上回るため、昇格として422になる。
    MvcResult imported = importRequest();
    assertThat(imported.getResponse().getStatus()).isEqualTo(422);
    JsonNode body =
        ConfigDocumentFactory.JSON.readTree(imported.getResponse().getContentAsString());
    assertThat(body.get("code").stringValue()).isEqualTo("config.import.rbac.escalation");
  }

  // ---- 初期状態の例外 ----

  @Test
  void
      theBootstrapExceptionLetsTheInitialAdminWithoutARoleImportOnlyWhileNoPrimaryPermissionExists()
          throws Exception {
    operators.set("initial-admin", null);
    // 主権限が0件の間は、ロールを持たない初期管理者も、到達できる(エクスポートも)。
    assertThat(getExport().getResponse().getStatus()).isEqualTo(200);

    MvcResult first = importRequest();
    assertThat(first.getResponse().getStatus())
        .as(first.getResponse().getContentAsString())
        .isEqualTo(200);

    // 初回の取り込みで、主権限が1件以上になり、初期状態の例外は、終了する。
    assertThat(getExport().getResponse().getStatus()).isEqualTo(403);
    assertThat(importRequest().getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void
      anImportThatWouldEmptyThePrimaryPermissionsIsRejectedEvenInTheBootstrapStateSoTheExceptionCannotBeReEnabled()
          throws Exception {
    operators.set("initial-admin", null);
    ConfigDocument empty = ConfigDocumentFactory.mechanical(new Spec(1, 1, 1, 0, 0, 0, 0, 0, 0));

    MvcResult result = postImport(empty);

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    JsonNode body = ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").stringValue()).isEqualTo("config.import.rbac.empty");
    assertThat(tableCount()).isZero();
  }
}
