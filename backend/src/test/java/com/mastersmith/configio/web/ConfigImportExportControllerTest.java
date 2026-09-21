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

package com.mastersmith.configio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.common.security.TestOperatorContext;
import com.mastersmith.common.security.TestOperatorContextConfig;
import com.mastersmith.common.security.TestPermitAllSecurityConfig;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.configio.exception.ConfigImportValidationException;
import com.mastersmith.configio.service.ConfigExportService;
import com.mastersmith.configio.service.ConfigImportService;
import com.mastersmith.configio.service.ImportResult;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.permission.PermissionEngineApi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@link ConfigImportExportController}・{@link ConfigImportExceptionHandler}・{@link
 * ConfigImportAuthorizer}のテスト(実際のSpring MVC・実際のJacksonのHTTPメッセージ変換。エクスポート・インポートの
 * サービスと、permission-engine(C10)は、モックし、操作者(C15)は、{@link
 * TestOperatorContext}で供給する)。C7の応答の形式(200・401・403・422・500・503、RFC 9457)、認可の再検証(サーバー側で必ず先に)、
 * 束縛の失敗(読めないJSON・空の本体・Content-Type)が、認可の後に、422(MALFORMED)になること、内部の詳細を応答に含めないこと(NFR2.6)を確認する。認可拒否(negative-authorization)専用のテスト
 * (team.md Q8-c)を含む。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@AutoConfigureMockMvc
@Import({TestOperatorContextConfig.class, TestPermitAllSecurityConfig.class})
class ConfigImportExportControllerTest {

  private static final String EXPORT = "/api/config/export";
  private static final String IMPORT = "/api/config/import";
  private static final String USER_ID = "user-1";
  private static final String ROLE_ID = "role-1";

  @Autowired private MockMvc mockMvc;
  @Autowired private TestOperatorContext operators;

  @MockitoBean private ConfigExportService exportService;
  @MockitoBean private ConfigImportService importService;
  @MockitoBean private PermissionEngineApi permissionEngineApi;

  @BeforeEach
  void authorizedByDefault() {
    operators.set(USER_ID, ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ROLE_ID), eq("config-import-export")))
        .thenReturn(true);
  }

  @AfterEach
  void cleanUp() {
    operators.clear();
    reset(exportService, importService, permissionEngineApi);
  }

  private static ConfigDocument document() {
    return ConfigDocumentFactory.mechanical(Spec.small());
  }

  private static ImportResult resultOf(Map<String, SectionCounts> sections) {
    return new ImportResult(sections);
  }

  // ---- エクスポート ----

  @Test
  void exportReturnsTheDocumentAsJsonWithAnAttachmentFileName() throws Exception {
    ConfigDocument document =
        new ConfigDocument(
            1,
            "2026-09-21T01:02:03Z",
            "1.2.3",
            document().schema(),
            document().menu(),
            document().rbac());
    when(exportService.export(any())).thenReturn(document);

    mockMvc
        .perform(get(EXPORT))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString(
                        "mastersmith-config-20260921T010203Z.json")))
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
        .andExpect(jsonPath("$.formatVersion").value(1))
        .andExpect(jsonPath("$.exportedAt").value("2026-09-21T01:02:03Z"))
        .andExpect(jsonPath("$.appVersion").value("1.2.3"))
        .andExpect(jsonPath("$.schema.tables[0].schemaName").value("schema_0000"))
        .andExpect(jsonPath("$.schema.tables[0].columns[0].isPrimaryKey").value(true))
        .andExpect(jsonPath("$.rbac.primaryPermissions[0].level").exists());
    verify(exportService).export(TestOperatorContext.operator(USER_ID, ROLE_ID));
  }

  @Test
  void exportWithoutAnOperatorIs401BeforeAnythingElse() throws Exception {
    operators.clear();

    mockMvc
        .perform(get(EXPORT))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("auth.token.invalid"));
    verifyNoInteractions(exportService, permissionEngineApi);
  }

  @Test
  void exportWithoutThePermissionIs403WithoutReadingAnything() throws Exception {
    when(permissionEngineApi.canAccessScreen(eq(ROLE_ID), eq("config-import-export")))
        .thenReturn(false);

    mockMvc
        .perform(get(EXPORT))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("config.import.forbidden"))
        .andExpect(jsonPath("$.instance").value(EXPORT));
    verifyNoInteractions(exportService);
  }

  @Test
  void exportWithAnUnselectedActiveRolePassesNullToPermissionEngineWithoutRejectingItself()
      throws Exception {
    operators.set(USER_ID, null);
    when(permissionEngineApi.canAccessScreen(eq(null), eq("config-import-export")))
        .thenReturn(true);
    when(exportService.export(any())).thenReturn(document());

    mockMvc.perform(get(EXPORT)).andExpect(status().isOk());

    verify(permissionEngineApi).canAccessScreen(null, "config-import-export");
  }

  @Test
  void exportWhenTheDatabaseIsUnavailableIs503WithAGenericMessage() throws Exception {
    when(exportService.export(any()))
        .thenThrow(new DataAccessResourceFailureException("jdbc:h2:file:./secret-path failed"));

    mockMvc
        .perform(get(EXPORT))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("config.import.unavailable"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-path"))))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))));
  }

  @Test
  void exportWithAnUnexpectedFailureIs500WithoutInternalDetails() throws Exception {
    when(exportService.export(any()))
        .thenThrow(new IllegalStateException("SECRET-INTERNAL-DETAIL"));

    mockMvc
        .perform(get(EXPORT))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("config.import.internal-error"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SECRET-INTERNAL-DETAIL"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("IllegalStateException"))));
  }

  // ---- インポート: 成功・認可 ----

  @Test
  void importReturns200WithTheSectionCounts() throws Exception {
    Map<String, SectionCounts> sections = new LinkedHashMap<>();
    sections.put(ImportSections.SCHEMA, new SectionCounts(1, 2, 3));
    sections.put(ImportSections.MENU, new SectionCounts(0, 0, 4));
    when(importService.importConfig(any(), any())).thenReturn(resultOf(sections));

    mockMvc
        .perform(
            post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{\"formatVersion\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("SUCCESS"))
        .andExpect(jsonPath("$.sections.schema.added").value(1))
        .andExpect(jsonPath("$.sections.schema.updated").value(2))
        .andExpect(jsonPath("$.sections.schema.deleted").value(3))
        .andExpect(jsonPath("$.sections.menu.deleted").value(4));
  }

  @Test
  void importPassesTheAuthorizedOperatorAndTheParsedBodyToTheService() throws Exception {
    when(importService.importConfig(any(), any())).thenReturn(resultOf(Map.of()));

    mockMvc
        .perform(
            post(IMPORT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formatVersion\":1,\"x\":[1,2]}"))
        .andExpect(status().isOk());

    verify(importService)
        .importConfig(
            org.mockito.ArgumentMatchers.argThat(
                node ->
                    node.isObject()
                        && node.get("formatVersion").intValue() == 1
                        && node.get("x").size() == 2),
            eq(TestOperatorContext.operator(USER_ID, ROLE_ID)));
  }

  @Test
  void importWithoutAnOperatorIs401AndTheServiceIsNeverCalled() throws Exception {
    operators.clear();

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
    verifyNoInteractions(importService);
  }

  @Test
  void importWithoutThePermissionIs403AndTheServiceIsNeverCalled() throws Exception {
    when(permissionEngineApi.canAccessScreen(eq(ROLE_ID), eq("config-import-export")))
        .thenReturn(false);

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("config.import.forbidden"));
    verifyNoInteractions(importService);
  }

  // ---- インポート: 422 ----

  @Test
  void aValidationFailureIs422WithPositionedErrorsTheTotalAndTheTruncationFlag() throws Exception {
    when(importService.importConfig(any(), any()))
        .thenThrow(
            new ConfigImportValidationException(
                FailureCategory.VALIDATION_ERROR,
                List.of(
                    ImportValidationError.of(
                        "schema.tables[3].columns[2].editorType",
                        ImportMessageKeys.FIELD_VALUE_INVALID,
                        Map.of("allowed", List.of("text", "select"))),
                    ImportValidationError.of(
                        "menu.items[0].label", ImportMessageKeys.FIELD_REQUIRED)),
                150,
                true));

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("config.import.validation.failed"))
        .andExpect(jsonPath("$.errorCount").value(150))
        .andExpect(jsonPath("$.truncated").value(true))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].field").value("schema.tables[3].columns[2].editorType"))
        .andExpect(jsonPath("$.errors[0].message").value("config.import.field.value.invalid"))
        .andExpect(jsonPath("$.errors[0].params.allowed[1]").value("select"))
        .andExpect(jsonPath("$.errors[1].field").value("menu.items[0].label"))
        .andExpect(jsonPath("$.errors[1].params").doesNotExist());
  }

  static Stream<Arguments> failureCategoriesAndCodes() {
    return Stream.of(
        Arguments.of(FailureCategory.MALFORMED, "config.import.json.malformed"),
        Arguments.of(FailureCategory.UNSUPPORTED_FORMAT, "config.import.format.unsupported"),
        Arguments.of(FailureCategory.ESCALATION_DENIED, "config.import.rbac.escalation"),
        Arguments.of(FailureCategory.RBAC_EMPTY, "config.import.rbac.empty"),
        Arguments.of(FailureCategory.VALIDATION_ERROR, "config.import.validation.failed"));
  }

  @ParameterizedTest(name = "{0} -> code {1}")
  @MethodSource("failureCategoriesAndCodes")
  void theOverallCodeTellsTheFailureCategoryApart(FailureCategory category, String code)
      throws Exception {
    when(importService.importConfig(any(), any()))
        .thenThrow(
            new ConfigImportValidationException(
                category, List.of(ImportValidationError.of("x", "m")), 1, false));

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.truncated").value(false));
  }

  // ---- インポート: 束縛の失敗(認可の後に、422のMALFORMED) ----

  static Stream<Arguments> unreadableBodies() {
    return Stream.of(
        Arguments.of("読めないJSON", MediaType.APPLICATION_JSON, "{\"formatVersion\":"),
        Arguments.of("JSONでない本文", MediaType.APPLICATION_JSON, "not json at all"),
        Arguments.of("空の本体", MediaType.APPLICATION_JSON, ""),
        Arguments.of("Content-Typeの不一致", MediaType.TEXT_PLAIN, "{\"formatVersion\":1}"));
  }

  @ParameterizedTest(name = "認可後の束縛の失敗: {0}")
  @MethodSource("unreadableBodies")
  void anUnreadableBodyAfterAuthorizationIs422MalformedAndRecordsAFailureEvent(
      String label, MediaType contentType, String body) throws Exception {
    mockMvc
        .perform(post(IMPORT).contentType(contentType).content(body))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("config.import.json.malformed"))
        .andExpect(jsonPath("$.errorCount").value(1))
        .andExpect(jsonPath("$.errors[0].message").value("config.import.json.malformed"));

    verify(importService).recordMalformed(TestOperatorContext.operator(USER_ID, ROLE_ID));
    verify(importService, never()).importConfig(any(), any());
  }

  @ParameterizedTest(name = "権限なしの束縛の失敗: {0}")
  @MethodSource("unreadableBodies")
  void anUnreadableBodyWithoutThePermissionIs403AndLeavesNoAuditTrail(
      String label, MediaType contentType, String body) throws Exception {
    when(permissionEngineApi.canAccessScreen(eq(ROLE_ID), eq("config-import-export")))
        .thenReturn(false);

    mockMvc
        .perform(post(IMPORT).contentType(contentType).content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("config.import.forbidden"));

    verifyNoInteractions(importService);
  }

  @ParameterizedTest(name = "未認証の束縛の失敗: {0}")
  @MethodSource("unreadableBodies")
  void anUnreadableBodyWithoutAnOperatorIs401AndLeavesNoAuditTrail(
      String label, MediaType contentType, String body) throws Exception {
    operators.clear();

    mockMvc
        .perform(post(IMPORT).contentType(contentType).content(body))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(importService);
  }

  @Test
  void aDatabaseFailureWhileAuthorizingAnUnreadableBodyIs503() throws Exception {
    when(permissionEngineApi.canAccessScreen(any(), eq("config-import-export")))
        .thenThrow(new DataAccessResourceFailureException("down"));

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isServiceUnavailable());

    verifyNoInteractions(importService);
  }

  @Test
  void anUnexpectedFailureWhileAuthorizingAnUnreadableBodyIs500() throws Exception {
    when(permissionEngineApi.canAccessScreen(any(), eq("config-import-export")))
        .thenThrow(new IllegalStateException("boom"));

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isInternalServerError());
  }

  // ---- インポート: 内部の障害 ----

  @Test
  void aConcurrencyFailureIs503() throws Exception {
    when(importService.importConfig(any(), any()))
        .thenThrow(new CannotAcquireLockException("lock"));

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("config.import.unavailable"));
  }

  @Test
  void aTransactionFailureIs503() throws Exception {
    when(importService.importConfig(any(), any()))
        .thenThrow(
            new org.springframework.transaction.CannotCreateTransactionException("no connection"));

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void anUnexpectedFailureIs500AndNothingInternalLeaks() throws Exception {
    when(importService.importConfig(any(), any()))
        .thenThrow(new IllegalStateException("SECRET-SQL select * from x"));

    mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("config.import.internal-error"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET-SQL"))));
  }

  // ---- 認可拒否(negative-authorization)専用 ----

  static Stream<Arguments> everyEndpointWithoutAccess() {
    return Stream.of(Arguments.of("GET", EXPORT), Arguments.of("POST", IMPORT));
  }

  @ParameterizedTest(name = "{0} {1}: 権限がなければ拒否し、サービスに触れない")
  @MethodSource("everyEndpointWithoutAccess")
  void everyEndpointRejectsAnOperatorWhoseRoleHasNoAccessToTheScreen(String method, String path)
      throws Exception {
    when(permissionEngineApi.canAccessScreen(eq(ROLE_ID), eq("config-import-export")))
        .thenReturn(false);

    var request =
        method.equals("GET")
            ? get(path)
            : post(path).contentType(MediaType.APPLICATION_JSON).content("{\"formatVersion\":1}");
    MvcResult result = mockMvc.perform(request).andExpect(status().isForbidden()).andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("formatVersion");
    verifyNoInteractions(exportService, importService);
  }

  @Test
  void theMethodAndPathContractIsExactlyTheAgreedOne() throws Exception {
    mockMvc.perform(post(EXPORT)).andExpect(status().isMethodNotAllowed());
    mockMvc.perform(get(IMPORT)).andExpect(status().isMethodNotAllowed());
  }
}
