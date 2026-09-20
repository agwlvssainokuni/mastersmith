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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.dto.AcceptInvitationRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.exception.InvitationTokenNotFoundException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.service.InvitationAcceptService;
import com.mastersmith.usermanagement.testsupport.ChunkedRequests;
import com.mastersmith.usermanagement.testsupport.JsonBodies;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link InvitationAcceptController}のテスト(C5: {@code POST
 * /api/users/invitations/{token}/accept}、rules.md BR4.2・BR4.3、NFR2.4・NFR2.10): 認証不要の200、
 * 未知・使用済み・取消済みのトークンが同一の404になること、422(フィールド単位・入力値を含まない)、503(ハッシュ計算の待機超過)、413、 ProblemDetailsの{@code
 * instance}と応答に、招待トークンが現れないこと。
 */
@WebMvcTest(InvitationAcceptController.class)
class InvitationAcceptControllerTest {

  private static final String TOKEN = "11111111-2222-3333-4444-555555555555";
  private static final String OTHER_TOKEN = "99999999-8888-7777-6666-555555555555";
  private static final String ACCEPT_ROUTE_INSTANCE = "/api/users/invitations/%7Btoken%7D/accept";
  private static final String ACCEPT_BODY =
      "{\"password\":\"correct horse battery\",\"name\":\"受諾"
          + " 花子\",\"theme\":\"dark\",\"fontSize\":\"large\",\"locale\":\"en\"}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InvitationAcceptService acceptService;

  private static String acceptPath(String token) {
    return "/api/users/invitations/" + token + "/accept";
  }

  @Test
  void acceptReturns200WithoutAuthenticationAndPassesTheTokenAndTheRequest() throws Exception {
    when(acceptService.accept(eq(TOKEN), any(AcceptInvitationRequest.class)))
        .thenReturn(
            new UserResponse("u-1", "受諾 花子", "hanako@example.test", "active", List.of("r1")));

    mockMvc
        .perform(
            post(acceptPath(TOKEN)).contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.invitationToken").doesNotExist())
        .andExpect(content().string(not(containsString(TOKEN))))
        .andExpect(content().string(not(containsString("correct horse battery"))));

    ArgumentCaptor<AcceptInvitationRequest> captor =
        ArgumentCaptor.forClass(AcceptInvitationRequest.class);
    verify(acceptService).accept(eq(TOKEN), captor.capture());
    assertThat(captor.getValue().password()).isEqualTo("correct horse battery");
    assertThat(captor.getValue().name()).isEqualTo("受諾 花子");
    assertThat(captor.getValue().theme()).isEqualTo("dark");
    assertThat(captor.getValue().fontSize()).isEqualTo("large");
    assertThat(captor.getValue().locale()).isEqualTo("en");
  }

  @Test
  void displaySettingsAreOptionalInTheRequest() throws Exception {
    when(acceptService.accept(any(), any()))
        .thenReturn(new UserResponse("u-1", "n", "a@example.test", "active", List.of()));

    mockMvc
        .perform(
            post(acceptPath(TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"correct horse battery\",\"name\":\"n\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<AcceptInvitationRequest> captor =
        ArgumentCaptor.forClass(AcceptInvitationRequest.class);
    verify(acceptService).accept(eq(TOKEN), captor.capture());
    assertThat(captor.getValue().theme()).isNull();
    assertThat(captor.getValue().fontSize()).isNull();
    assertThat(captor.getValue().locale()).isNull();
  }

  @Test
  void unknownUsedAndCancelledTokensGetTheIdenticalProblemWithoutTheToken() throws Exception {
    when(acceptService.accept(any(), any())).thenThrow(new InvitationTokenNotFoundException());

    String first =
        mockMvc
            .perform(
                post(acceptPath(TOKEN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ACCEPT_BODY))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.instance").value(ACCEPT_ROUTE_INSTANCE))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        mockMvc
            .perform(
                post(acceptPath(OTHER_TOKEN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ACCEPT_BODY))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // 未知・使用済み・取消済みを区別しない(応答が、トークンによらず同一)。招待トークンは、応答のどこにも現れない。
    assertThat(first).isEqualTo(second);
    assertThat(first).doesNotContain(TOKEN).doesNotContain(OTHER_TOKEN);
  }

  @Test
  void validationErrorsAreFieldLevelKeysWithoutThePassword() throws Exception {
    when(acceptService.accept(any(), any()))
        .thenThrow(
            new UserValidationException(
                UserFieldError.of(
                    "password", "user.validation.password.length", Map.of("min", 8, "max", 128))));

    mockMvc
        .perform(
            post(acceptPath(TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SECRET7\",\"name\":\"n\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(jsonPath("$.errors[0].message").value("user.validation.password.length"))
        .andExpect(jsonPath("$.errors[0].params.min").value(8))
        .andExpect(jsonPath("$.errors[0].params.max").value(128))
        .andExpect(jsonPath("$.instance").value(ACCEPT_ROUTE_INSTANCE))
        .andExpect(content().string(not(containsString("SECRET7"))))
        .andExpect(content().string(not(containsString(TOKEN))));
  }

  @Test
  void aBusyHashCapacityIs503WithoutTheToken() throws Exception {
    when(acceptService.accept(any(), any())).thenThrow(new HashCapacityExceededException("busy"));

    mockMvc
        .perform(
            post(acceptPath(TOKEN)).contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.instance").value(ACCEPT_ROUTE_INSTANCE))
        .andExpect(content().string(not(containsString(TOKEN))));
  }

  @Test
  void anOversizedBodyIs413WithoutTheTokenAndWithoutReachingTheService() throws Exception {
    mockMvc
        .perform(
            post(acceptPath(TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.filler(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().string(not(containsString(TOKEN))));

    verifyNoInteractions(acceptService);
  }

  @Test
  void anOversizedChunkedBodyIs413ThroughTheAdviceWithTheRouteTemplateAsInstance()
      throws Exception {
    mockMvc
        .perform(
            post(acceptPath(TOKEN))
                .with(ChunkedRequests.chunked())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.inviteBodyOfExactly(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.instance").value(ACCEPT_ROUTE_INSTANCE))
        .andExpect(content().string(not(containsString(TOKEN))));

    verifyNoInteractions(acceptService);
  }

  @Test
  void malformedJsonIs400WithTheRouteTemplateAsInstance() throws Exception {
    mockMvc
        .perform(
            post(acceptPath(TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.instance").value(ACCEPT_ROUTE_INSTANCE))
        .andExpect(content().string(not(containsString(TOKEN))));
  }
}
