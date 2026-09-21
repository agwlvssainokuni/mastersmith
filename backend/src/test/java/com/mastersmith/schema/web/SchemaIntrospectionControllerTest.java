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

import static org.assertj.core.api.Assertions.assertThat;
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
import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.exception.FieldError;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.schema.dto.SchemaIntrospectionResult;
import com.mastersmith.schema.exception.SchemaDraftValidationException;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import com.mastersmith.schema.service.SchemaIntrospectionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SchemaIntrospectionController}の単体テスト(C8:
 * 正常系200、操作者の未解決401、ロール未選択・権限なし403、メタデータ読み取り失敗・config-engineの拒否422)。{@link
 * SchemaIntrospectionService}・{@link PermissionEngineApi}・{@link
 * TestOperatorContext}で操作者(C15)を供給し、HTTP層(ステータスコード・ProblemDetail形式)の検証に専念する。
 *
 * <p>リクエストボディはSpring管理の{@code ObjectMapper}に依存せず、単純なJSONリテラルとして直接組み立てる({@link
 * SchemaIntrospectionRequest}はschemaName/tableNamesのみを持つ単純な形であるため)。
 */
@WebMvcTest(SchemaIntrospectionController.class)
@ExtendWith(OutputCaptureExtension.class)
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

    verify(permissionEngineApi).canAccessScreen(ACTIVE_ROLE_ID, "config-import-export");
  }

  @Test
  void returns403AndSkipsIntrospectionWhenPermissionDenied() throws Exception {
    operators.set(USER_ID, ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("config-import-export")))
        .thenReturn(false);

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isForbidden());

    verify(permissionEngineApi).canAccessScreen(ACTIVE_ROLE_ID, "config-import-export");
    verifyNoInteractions(service);
  }

  @Test
  void returns403WhenTheOperatorHasNotSelectedAnActiveRole() throws Exception {
    // アクティブロールが未選択(null)の操作者: 自前で拒否せず、そのままC10へ渡し、権限なし(fail closed)として403
    // (authentication-serviceの機能設計 BR5.12)。C10の戻り値は、モックの既定値に頼らず、明示する。
    operators.set(USER_ID, null);
    when(permissionEngineApi.canAccessScreen(null, "config-import-export")).thenReturn(false);

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isForbidden());

    verify(permissionEngineApi).canAccessScreen(null, "config-import-export");
    verifyNoInteractions(service);
  }

  @Test
  void returns401AndNeverAsksTheEngineWhenTheOperatorCannotBeResolved() throws Exception {
    // ブートストラップ状態(RBAC設定が空)では、C10は、config-import-exportに限り、activeRoleIdにかかわらずtrueを返す。
    // それでも、操作者を解決できない(未認証の)要求は、コントローラ自身が401で拒否し、C10も、サービスも、呼ばない。
    operators.clear();
    when(permissionEngineApi.canAccessScreen(any(), any())).thenReturn(true);

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.title").value("Unauthorized"));

    verifyNoInteractions(permissionEngineApi, service);
  }

  @Test
  void anOperatorWithoutAnActiveRoleReachesTheScreenWhileTheRbacConfigIsEmpty() throws Exception {
    // RBAC設定が空の間の例外(config-import-export)は、C10が、activeRoleIdにかかわらず許可する。操作者は解決済み。
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
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.title").value("Schema introspection failed"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void returns422WithFieldErrorsWhenConfigEngineRejectsTheDraft() throws Exception {
    // 未対応の型(uuid、jsonなど)を持つカラムを、config-engineが拒否した場合(R-02): 500ではなく、422。
    operators.set(USER_ID, ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("config-import-export")))
        .thenReturn(true);
    when(service.introspect(any()))
        .thenThrow(
            new SchemaDraftValidationException(
                List.of(new FieldError("rawTypeName:POSTGRESQL", "unsupportedRdbmsType")),
                new ConfigValidationException(
                    List.of(new FieldError("rawTypeName:POSTGRESQL", "unsupportedRdbmsType")))));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST_BODY))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.title").value("Schema introspection rejected"))
        .andExpect(jsonPath("$.errors[0].field").value("rawTypeName:POSTGRESQL"))
        .andExpect(jsonPath("$.errors[0].message").value("unsupportedRdbmsType"))
        .andExpect(jsonPath("$.errors.length()").value(1));
  }

  @Test
  void neverWritesControlCharactersOfClientSuppliedNamesToTheLog(CapturedOutput output)
      throws Exception {
    // R-07: 改行を含むschemaNameで、ログの行を偽装できない(制御文字は、可視のエスケープになる)。
    operators.set(USER_ID, ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(eq(ACTIVE_ROLE_ID), eq("config-import-export")))
        .thenReturn(true);
    when(service.introspect(any())).thenReturn(new SchemaIntrospectionResult(List.of()));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaName\":\"shop\\nFORGED-LOG-ENTRY\",\"tableNames\":[\"a\\rb\"]}"))
        .andExpect(status().isOk());

    assertThat(output.getAll()).contains("shop\\u000aFORGED-LOG-ENTRY").contains("a\\u000db");
    assertThat(output.getAll()).doesNotContain("\nFORGED-LOG-ENTRY");
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
