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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.auth.SessionContextApi;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.exception.SessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link AuthCrossCuttingExceptionAdvice}のテスト(security-design.md NFR2.8・reliability-design.md
 * NFR4.2): 他ユニット(list-engine(U10)・record-edit-engine(U11)を想定した 検証用のコントローラ)のリクエスト処理の中で、C14({@link
 * SessionContextApi#getActiveRoleId})の{@link SessionNotFoundException}・{@link
 * SessionExpiredException}が401、{@link
 * AuthStorageUnavailableException}が503になること。対象は、U5自身の3つの例外の型に限る(他の例外は、変換しない)。
 */
@WebMvcTest(controllers = AuthCrossCuttingExceptionAdviceTest.OtherUnitController.class)
@Import(AuthCrossCuttingExceptionAdviceTest.OtherUnitController.class)
class AuthCrossCuttingExceptionAdviceTest {

  /** list-engine・record-edit-engineの代役。C14を呼び、例外を握りつぶさず、そのまま伝播させる。 */
  @RestController
  static class OtherUnitController {
    private final SessionContextApi sessionContextApi;

    OtherUnitController(SessionContextApi sessionContextApi) {
      this.sessionContextApi = sessionContextApi;
    }

    @GetMapping("/api/other-unit/{sessionId}/rows")
    String rows(@PathVariable String sessionId) {
      return sessionContextApi.getActiveRoleId(sessionId);
    }

    @GetMapping("/api/other-unit/bug")
    String bug() {
      throw new IllegalStateException("not an authentication exception");
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SessionContextApi sessionContextApi;

  @Test
  void aSessionThatIsNotFoundOrNotValidBecomesA401WithTheBearerChallenge() throws Exception {
    org.mockito.Mockito.when(sessionContextApi.getActiveRoleId("gone"))
        .thenThrow(new SessionNotFoundException());
    org.mockito.Mockito.when(sessionContextApi.getActiveRoleId("revoked"))
        .thenThrow(new SessionExpiredException());

    for (String sessionId : new String[] {"gone", "revoked"}) {
      mockMvc
          .perform(get("/api/other-unit/" + sessionId + "/rows"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(header().string("WWW-Authenticate", "Bearer"))
          .andExpect(jsonPath("$.code").value("auth.token.invalid"))
          // 生のパス(セッションIDを含む)ではなく、ルートのテンプレート。
          .andExpect(jsonPath("$.instance").value("/api/other-unit/%7BsessionId%7D/rows"))
          .andExpect(content().string(not(containsString(sessionId))));
    }
  }

  @Test
  void aStorageFailureBecomesA503() throws Exception {
    org.mockito.Mockito.when(sessionContextApi.getActiveRoleId("s1"))
        .thenThrow(new AuthStorageUnavailableException("db down"));

    mockMvc
        .perform(get("/api/other-unit/s1/rows"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().doesNotExist("WWW-Authenticate"))
        .andExpect(jsonPath("$.code").value("auth.service.unavailable"))
        .andExpect(content().string(not(containsString("db down"))));
  }

  @Test
  void onlyTheThreeExceptionTypesOfTheAuthenticationServiceAreConverted() {
    // 他の例外は、この助言の対象外(他ユニットの例外変換と衝突しない)。
    assertThatThrownBy(() -> mockMvc.perform(get("/api/other-unit/bug")))
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void theAdviceHasNoDependenciesSoItLoadsInAnyContext() {
    // 依存を持たない(スライスのテストを含め、どのコンテキストでも、追加の部品なしに読み込める)。
    assertThat(AuthCrossCuttingExceptionAdvice.class.getConstructors()).hasSize(1);
    assertThat(AuthCrossCuttingExceptionAdvice.class.getConstructors()[0].getParameterCount())
        .isZero();
  }
}
