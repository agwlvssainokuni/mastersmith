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

package com.mastersmith.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.mastersmith.MastersmithApplication;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.entity.SessionStatus;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthTestClockConfig;
import com.mastersmith.auth.testsupport.AuthTestEndpoints;
import com.mastersmith.auth.testsupport.MutableClock;
import com.mastersmith.auth.token.AccessTokenVerifier;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.Role;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 認証の一連の流れの統合テスト(code-generation-plan.md Step
 * 15、{@code @SpringBootTest}、実H2・実際のフィルタチェーン・実際のC11・実際のpermission-engine(C10)): ログイン → 複数ロールの
 * ユーザーのロール選択 → 認証フィルタ越しの検証用コントローラ({@link AuthTestEndpoints}。{@code OperatorContext}(C15)と{@code
 * SessionContextApi}(C14)を読み、{@code PermissionEngineApi}の判定を通す) での権限制御(許可・権限なし(403)・ロール未選択(403)) →
 * リフレッシュ → ログアウト後の401。複数端末の同時ログイン(ログアウトが他のSessionに影響しないこと)、ユーザーの無効化後のリフレッシュの
 * 401(Sessionの失効)、ロックの一連の流れ(しきい値まで誤り → ロック中は正しいパスワードでも同一の401 → 時計を進めて自動解除 → 成功)を含む。
 *
 * <p>複数プロファイル横断E2Eテスト(team.mdの必須テスト種別(b))は、frontend-ui(U12)・統合の担当であり、本ユニットの対象外(認証のコードは、業務ドメインの設定プロファイルに依存しない)。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@AutoConfigureMockMvc
@Import({AuthTestClockConfig.class, AuthTestEndpoints.class})
class AuthenticationFlowIntegrationTest {

  private static final String PASSWORD = "flow correct horse battery";
  private static final String PROTECTED = "/api/auth-test/protected";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PrimaryPermissionRepository primaryPermissionRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private PasswordHasher passwordHasher;
  @Autowired private AccessTokenVerifier accessTokenVerifier;
  @Autowired private MutableClock clock;

  private final List<String> createdEmails = new ArrayList<>();
  private final List<Role> createdRoles = new ArrayList<>();
  private final List<PrimaryPermission> createdPermissions = new ArrayList<>();

  private String allowedRole;
  private String deniedRole;

  @BeforeEach
  void setUp() {
    clock.set(Instant.parse("2026-01-01T00:00:00Z"));
    // 権限のあるロール(検証用の画面へのアクセスが可)と、権限のないロール。
    allowedRole = newRole("flow-allowed");
    deniedRole = newRole("flow-denied");
    createdPermissions.add(
        primaryPermissionRepository.saveAndFlush(
            new PrimaryPermission(
                allowedRole, ScopeType.TABLE, AuthTestEndpoints.SCREEN_KEY, PermissionLevel.READ)));
  }

  @AfterEach
  void tearDown() {
    primaryPermissionRepository.deleteAll(createdPermissions);
    roleRepository.deleteAll(createdRoles);
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

  // ---- 部品 ----

  private String newRole(String prefix) {
    Role role = roleRepository.saveAndFlush(new Role(prefix + "-" + UUID.randomUUID()));
    createdRoles.add(role);
    return role.getRoleId();
  }

  private User newUser(List<String> roles) {
    String email = "flow-" + UUID.randomUUID() + "@example.test";
    createdEmails.add(email);
    return userRepository.saveAndFlush(
        User.activeAdmin("Flow User", email, passwordHasher.hash(PASSWORD), roles));
  }

  /** ログイン・リフレッシュの成功の結果。 */
  private record Tokens(
      String accessToken, String refreshToken, List<String> roles, String activeRoleId) {}

  private static Tokens tokensOf(String body) {
    return new Tokens(
        JsonPath.read(body, "$.accessToken"),
        JsonPath.read(body, "$.refreshToken"),
        JsonPath.read(body, "$.roles"),
        JsonPath.read(body, "$.activeRoleId"));
  }

  private MockHttpServletResponse call(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request).andReturn().getResponse();
  }

  private static String text(MockHttpServletResponse response) throws Exception {
    return response.getContentAsString(StandardCharsets.UTF_8);
  }

  private MockHttpServletResponse loginAs(String email, String password) throws Exception {
    return call(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
  }

  private Tokens login(User user) throws Exception {
    MockHttpServletResponse response = loginAs(user.getEmail(), PASSWORD);
    assertThat(response.getStatus()).as(text(response)).isEqualTo(200);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    return tokensOf(text(response));
  }

  private MockHttpServletResponse refresh(String refreshToken, String authorization)
      throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + refreshToken + "\"}");
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    return call(request);
  }

  private MockHttpServletResponse bearer(MockHttpServletRequestBuilder request, String accessToken)
      throws Exception {
    return call(request.header("Authorization", "Bearer " + accessToken));
  }

  private MockHttpServletResponse selectRole(String accessToken, String roleId) throws Exception {
    return bearer(
        put("/api/auth/active-role")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roleId\":\"" + roleId + "\"}"),
        accessToken);
  }

  private int protectedStatus(String accessToken) throws Exception {
    return bearer(get(PROTECTED), accessToken).getStatus();
  }

  // ---- 一連の流れ ----

  @Test
  void
      loginRoleSelectionPermissionControlRefreshAndLogoutWorkTogetherThroughTheAuthenticationFilter()
          throws Exception {
    User user = newUser(List.of(allowedRole, deniedRole));

    // ログイン: 複数ロールのユーザーは、アクティブロールが未選択(null)。
    Tokens login = login(user);
    assertThat(login.roles()).containsExactlyInAnyOrder(allowedRole, deniedRole);
    assertThat(login.activeRoleId()).isNull();
    assertThat(accessTokenVerifier.verify(login.accessToken()).sub()).isEqualTo(user.getUserId());

    // 認証フィルタ越しに、操作者(C15)が解決される(未選択でも、Operatorは存在する)。
    MockHttpServletResponse whoami = bearer(get("/api/auth-test/whoami"), login.accessToken());
    assertThat(whoami.getStatus()).isEqualTo(200);
    assertThat((String) JsonPath.read(text(whoami), "$.userId")).isEqualTo(user.getUserId());
    assertThat((Object) JsonPath.read(text(whoami), "$.activeRoleId")).isNull();

    // ロール未選択: 401ではなく、権限なし(403)。
    assertThat(protectedStatus(login.accessToken())).isEqualTo(403);

    // 権限のないロールを選択: 403(権限なし)。
    assertThat(selectRole(login.accessToken(), deniedRole).getStatus()).isEqualTo(200);
    assertThat(protectedStatus(login.accessToken())).isEqualTo(403);

    // 保持していないロールの選択: 403で、Sessionは変わらない(直前の選択のまま)。
    assertThat(selectRole(login.accessToken(), "someone-elses-role").getStatus()).isEqualTo(403);
    assertThat(protectedStatus(login.accessToken())).isEqualTo(403);

    // 権限のあるロールを選択: 以降のリクエストへ即時に反映され、許可される。
    MockHttpServletResponse selected = selectRole(login.accessToken(), allowedRole);
    assertThat(selected.getStatus()).isEqualTo(200);
    assertThat((String) JsonPath.read(text(selected), "$.activeRoleId")).isEqualTo(allowedRole);
    MockHttpServletResponse allowed = bearer(get(PROTECTED), login.accessToken());
    assertThat(allowed.getStatus()).isEqualTo(200);
    assertThat((String) JsonPath.read(text(allowed), "$.activeRoleId")).isEqualTo(allowedRole);

    // リフレッシュ: 新しいトークンと、ロール情報(選択中のロール)が返る。選択したロールは、保たれる。
    clock.advance(Duration.ofMinutes(5));
    MockHttpServletResponse refreshed = refresh(login.refreshToken(), null);
    assertThat(refreshed.getStatus()).isEqualTo(200);
    Tokens second = tokensOf(text(refreshed));
    assertThat(second.refreshToken()).isNotEqualTo(login.refreshToken());
    assertThat(second.activeRoleId()).isEqualTo(allowedRole);
    assertThat(second.roles()).containsExactlyInAnyOrder(allowedRole, deniedRole);
    assertThat(protectedStatus(second.accessToken())).isEqualTo(200);

    // 更新済みの古いトークンの再送(猶予内)は、401だけで、Sessionを失効させない。
    assertThat(refresh(login.refreshToken(), null).getStatus()).isEqualTo(401);
    assertThat(protectedStatus(second.accessToken())).isEqualTo(200);

    // ログアウト: 該当のSessionが失効し、以降は、有効期限の前でも401。リフレッシュもできない。
    assertThat(bearer(post("/api/auth/logout"), second.accessToken()).getStatus()).isEqualTo(204);
    MockHttpServletResponse afterLogout = bearer(get(PROTECTED), second.accessToken());
    assertThat(afterLogout.getStatus()).isEqualTo(401);
    assertThat(afterLogout.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(refresh(second.refreshToken(), null).getStatus()).isEqualTo(401);
    // 古いアクセストークン(ログアウト前に発行)も、同じSessionのため401。
    assertThat(protectedStatus(login.accessToken())).isEqualTo(401);
  }

  @Test
  void aUserWithASingleRoleHasItSelectedAutomaticallyAndAUserWithoutRolesIsDeniedNot401()
      throws Exception {
    Tokens single = login(newUser(List.of(allowedRole)));
    Tokens none = login(newUser(List.of()));

    assertThat(single.activeRoleId()).isEqualTo(allowedRole);
    assertThat(protectedStatus(single.accessToken())).isEqualTo(200);
    assertThat(none.roles()).isEmpty();
    assertThat(none.activeRoleId()).isNull();
    // ロールを持たないユーザー: ログインでき、認証は成功し、権限判定の結果として拒否される。
    assertThat(bearer(get("/api/auth-test/whoami"), none.accessToken()).getStatus()).isEqualTo(200);
    assertThat(protectedStatus(none.accessToken())).isEqualTo(403);
  }

  @Test
  void otherUnitsControllersWorkThroughTheSameOperatorContext() throws Exception {
    Tokens tokens = login(newUser(List.of(allowedRole)));

    // user-management(U4): 自分自身の表示設定は、認証済みであれば、誰でも操作できる(ロール選択に依存しない)。
    assertThat(bearer(get("/api/me/preferences"), tokens.accessToken()).getStatus()).isEqualTo(200);
    // menu-navigation(U6): 権限のあるメニューが、なければ空(401ではない)。
    assertThat(bearer(get("/api/menu"), tokens.accessToken()).getStatus()).isEqualTo(200);
    // user-management(U4)の管理者向け操作・audit-logging(U7): 権限がないため、403(401ではない)。
    assertThat(bearer(get("/api/users"), tokens.accessToken()).getStatus()).isEqualTo(403);
    assertThat(bearer(get("/api/audit-log"), tokens.accessToken()).getStatus()).isEqualTo(403);
    // 認証がなければ、いずれも401。
    for (String path :
        List.of("/api/me/preferences", "/api/menu", "/api/users", "/api/audit-log")) {
      assertThat(call(get(path)).getStatus()).as(path).isEqualTo(401);
    }
  }

  // ---- 複数端末の同時ログイン(FR3.2) ----

  @Test
  void loggingOutOfOneDeviceDoesNotAffectTheOtherDevicesOfTheSameUser() throws Exception {
    User user = newUser(List.of(allowedRole));
    Tokens phone = login(user);
    Tokens laptop = login(user);

    String phoneSid = accessTokenVerifier.verify(phone.accessToken()).sid();
    String laptopSid = accessTokenVerifier.verify(laptop.accessToken()).sid();
    assertThat(phoneSid).isNotEqualTo(laptopSid);
    assertThat(protectedStatus(phone.accessToken())).isEqualTo(200);
    assertThat(protectedStatus(laptop.accessToken())).isEqualTo(200);

    assertThat(bearer(post("/api/auth/logout"), phone.accessToken()).getStatus()).isEqualTo(204);

    assertThat(protectedStatus(phone.accessToken())).isEqualTo(401);
    assertThat(protectedStatus(laptop.accessToken())).isEqualTo(200);
    assertThat(sessionRepository.findById(phoneSid).map(Session::getStatus))
        .contains(SessionStatus.REVOKED);
    assertThat(sessionRepository.findById(laptopSid).map(Session::getStatus))
        .contains(SessionStatus.ACTIVE);
    assertThat(refresh(laptop.refreshToken(), null).getStatus()).isEqualTo(200);
  }

  @Test
  void theActiveRoleIsPerSessionSoSelectingOnOneDeviceDoesNotChangeTheOther() throws Exception {
    User user = newUser(List.of(allowedRole, deniedRole));
    Tokens first = login(user);
    Tokens second = login(user);

    assertThat(selectRole(first.accessToken(), allowedRole).getStatus()).isEqualTo(200);

    assertThat(protectedStatus(first.accessToken())).isEqualTo(200);
    assertThat(protectedStatus(second.accessToken())).isEqualTo(403);
  }

  // ---- ユーザーの無効化(FR2.3) ----

  @Test
  void afterTheUserIsDisabledTheNextRefreshIsRejectedAndTheSessionIsRevoked() throws Exception {
    User user = newUser(List.of(allowedRole));
    Tokens tokens = login(user);
    // 無効化の前に発行済みのアクセストークンは、要件どおり、有効期限まで有効(リフレッシュまで、Sessionは失効しない)。
    User loaded = userRepository.findById(user.getUserId()).orElseThrow();
    loaded.disable();
    userRepository.saveAndFlush(loaded);
    assertThat(protectedStatus(tokens.accessToken())).isEqualTo(200);

    // リフレッシュでは、無効化を確認して拒否し、Sessionを失効させる。
    assertThat(refresh(tokens.refreshToken(), null).getStatus()).isEqualTo(401);

    String sid = accessTokenVerifier.verify(tokens.accessToken()).sid();
    assertThat(sessionRepository.findById(sid).map(Session::getStatus))
        .contains(SessionStatus.REVOKED);
    assertThat(protectedStatus(tokens.accessToken())).isEqualTo(401);
    // 無効化されたユーザーは、ログインもできない(同一の401)。
    MockHttpServletResponse loginAttempt = loginAs(user.getEmail(), PASSWORD);
    assertThat(loginAttempt.getStatus()).isEqualTo(401);
    assertThat(JsonPath.<String>read(text(loginAttempt), "$.code")).isEqualTo("auth.login.failed");
  }

  // ---- 期限切れのアクセストークンからの復旧 ----

  @Test
  void anExpiredAccessTokenIsRecoveredByRefreshEvenWhenItIsSentAlongWithTheRefreshRequest()
      throws Exception {
    Tokens tokens = login(newUser(List.of(allowedRole)));
    clock.advance(Duration.ofMinutes(11)); // アクセストークン(10分)は期限切れ。リフレッシュトークン(30分)は有効。

    assertThat(protectedStatus(tokens.accessToken())).isEqualTo(401);
    MockHttpServletResponse refreshed =
        refresh(tokens.refreshToken(), "Bearer " + tokens.accessToken());

    assertThat(refreshed.getStatus()).isEqualTo(200);
    assertThat(protectedStatus(tokensOf(text(refreshed)).accessToken())).isEqualTo(200);
  }

  @Test
  void theSessionEndsWhenTheRefreshTokenExpiresWithoutBeingUsed() throws Exception {
    Tokens tokens = login(newUser(List.of(allowedRole)));
    clock.advance(Duration.ofMinutes(30)); // リフレッシュの有効期限ちょうど

    assertThat(refresh(tokens.refreshToken(), null).getStatus()).isEqualTo(401);
    assertThat(protectedStatus(tokens.accessToken())).isEqualTo(401);
  }

  @Test
  void aRoleRemovedFromTheUserIsDroppedFromTheSessionAtTheNextRefresh() throws Exception {
    User user = newUser(List.of(allowedRole, deniedRole));
    Tokens tokens = login(user);
    assertThat(selectRole(tokens.accessToken(), deniedRole).getStatus()).isEqualTo(200);

    // deniedRoleが外され、残りが1つになった。
    User loaded = userRepository.findById(user.getUserId()).orElseThrow();
    loaded.replaceRoleIds(List.of(allowedRole));
    userRepository.saveAndFlush(loaded);
    Tokens refreshed = tokensOf(text(refresh(tokens.refreshToken(), null)));

    // 選択済みのロールが含まれなくなり、残りがちょうど1つのため、自動選択される。
    assertThat(refreshed.roles()).containsExactly(allowedRole);
    assertThat(refreshed.activeRoleId()).isEqualTo(allowedRole);
    assertThat(protectedStatus(refreshed.accessToken())).isEqualTo(200);
  }

  // ---- アカウントのロック(FR2.7) ----

  @Test
  void theLockFlowLocksAfterTheThresholdRejectsEvenTheCorrectPasswordAndUnlocksAutomatically()
      throws Exception {
    User user = newUser(List.of(allowedRole));
    String unifiedFailure = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      MockHttpServletResponse failed = loginAs(user.getEmail(), "wrong password " + attempt);
      assertThat(failed.getStatus()).isEqualTo(401);
      unifiedFailure = text(failed);
    }

    // ロック中は、正しいパスワードでも、同一の401(ロック状態を漏らさない)。
    MockHttpServletResponse locked = loginAs(user.getEmail(), PASSWORD);
    assertThat(locked.getStatus()).isEqualTo(401);
    assertThat(text(locked)).isEqualTo(unifiedFailure);
    // ロックの期間は、延長されない(ロック中の試行は数えない): 15分後に、解除される。
    clock.advance(Duration.ofMinutes(14));
    assertThat(loginAs(user.getEmail(), PASSWORD).getStatus()).isEqualTo(401);
    clock.advance(Duration.ofMinutes(1));

    MockHttpServletResponse unlocked = loginAs(user.getEmail(), PASSWORD);
    assertThat(unlocked.getStatus()).isEqualTo(200);
    assertThat(protectedStatus(tokensOf(text(unlocked)).accessToken())).isEqualTo(200);
  }

  @Test
  void aSuccessfulLoginResetsTheFailureCountSoNonConsecutiveFailuresNeverLock() throws Exception {
    User user = newUser(List.of(allowedRole));
    for (int round = 0; round < 3; round++) {
      for (int attempt = 0; attempt < 4; attempt++) {
        assertThat(loginAs(user.getEmail(), "wrong " + round + attempt).getStatus()).isEqualTo(401);
      }
      // しきい値(5回)に達する前に、正しいパスワードで成功すると、回数が0に戻る。
      assertThat(loginAs(user.getEmail(), PASSWORD).getStatus()).isEqualTo(200);
    }
  }
}
