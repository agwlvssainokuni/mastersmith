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

package com.mastersmith.usermanagement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.usermanagement.dto.UserPreferenceDto;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.security.HeaderCurrentOperatorProvider;
import com.mastersmith.usermanagement.security.Operator;
import com.mastersmith.usermanagement.service.UserPreferenceService;
import com.mastersmith.usermanagement.testsupport.JsonBodies;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link MePreferencesController}のテスト(C5: {@code /api/me/preferences}、rules.md BR4.8):
 * 200・未認証の401・422・413、activeRoleIdに依存しないこと、
 * **他人の設定を操作できないこと**(対象のuserIdは、ヘッダー由来の操作者のみで、ボディ・クエリからは受け取らない)。
 */
@WebMvcTest(MePreferencesController.class)
@Import(HeaderCurrentOperatorProvider.class)
class MePreferencesControllerTest {

  private static final String ENDPOINT = "/api/me/preferences";
  private static final String PUT_BODY =
      "{\"theme\":\"dark\",\"fontSize\":\"large\",\"locale\":\"en\"}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserPreferenceService preferenceService;

  @Test
  void getReturns200ForAnOperatorWithoutAnActiveRole() throws Exception {
    when(preferenceService.get(new Operator("user-1", null)))
        .thenReturn(new UserPreferenceDto("dark", "large", "en"));

    mockMvc
        .perform(get(ENDPOINT).header("X-User-Id", "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("dark"))
        .andExpect(jsonPath("$.fontSize").value("large"))
        .andExpect(jsonPath("$.locale").value("en"));
  }

  @Test
  void putReturns200AndPassesTheRequestedValues() throws Exception {
    when(preferenceService.update(
            eq(new Operator("user-1", "any-role")), any(UserPreferenceDto.class)))
        .thenReturn(new UserPreferenceDto("dark", "large", "en"));

    mockMvc
        .perform(
            put(ENDPOINT)
                .header("X-User-Id", "user-1")
                .header("X-Active-Role-Id", "any-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PUT_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("dark"));

    ArgumentCaptor<UserPreferenceDto> captor = ArgumentCaptor.forClass(UserPreferenceDto.class);
    verify(preferenceService).update(any(), captor.capture());
    assertThat(captor.getValue()).isEqualTo(new UserPreferenceDto("dark", "large", "en"));
  }

  @Test
  void theTargetUserIsAlwaysTheOperatorNeverAUserIdFromTheBodyOrTheQuery() throws Exception {
    when(preferenceService.update(any(), any()))
        .thenReturn(new UserPreferenceDto("dark", "large", "en"));
    when(preferenceService.get(any())).thenReturn(new UserPreferenceDto("dark", "large", "en"));

    mockMvc
        .perform(
            put(ENDPOINT)
                .param("userId", "victim-user")
                .header("X-User-Id", "attacker-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\"victim-user\",\"theme\":\"dark\",\"fontSize\":\"large\",\"locale\":\"en\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(ENDPOINT).param("userId", "victim-user").header("X-User-Id", "attacker-user"))
        .andExpect(status().isOk());

    ArgumentCaptor<Operator> putOperator = ArgumentCaptor.forClass(Operator.class);
    verify(preferenceService).update(putOperator.capture(), any());
    assertThat(putOperator.getValue().userId()).isEqualTo("attacker-user");
    ArgumentCaptor<Operator> getOperator = ArgumentCaptor.forClass(Operator.class);
    verify(preferenceService).get(getOperator.capture());
    assertThat(getOperator.getValue().userId()).isEqualTo("attacker-user");
  }

  @Test
  void bothEndpointsReturn401WhenTheOperatorCannotBeResolved() throws Exception {
    when(preferenceService.get(Operator.unresolved())).thenThrow(new OperatorUnresolvedException());
    when(preferenceService.update(eq(Operator.unresolved()), any()))
        .thenThrow(new OperatorUnresolvedException());

    mockMvc
        .perform(get(ENDPOINT))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mockMvc
        .perform(put(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(PUT_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validationErrorsAreFieldLevelKeysWithoutTheEnteredValues() throws Exception {
    when(preferenceService.update(any(), any()))
        .thenThrow(
            new UserValidationException(
                List.of(UserFieldError.of("theme", "user.validation.theme.invalid"))));

    mockMvc
        .perform(
            put(ENDPOINT)
                .header("X-User-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"SECRET-THEME\",\"fontSize\":\"large\",\"locale\":\"en\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("theme"))
        .andExpect(jsonPath("$.errors[0].message").value("user.validation.theme.invalid"))
        .andExpect(content().string(not(containsString("SECRET-THEME"))));
  }

  @Test
  void anOversizedBodyIs413WithoutReachingTheService() throws Exception {
    mockMvc
        .perform(
            put(ENDPOINT)
                .header("X-User-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.filler(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge());

    verifyNoInteractions(preferenceService);
  }
}
