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

package com.mastersmith.schema.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.common.security.TestOperatorContext;
import com.mastersmith.common.security.TestOperatorContextConfig;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.schema.dto.SchemaIntrospectionResult;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import com.mastersmith.schema.service.SchemaIntrospectionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SchemaIntrospectionController}の単体テスト(C8: 正常系200、権限拒否403、メタデータ読み取り失敗422)。{@link
 * SchemaIntrospectionService}・{@link PermissionEngineApi}・{@link
 * TestOperatorContext}で操作者(C15)を供給し、HTTP層(ステータスコード・ProblemDetail形式)の検証に専念する。
 *
 * <p>リクエストボディはSpring管理の{@code ObjectMapper}に依存せず、単純なJSONリテラルとして直接組み立てる({@link
 * SchemaIntrospectionRequest}はschemaName/tableNamesのみを持つ単純な形であるため)。
 */
@WebMvcTest(SchemaIntrospectionController.class)
@Import(TestOperatorContextConfig.class)
// SchemaIntrospectionControllerは他ユニットのCsvExportService等と同じ@ConditionalOnPropertyガード付きのため、
// このスライステストでのみ有効化する(businessDataSource自体はこのスライスの対象外なので実接続は発生しない)。
@TestPropertySource(properties = "mastersmith.business-datasource.enabled=true")
class SchemaIntrospectionControllerTest {

  private static final String ENDPOINT = "/api/config/schema-introspection";
  private static final String USER_ID = "user-1";
  private static final String ACTIVE_ROLE_ID = "role-1";
  private static final String VALID_REQUEST_BODY = "{\"schemaName\":\"shop\"}";
  private static final String BLANK_SCHEMA_NAME_REQUEST_BODY = "{\"schemaName\":\"\"}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SchemaIntrospectionService service;
  @MockitoBean private PermissionEngineApi permissionEngineApi;
  @Autowired private TestOperatorContext operators;

  @BeforeEach
  void noOperatorByDefault() {
    operators.clear();
  }

  @TestConfiguration
  static class MeterRegistryTestConfig {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Test
  void returns200WithGeneratedIdsWhenAuthorizedAndReadSucceeds() throws Exception {
    operators.set(USER_ID, ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("config-import-export")))
        .thenReturn(true);
    when(service.introspect(any()))
        .thenReturn(new SchemaIntrospectionResult(List.of("table-config-1")));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generatedTableConfigIds[0]").value("table-config-1"));
  }

  @Test
  void returns403AndSkipsIntrospectionWhenPermissionDenied() throws Exception {
    operators.set(USER_ID, ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("config-import-export")))
        .thenReturn(false);

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void returns403WhenActiveRoleIdIsMissing() throws Exception {
    // アクティブロールが未選択(null)の操作者: 自前で拒否せず、そのままC10へ渡し、権限なし(fail closed)として403
    // (authentication-serviceの機能設計 BR5.12)。
    operators.set(USER_ID, null);

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
    verify(permissionEngineApi).canAccessScreen(null, "config-import-export");
  }

  @Test
  void returns403WhenTheOperatorCannotBeResolved() throws Exception {
    operators.clear();

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void aRoleWithoutAnActiveRoleCanReachTheScreenWhileTheRbacConfigIsEmpty() throws Exception {
    // RBAC設定が空の間の例外(config-import-export)は、C10が、activeRoleIdにかかわらず許可する。
    operators.set(USER_ID, null);
    when(permissionEngineApi.canAccessScreen(null, "config-import-export")).thenReturn(true);
    when(service.introspect(any()))
        .thenReturn(new SchemaIntrospectionResult(List.of("table-config-1")));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isOk());
  }

  @Test
  void returns422WhenMetadataReadFails() throws Exception {
    operators.set(USER_ID, ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("config-import-export")))
        .thenReturn(true);
    when(service.introspect(any()))
        .thenThrow(new SchemaIntrospectionException("Failed to connect to the business database"));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void returns422WhenSchemaNameIsBlank() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BLANK_SCHEMA_NAME_REQUEST_BODY))
        .andExpect(status().is4xxClientError());

    verifyNoInteractions(service);
  }
}
