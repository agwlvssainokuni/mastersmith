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

package com.mastersmith.audit.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.common.security.TestOperatorContext;
import com.mastersmith.common.security.TestOperatorContextConfig;
import com.mastersmith.common.security.TestPermitAllSecurityConfig;
import com.mastersmith.permission.PermissionEngineApi;
import java.time.Instant;
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
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AuditLogController}の統合テスト(unit-test-instructions.mdのモック方針: {@link
 * PermissionEngineApi}・{@link TestOperatorContext}で操作者(C15)を供給し、{@link
 * AuditLogEntryRepository}は実際のH2(Flywayマイグレーション適用後)に対して検証する)。
 *
 * <p>正常系(200、ページング・targetTypeフィルタ)、クエリパラメータ検証エラー(400、境界値のテーブル駆動)、認可拒否専用テスト(403、team.md必須テスト種別(c))、内部設定DB利用不可時(503)を検証する(plan
 * Step8)。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@AutoConfigureMockMvc
@Import({TestOperatorContextConfig.class, TestPermitAllSecurityConfig.class})
@Transactional
class AuditLogControllerTest {

  private static final String ENDPOINT = "/api/audit-log";
  private static final String USER_ID = "user-1";
  private static final String ACTIVE_ROLE_ID = "role-1";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private PermissionEngineApi permissionEngineApi;
  @Autowired private TestOperatorContext operators;

  // 実装のAuditLogEntryRepositoryをspyする(unit-test-instructions.md: 実際のH2に対して検証し、モックしない)。
  // 503テストのみdoThrowで一時的に差し替え、@AfterEachでMockito.reset()し既定の実委譲動作へ戻す。
  @MockitoSpyBean private AuditLogEntryRepository repository;

  @BeforeEach
  void grantAccessByDefault() {
    operators.set(USER_ID, ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("audit-log"))).thenReturn(true);
  }

  /**
   * user-management(U4)の初期管理者の自動作成(BOOTSTRAPPED)は、アプリケーションの起動時に、監査ログへ1行を確定する。このテストは、空の監査ログを前提とするため、
   * テストのトランザクションの中でだけ、その行を除外する(テスト終了時にロールバックされ、他のテストには影響しない)。
   */
  @BeforeEach
  void excludeTheRowRecordedAtStartup() {
    jdbcTemplate.update("delete from audit_log_entry");
  }

  @AfterEach
  void resetSpiedRepository() {
    reset(repository);
  }

  private static AuditLogEntry anEntry(String targetType, String targetId, Instant occurredAt) {
    return new AuditLogEntry(
        null, "system", targetType, targetId, "DRAFT_IMPORTED", occurredAt, null, null);
  }

  @Test
  void returns200WithPagedItemsOrderedByOccurredAtDescending() throws Exception {
    repository.save(anEntry("ConfigEngine", "shop.a", Instant.parse("2026-09-01T00:00:00Z")));
    repository.save(anEntry("ConfigEngine", "shop.b", Instant.parse("2026-09-02T00:00:00Z")));

    mockMvc
        .perform(get(ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.items[0].targetId").value("shop.b"))
        .andExpect(jsonPath("$.items[1].targetId").value("shop.a"));
  }

  @Test
  void returns200FilteredByTargetTypeWhenSpecified() throws Exception {
    repository.save(anEntry("ConfigEngine", "shop.a", Instant.parse("2026-09-01T00:00:00Z")));
    repository.save(anEntry("PermissionEngine", "role-2", Instant.parse("2026-09-02T00:00:00Z")));

    mockMvc
        .perform(get(ENDPOINT).param("targetType", "PermissionEngine"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.items[0].targetType").value("PermissionEngine"));
  }

  @Test
  void returns200WithPageSizeLimitingItemCount() throws Exception {
    for (int i = 0; i < 3; i++) {
      repository.save(
          anEntry(
              "ConfigEngine", "shop." + i, Instant.parse("2026-09-0" + (i + 1) + "T00:00:00Z")));
    }

    mockMvc
        .perform(get(ENDPOINT).param("pageSize", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  private static Stream<Arguments> invalidQueryParameters() {
    return Stream.of(
        Arguments.of("page=0", "0", "20", null),
        Arguments.of("negative page", "-1", "20", null),
        Arguments.of("pageSize=0", "1", "0", null),
        Arguments.of("pageSize=101", "1", "101", null),
        Arguments.of("unknown targetType", "1", "20", "UnknownType"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidQueryParameters")
  void returns400ForInvalidQueryParameters(
      String caseName, String page, String pageSize, String targetType) throws Exception {
    var request = get(ENDPOINT).param("page", page).param("pageSize", pageSize);
    if (targetType != null) {
      request = request.param("targetType", targetType);
    }

    mockMvc.perform(request).andExpect(status().isBadRequest());
  }

  @Test
  void returns403AndSkipsQueryWhenPermissionDenied() throws Exception {
    // negative-authorization(team.md必須テスト種別(c)): canAccessScreenがfalseの場合は必ず拒否する。
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("audit-log")))
        .thenReturn(false);

    mockMvc.perform(get(ENDPOINT)).andExpect(status().isForbidden());
  }

  @Test
  void returns403WhenActiveRoleIdIsMissing() throws Exception {
    // アクティブロールが未選択(null)の操作者: 自前で拒否せず、そのままC10へ渡し、権限なし(fail closed)として403
    // (authentication-serviceの機能設計 BR5.12)。
    operators.set(USER_ID, null);

    mockMvc.perform(get(ENDPOINT)).andExpect(status().isForbidden());

    verify(permissionEngineApi).canAccessScreen(null, "audit-log");
  }

  @Test
  void returns403WhenTheOperatorCannotBeResolved() throws Exception {
    operators.clear();

    mockMvc.perform(get(ENDPOINT)).andExpect(status().isForbidden());
  }

  @Test
  void returns503WhenTheInternalConfigDatastoreIsUnavailable() throws Exception {
    doThrow(new QueryTimeoutException("internal config datastore unavailable"))
        .when(repository)
        .findAll(any(Pageable.class));

    mockMvc.perform(get(ENDPOINT)).andExpect(status().isServiceUnavailable());
  }

  @Test
  void returns503WhenTheInternalConfigDatastoreIsUnavailableForAFilteredQuery() throws Exception {
    doThrow(new QueryTimeoutException("internal config datastore unavailable"))
        .when(repository)
        .findByTargetType(anyString(), any(Pageable.class));

    mockMvc
        .perform(get(ENDPOINT).param("targetType", "ConfigEngine"))
        .andExpect(status().isServiceUnavailable());
  }
}
