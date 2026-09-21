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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.common.security.TestOperatorContext;
import com.mastersmith.common.security.TestOperatorContextConfig;
import com.mastersmith.common.security.TestPermitAllSecurityConfig;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.UserAccountLookupApi;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.mail.InvitationMailer;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.testsupport.JsonBodies;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * user-managementのAPIの、アプリケーション全体の結線での統合テスト(実際のフィルタチェーン・例外変換・サービス・H2):
 * **未認証の401・権限なしの403を全エンドポイントで**確認し
 * (team.md必須テスト種別(c))、招待→受諾(認証不要)→更新→表示設定→無効化の一連の流れで、応答・ProblemDetailsに{@code
 * passwordHash}・招待トークンが現れないこと、
 * 認証前のボディが413になること(フィルタの順序)を確認する。PermissionEngineApi(C10)と招待メールの送信はモックする。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@AutoConfigureMockMvc
@Import({TestOperatorContextConfig.class, TestPermitAllSecurityConfig.class})
class UserApiIntegrationTest {

  private static final String ADMIN_ID = "it-admin-user";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private UserAccountLookupApi userAccountLookupApi;

  /** 操作者(C15)の供給。ヘッダーで操作者を渡す暫定の方式は、認証フィルタの導入で削除した(authentication-serviceの機能設計 W7)。 */
  @Autowired private TestOperatorContext operators;

  @MockitoBean private PermissionEngineApi permissionEngineApi;
  @MockitoBean private InvitationMailer invitationMailer;

  private final List<String> createdEmails = new ArrayList<>();

  @BeforeEach
  void setUp() {
    operators.clear();
    when(permissionEngineApi.canAccessScreen("admin-role", "user-management")).thenReturn(true);
    when(permissionEngineApi.canAccessScreen("viewer-role", "user-management")).thenReturn(false);
    when(permissionEngineApi.roleExists(anyString())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    operators.clear();
    for (String email : createdEmails) {
      userRepository
          .findByEmail(email)
          .ifPresent(
              user -> {
                preferenceRepository.deleteById(user.getUserId());
                userRepository.delete(user);
              });
    }
  }

  /** 以降のリクエストの操作者(userId・activeRoleId)を設定する。 */
  private MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder builder, String userId, String roleId) {
    operators.set(userId, roleId);
    return builder;
  }

  private String newEmail() {
    String email = UserTestFactory.uniqueEmail();
    createdEmails.add(email);
    return email;
  }

  // ---- 認可拒否専用テスト(全エンドポイント) ----

  @Test
  void everyAdminEndpointReturns401WithoutAnOperatorAndNothingIsChanged() throws Exception {
    long usersBefore = userRepository.count();
    String body = "{\"email\":\"" + newEmail() + "\",\"name\":\"n\",\"roleIds\":[]}";

    mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mockMvc
        .perform(
            put("/api/users/any")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"roleIds\":[]}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/users/any")).andExpect(status().isUnauthorized());
    // userIdだけ(activeRoleIdが未選択)の操作者は、401にせず、そのままC10へ渡し、権限なしとして403(ロール選択前。BR5.12)。
    mockMvc.perform(as(get("/api/users"), ADMIN_ID, null)).andExpect(status().isForbidden());

    assertThat(userRepository.count()).isEqualTo(usersBefore);
  }

  @Test
  void everyAdminEndpointReturns403ForARoleWithoutThePermissionAndNothingIsChanged()
      throws Exception {
    long usersBefore = userRepository.count();
    User target =
        userRepository.saveAndFlush(UserTestFactory.activeUser(newEmail(), List.of("r1")));
    String body = "{\"email\":\"" + newEmail() + "\",\"name\":\"n\",\"roleIds\":[]}";

    mockMvc
        .perform(as(get("/api/users"), ADMIN_ID, "viewer-role"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            as(post("/api/users"), ADMIN_ID, "viewer-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Forbidden"));
    mockMvc
        .perform(
            as(put("/api/users/" + target.getUserId()), ADMIN_ID, "viewer-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"変更\",\"roleIds\":[\"r1\",\"r-admin\"]}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(delete("/api/users/" + target.getUserId()), ADMIN_ID, "viewer-role"))
        .andExpect(status().isForbidden());

    User unchanged = userRepository.findById(target.getUserId()).orElseThrow();
    assertThat(unchanged.getName()).isEqualTo("有効 花子");
    assertThat(unchanged.getRoleIds()).containsExactly("r1");
    assertThat(unchanged.getStatus().value()).isEqualTo("active");
    assertThat(userRepository.count()).isEqualTo(usersBefore + 1);
  }

  @Test
  void thePreferencesEndpointsReturn401WithoutAUserIdButNeedNoRolePermission() throws Exception {
    mockMvc.perform(get("/api/me/preferences")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"dark\",\"fontSize\":\"large\",\"locale\":\"en\"}"))
        .andExpect(status().isUnauthorized());

    User user = userRepository.saveAndFlush(UserTestFactory.activeUser(newEmail(), List.of()));
    // 権限のないロール・ロール未選択でも、自分自身の設定は操作できる(既定値を返す)。
    mockMvc
        .perform(as(get("/api/me/preferences"), user.getUserId(), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("light"))
        .andExpect(jsonPath("$.fontSize").value("medium"))
        .andExpect(jsonPath("$.locale").value("ja"));
  }

  // ---- 認証前のボディの拒否(フィルタの順序) ----

  @Test
  void anOversizedBodyIsRejectedWith413BeforeAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.filler(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mockMvc
        .perform(
            post("/api/users/invitations/some-token/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.filler(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().string(not(containsString("some-token"))));
    // 他ユニットのURLは、このフィルタの対象外(413にならない)。
    mockMvc
        .perform(get("/api/menu").content(JsonBodies.filler(64 * 1024 + 1)))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(413));
  }

  // ---- 招待から無効化までの一連の流れ ----

  @Test
  void theInvitationLifecycleNeverExposesThePasswordHashOrTheInvitationToken() throws Exception {
    String email = newEmail();

    String inviteResponse =
        mockMvc
            .perform(
                as(post("/api/users"), ADMIN_ID, "admin-role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"  "
                            + email.toUpperCase()
                            + " \",\"name\":\" 統合 太郎 \",\"roleIds\":[\"r1\"],\"locale\":\"en\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.name").value("統合 太郎"))
            .andExpect(jsonPath("$.status").value("invited"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    User invited = userRepository.findByEmail(email).orElseThrow();
    String token = invited.getInvitationToken();
    assertThat(token).isNotBlank();
    assertThat(inviteResponse)
        .doesNotContain(token)
        .doesNotContain("passwordHash")
        .doesNotContain("invitationToken");

    String listResponse =
        mockMvc
            .perform(as(get("/api/users"), ADMIN_ID, "admin-role"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.email=='" + email + "')].status").value("invited"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(listResponse)
        .doesNotContain(token)
        .doesNotContain("passwordHash")
        .doesNotContain("invitationToken");

    // 認証なしで、招待を受諾する。
    String acceptResponse =
        mockMvc
            .perform(
                post("/api/users/invitations/" + token + "/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"password\":\"integration passphrase\",\"name\":\"受諾"
                            + " 太郎\",\"theme\":\"dark\",\"fontSize\":\"small\",\"locale\":\"en\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.name").value("受諾 太郎"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(acceptResponse)
        .doesNotContain(token)
        .doesNotContain("integration passphrase")
        .doesNotContain("$argon2");
    assertThat(
            userAccountLookupApi.verifyPasswordHash(invited.getUserId(), "integration passphrase"))
        .isTrue();

    // 使用済みのトークンは404。ProblemDetailsのinstanceにも、応答にも、トークンは現れない。
    mockMvc
        .perform(
            post("/api/users/invitations/" + token + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"integration passphrase\",\"name\":\"n\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.instance").value("/api/users/invitations/%7Btoken%7D/accept"))
        .andExpect(content().string(not(containsString(token))));

    // 受諾後の表示設定(受諾時の値)。操作者は、そのUser自身(ロール未選択)。
    mockMvc
        .perform(as(get("/api/me/preferences"), invited.getUserId(), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("dark"))
        .andExpect(jsonPath("$.fontSize").value("small"))
        .andExpect(jsonPath("$.locale").value("en"));
    mockMvc
        .perform(
            as(put("/api/me/preferences"), invited.getUserId(), null)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"light\",\"fontSize\":\"large\",\"locale\":\"ja\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("light"));

    // 管理者による更新。更新できない項目(email)の指定は、無視されずに422(フィールド単位)。
    mockMvc
        .perform(
            as(put("/api/users/" + invited.getUserId()), ADMIN_ID, "admin-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"更新後\",\"roleIds\":[\"r1\",\"r2\"],\"email\":\"changed@example.test\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("email"))
        .andExpect(jsonPath("$.errors[0].message").value("user.validation.field.unsupported"));
    mockMvc
        .perform(
            as(put("/api/users/" + invited.getUserId()), ADMIN_ID, "admin-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"更新後\",\"roleIds\":[\"r1\",\"r2\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("更新後"))
        .andExpect(jsonPath("$.roleIds.length()").value(2));

    // 無効化。以後、C11のisDisabledはtrue。すでにdisabledへの再実行は冪等に204。
    mockMvc
        .perform(as(delete("/api/users/" + invited.getUserId()), ADMIN_ID, "admin-role"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(as(delete("/api/users/" + invited.getUserId()), ADMIN_ID, "admin-role"))
        .andExpect(status().isNoContent());
    assertThat(userAccountLookupApi.isDisabled(invited.getUserId())).isTrue();
    assertThat(
            userAccountLookupApi.verifyPasswordHash(invited.getUserId(), "integration passphrase"))
        .isFalse();
    // 存在しないUserは404。
    mockMvc
        .perform(as(delete("/api/users/no-such-user"), ADMIN_ID, "admin-role"))
        .andExpect(status().isNotFound());
  }

  @Test
  void aValidationErrorNeverEchoesTheEnteredValues() throws Exception {
    mockMvc
        .perform(
            as(post("/api/users"), ADMIN_ID, "admin-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"SECRET-NOT-AN-EMAIL\",\"name\":\"SECRET\\n"
                        + "NAME\",\"roleIds\":[]}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("email"))
        .andExpect(jsonPath("$.errors[0].message").value("user.validation.email.invalid"))
        .andExpect(jsonPath("$.errors[1].field").value("name"))
        .andExpect(jsonPath("$.errors[1].message").value("user.validation.name.controlCharacter"))
        .andExpect(content().string(not(containsString("SECRET"))));
  }
}
