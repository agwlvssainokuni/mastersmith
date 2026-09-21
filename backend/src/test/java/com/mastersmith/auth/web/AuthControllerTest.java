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

package com.mastersmith.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.auth.dto.LoginResponse;
import com.mastersmith.auth.dto.RefreshResponse;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.LoginFailedException;
import com.mastersmith.auth.exception.RefreshRejectedException;
import com.mastersmith.auth.exception.RequestBodyTooLargeException;
import com.mastersmith.auth.exception.RoleNotHeldException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.service.AuthenticationApplicationService;
import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.TestOperatorContext;
import com.mastersmith.common.security.TestOperatorContextConfig;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@link AuthController}(C4)と{@link
 * AuthApiExceptionAdvice}のテスト({@code @WebMvcTest}、サービスはモック。NFR2.8): 正常系(200・204)、<b>認可拒否専用テスト</b>
 * (未認証の401・保持しないロールの選択の403。team.mdの必須テスト種別(c))、失敗の応答の一本化({@code
 * auth.login.failed})、503(ハッシュ計算の上限超過・DB障害)・400・413(原因の連鎖を たどる)・500、応答に{@code
 * refreshTokenHash}・パスワード・鍵が含まれないこと。
 */
@WebMvcTest(AuthController.class)
@Import(TestOperatorContextConfig.class)
class AuthControllerTest {

  private static final String LOGIN = "/api/auth/login";
  private static final String REFRESH = "/api/auth/refresh";
  private static final String LOGOUT = "/api/auth/logout";
  private static final String ACTIVE_ROLE = "/api/auth/active-role";
  private static final String LOGIN_BODY =
      "{\"email\":\"a@example.test\",\"password\":\"SECRET-PASSWORD-123\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private TestOperatorContext operators;

  @MockitoBean private AuthenticationApplicationService service;

  @BeforeEach
  void noOperatorByDefault() {
    operators.clear();
  }

  // ---- 正常系 ----

  @Test
  void loginReturns200WithTokensRolesAndTheActiveRole() throws Exception {
    when(service.login("a@example.test", "SECRET-PASSWORD-123"))
        .thenReturn(new LoginResponse("access.jwt", "refresh-token", List.of("r1", "r2"), null));

    mockMvc
        .perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access.jwt"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.roles[0]").value("r1"))
        .andExpect(jsonPath("$.roles[1]").value("r2"))
        .andExpect(jsonPath("$.activeRoleId").doesNotExist())
        .andExpect(content().string(not(containsString("refreshTokenHash"))))
        .andExpect(content().string(not(containsString("password"))));
  }

  @Test
  void refreshReturns200WithRotatedTokensAndTheRoleInformation() throws Exception {
    when(service.refresh("old-refresh-token"))
        .thenReturn(
            new RefreshResponse("new.access.jwt", "new-refresh-token", List.of("r1"), "r1"));

    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"old-refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new.access.jwt"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
        .andExpect(jsonPath("$.roles[0]").value("r1"))
        .andExpect(jsonPath("$.activeRoleId").value("r1"));
  }

  @Test
  void logoutReturns204AndRevokesTheSessionOfTheAuthenticatedOperator() throws Exception {
    operators.set(new Operator("u-1", "sid-1", "r1"));

    mockMvc.perform(post(LOGOUT)).andExpect(status().isNoContent());

    verify(service).logout(new Operator("u-1", "sid-1", "r1"));
  }

  @Test
  void activeRoleReturns200WithTheSelectedRole() throws Exception {
    operators.set(new Operator("u-1", "sid-1", null));
    when(service.selectActiveRole(new Operator("u-1", "sid-1", null), "r2")).thenReturn("r2");

    mockMvc
        .perform(
            put(ACTIVE_ROLE).contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"r2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeRoleId").value("r2"));
  }

  // ---- 認可拒否専用テスト(negative-authorization) ----

  @Test
  void logoutAndRoleSelectionReturn401WithTheBearerChallengeWhenNoOperatorIsAuthenticated()
      throws Exception {
    mockMvc
        .perform(post(LOGOUT))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.code").value("auth.token.invalid"));
    mockMvc
        .perform(
            put(ACTIVE_ROLE).contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"r2\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("auth.token.invalid"));

    verifyNoInteractions(service);
  }

  @Test
  void selectingARoleThatIsNotHeldReturns403() throws Exception {
    operators.set(new Operator("u-1", "sid-1", "r1"));
    when(service.selectActiveRole(any(), eq("someone-elses-role")))
        .thenThrow(new RoleNotHeldException());

    mockMvc
        .perform(
            put(ACTIVE_ROLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"someone-elses-role\"}"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("auth.role.not-held"));
  }

  @Test
  void aSessionThatBecameInactiveIsAnAuthenticationFailure() throws Exception {
    operators.set(new Operator("u-1", "sid-1", "r1"));
    when(service.selectActiveRole(any(), anyString())).thenThrow(new SessionExpiredException());

    mockMvc
        .perform(
            put(ACTIVE_ROLE).contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"r1\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
  }

  // ---- 失敗の応答の一本化 ----

  @Test
  void aFailedLoginReturnsTheSingleUnifiedProblemWithoutAnyHintOfTheCause() throws Exception {
    when(service.login(anyString(), anyString())).thenThrow(new LoginFailedException());

    mockMvc
        .perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("auth.login.failed"))
        // typeは、既定(about:blank)の場合、Spring MVCのシリアライズでは省略される(RFC 9457: 省略はabout:blankと同義)。
        // 生のパスではなく、ルートのテンプレート。入力値(パスワード)は含めない。
        .andExpect(jsonPath("$.instance").value(LOGIN))
        .andExpect(content().string(not(containsString("SECRET-PASSWORD-123"))))
        .andExpect(content().string(not(containsString("a@example.test"))))
        // ログイン失敗のBearerの挑戦は付けない(理由を示す情報を、応答に出さない)。
        .andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  @Test
  void aRejectedRefreshReturnsTheSingleUnifiedProblem() throws Exception {
    when(service.refresh(anyString())).thenThrow(new RefreshRejectedException());

    mockMvc
        .perform(
            post(REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("auth.refresh.rejected"));
  }

  @Test
  void emptyRequestBodiesAreHandedToTheServiceAsMissingValues() throws Exception {
    when(service.login(null, null)).thenThrow(new LoginFailedException());

    mockMvc
        .perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("auth.login.failed"));
  }

  // ---- 503・400・413・500 ----

  @Test
  void aHashCapacityExceededAndAStorageFailureBothReturnTheSame503() throws Exception {
    when(service.login(anyString(), anyString()))
        .thenThrow(new HashCapacityExceededException("capacity"))
        .thenThrow(new AuthStorageUnavailableException("db down"));

    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
          .andExpect(status().isServiceUnavailable())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("auth.service.unavailable"))
          // 原因の詳細(例外のメッセージ)は含めない。
          .andExpect(content().string(not(containsString("capacity"))))
          .andExpect(content().string(not(containsString("db down"))));
    }
  }

  @Test
  void aMalformedJsonBodyReturns400WithoutTheDetails() throws Exception {
    mockMvc
        .perform(
            post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": SECRET-BROKEN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("auth.request.malformed"))
        .andExpect(content().string(not(containsString("SECRET-BROKEN"))));
    mockMvc
        .perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("auth.request.malformed"));

    verifyNoInteractions(service);
  }

  /** 読み取りの途中で、{@link RequestBodyTooLargeException}を投げる入力ストリーム(チャンク転送で上限を超えた場合の再現)。 */
  private static RequestPostProcessor bodyThatIsTooLargeWhileReading() {
    return request -> {
      MockHttpServletRequest failing =
          new MockHttpServletRequest(
              request.getServletContext(), request.getMethod(), request.getRequestURI()) {
            @Override
            public ServletInputStream getInputStream() {
              return new ServletInputStream() {
                @Override
                public int read() throws IOException {
                  throw new RequestBodyTooLargeException(64 * 1024);
                }

                @Override
                public boolean isFinished() {
                  return false;
                }

                @Override
                public boolean isReady() {
                  return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {}
              };
            }
          };
      failing.setContentType(MediaType.APPLICATION_JSON_VALUE);
      return failing;
    };
  }

  @Test
  void aBodyThatTurnsOutTooLargeWhileReadingReturns413WhenWrappedByTheJsonReader()
      throws Exception {
    mockMvc
        .perform(post(LOGIN).with(bodyThatIsTooLargeWhileReading()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("auth.request.too-large"));

    verifyNoInteractions(service);
  }

  @Test
  void aBodyThatIsTooLargeIsConvertedTo413WhenTheExceptionPropagatesDirectlyToo() {
    var response =
        new AuthApiExceptionAdvice().handleTooLarge(new MockHttpServletRequest("POST", LOGIN));

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    assertThat(response.getBody().getProperties()).containsEntry("code", "auth.request.too-large");
  }

  @Test
  void anUnexpectedExceptionReturnsA500WithoutDetailsAndWithoutTheExceptionMessage()
      throws Exception {
    when(service.login(anyString(), anyString()))
        .thenThrow(new IllegalStateException("SECRET-INTERNAL-DETAIL jdbc:h2:file:./data"));

    mockMvc
        .perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("auth.internal-error"))
        .andExpect(content().string(not(containsString("SECRET-INTERNAL-DETAIL"))))
        .andExpect(content().string(not(containsString("IllegalStateException"))));
  }

  @Test
  void theEndpointsOnlyAcceptTheirDocumentedMethods() throws Exception {
    // GET /api/auth/loginは、ログインではない(ハンドラがない)。
    mockMvc.perform(get(LOGIN)).andExpect(status().isMethodNotAllowed());
    mockMvc.perform(get(LOGOUT)).andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(service);
  }
}
