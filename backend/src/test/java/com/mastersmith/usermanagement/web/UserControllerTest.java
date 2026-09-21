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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.TestOperatorContext;
import com.mastersmith.common.security.TestOperatorContextConfig;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.dto.InviteUserRequest;
import com.mastersmith.usermanagement.dto.UpdateUserRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.exception.EmailLockTimeoutException;
import com.mastersmith.usermanagement.exception.InvitationCapacityExceededException;
import com.mastersmith.usermanagement.exception.InvitationMailException;
import com.mastersmith.usermanagement.exception.InvitationMailException.Failure;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.UserAccessDeniedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserNotFoundException;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.service.InvitationFacade;
import com.mastersmith.usermanagement.service.UserApplicationService;
import com.mastersmith.usermanagement.testsupport.ChunkedRequests;
import com.mastersmith.usermanagement.testsupport.JsonBodies;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@link UserController}のテスト(C5: {@code /api/users}系、rules.md BR4.10・BR4.14):
 * 200/201/204、**認可拒否専用テスト**(未認証の401・権限なしの403を、
 * 全エンドポイントで確認。team.md必須テスト種別(c))、404、422(フィールド単位・i18nキー・入力値を含まない)、503、413、応答に{@code passwordHash}・
 * {@code invitationToken}が含まれないこと。サービスはモックし、HTTP層(ステータス・ProblemDetails・操作者の解決・リクエストの組み立て)の検証に専念する。
 */
@WebMvcTest(UserController.class)
@Import(TestOperatorContextConfig.class)
class UserControllerTest {

  private static final String USERS = "/api/users";
  private static final Operator ADMIN = TestOperatorContext.operator("admin-1", "admin-role");
  private static final String INVITE_BODY =
      "{\"email\":\"new@example.test\",\"name\":\"新規 太郎\",\"roleIds\":[\"r1\"],\"locale\":\"en\"}";
  private static final String UPDATE_BODY = "{\"name\":\"新しい名前\",\"roleIds\":[\"r1\",\"r2\"]}";

  @Autowired private MockMvc mockMvc;
  @Autowired private TestOperatorContext operators;

  @MockitoBean private UserApplicationService userService;
  @MockitoBean private InvitationFacade invitationFacade;

  @BeforeEach
  void noOperatorByDefault() {
    operators.clear();
  }

  private static UserResponse aUser() {
    return new UserResponse("u-1", "山田 太郎", "yamada@example.test", "active", List.of("r1"));
  }

  /** 以降のリクエストの操作者(C15)を、管理者にする(ヘッダーで渡す暫定の方式は、認証フィルタの導入で削除した)。 */
  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder asAdmin(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
    operators.set(ADMIN);
    return builder;
  }

  // ---- 正常系 ----

  @Test
  void listReturns200WithUsersAndNoSensitiveFields() throws Exception {
    when(userService.list(ADMIN)).thenReturn(List.of(aUser()));

    mockMvc
        .perform(asAdmin(get(USERS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value("u-1"))
        .andExpect(jsonPath("$[0].name").value("山田 太郎"))
        .andExpect(jsonPath("$[0].email").value("yamada@example.test"))
        .andExpect(jsonPath("$[0].status").value("active"))
        .andExpect(jsonPath("$[0].roleIds[0]").value("r1"))
        .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$[0].invitationToken").doesNotExist())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("invitationToken"))));
  }

  @Test
  void inviteReturns201AndPassesTheRequestAndTheOperatorToTheFacade() throws Exception {
    when(invitationFacade.invite(eq(ADMIN), any(InviteUserRequest.class)))
        .thenReturn(new UserResponse("u-2", "新規 太郎", "new@example.test", "invited", List.of("r1")));

    mockMvc
        .perform(asAdmin(post(USERS)).contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value("u-2"))
        .andExpect(jsonPath("$.status").value("invited"))
        .andExpect(jsonPath("$.invitationToken").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    ArgumentCaptor<InviteUserRequest> captor = ArgumentCaptor.forClass(InviteUserRequest.class);
    verify(invitationFacade).invite(eq(ADMIN), captor.capture());
    assertThat(captor.getValue().email()).isEqualTo("new@example.test");
    assertThat(captor.getValue().name()).isEqualTo("新規 太郎");
    assertThat(captor.getValue().roleIds()).containsExactly("r1");
    assertThat(captor.getValue().locale()).isEqualTo("en");
  }

  @Test
  void updateReturns200AndPassesOnlyNameAndRoleIds() throws Exception {
    when(userService.update(eq(ADMIN), eq("u-1"), any(UpdateUserRequest.class)))
        .thenReturn(aUser());

    mockMvc
        .perform(
            asAdmin(put(USERS + "/u-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("u-1"));

    ArgumentCaptor<UpdateUserRequest> captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
    verify(userService).update(eq(ADMIN), eq("u-1"), captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("新しい名前");
    assertThat(captor.getValue().roleIds()).containsExactly("r1", "r2");
    assertThat(captor.getValue().unsupportedFields()).isEmpty();
  }

  @Test
  void fieldsOtherThanNameAndRoleIdsAreCollectedAsUnsupportedInsteadOfBeingIgnored()
      throws Exception {
    when(userService.update(any(), any(), any())).thenReturn(aUser());

    mockMvc
        .perform(
            asAdmin(put(USERS + "/u-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"n\",\"roleIds\":[],\"email\":\"x@example.test\","
                        + "\"status\":\"active\",\"passwordHash\":\"h\",\"invitationToken\":\"t\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<UpdateUserRequest> captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
    verify(userService).update(any(), eq("u-1"), captor.capture());
    assertThat(captor.getValue().unsupportedFields())
        .containsExactlyInAnyOrder("email", "status", "passwordHash", "invitationToken");
  }

  @Test
  void valuesOfTheWrongTypeAreTreatedAsMissingSoTheServiceReportsFieldLevelErrors()
      throws Exception {
    when(userService.update(any(), any(), any())).thenReturn(aUser());

    mockMvc
        .perform(
            asAdmin(put(USERS + "/u-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":123,\"roleIds\":\"r1\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            asAdmin(put(USERS + "/u-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"roleIds\":[\"ok\",5,null]}"))
        .andExpect(status().isOk());

    ArgumentCaptor<UpdateUserRequest> captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
    verify(userService, org.mockito.Mockito.times(2)).update(any(), any(), captor.capture());
    assertThat(captor.getAllValues().get(0).name()).isNull();
    assertThat(captor.getAllValues().get(0).roleIds()).isNull();
    assertThat(captor.getAllValues().get(1).roleIds()).containsExactly("ok", null, null);
  }

  @Test
  void disableReturns204() throws Exception {
    mockMvc.perform(asAdmin(delete(USERS + "/u-1"))).andExpect(status().isNoContent());

    verify(userService).disable(ADMIN, "u-1");
  }

  // ---- 認可拒否専用テスト: 全エンドポイント × (401・403) ----

  private void stubEveryOperationToThrow(RuntimeException exception) {
    when(userService.list(any())).thenThrow(exception);
    when(invitationFacade.invite(any(), any())).thenThrow(exception);
    when(userService.update(any(), any(), any())).thenThrow(exception);
    doThrow(exception).when(userService).disable(any(), any());
  }

  private List<ResultActions> performEveryEndpoint(boolean withHeaders) throws Exception {
    java.util.function.UnaryOperator<
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>
        auth = builder -> withHeaders ? asAdmin(builder) : builder;
    return List.of(
        mockMvc.perform(auth.apply(get(USERS))),
        mockMvc.perform(
            auth.apply(post(USERS)).contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY)),
        mockMvc.perform(
            auth.apply(put(USERS + "/u-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY)),
        mockMvc.perform(auth.apply(delete(USERS + "/u-1"))));
  }

  @Test
  void everyEndpointReturns401WhenTheOperatorCannotBeResolved() throws Exception {
    stubEveryOperationToThrow(new OperatorUnresolvedException());

    for (ResultActions result : performEveryEndpoint(false)) {
      result
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(401));
    }
    // 操作者(C15)を解決できなければ、nullがサービスへ渡され、サービスが拒否する。
    verify(userService).list(null);
    verify(userService).disable(null, "u-1");
  }

  @Test
  void everyEndpointReturns403WhenTheActiveRoleHasNoPermission() throws Exception {
    stubEveryOperationToThrow(new UserAccessDeniedException());

    for (ResultActions result : performEveryEndpoint(true)) {
      result
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Forbidden"));
    }
  }

  // ---- 404 ----

  @Test
  void updateAndDisableReturn404ForAnUnknownUser() throws Exception {
    when(userService.update(any(), eq("ghost"), any())).thenThrow(new UserNotFoundException());
    doThrow(new UserNotFoundException()).when(userService).disable(any(), eq("ghost"));

    mockMvc
        .perform(
            asAdmin(put(USERS + "/ghost"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
    mockMvc.perform(asAdmin(delete(USERS + "/ghost"))).andExpect(status().isNotFound());
  }

  // ---- 422 ----

  @Test
  void validationErrorsAreFieldLevelI18nKeysWithoutTheEnteredValues() throws Exception {
    when(invitationFacade.invite(any(), any()))
        .thenThrow(
            new UserValidationException(
                List.of(
                    UserFieldError.of("email", "user.validation.email.invalid"),
                    UserFieldError.of(
                        "name", "user.validation.name.tooLong", Map.of("max", 100)))));

    mockMvc
        .perform(
            asAdmin(post(USERS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"SECRET-ENTERED-VALUE\",\"name\":\"SECRET-NAME\",\"roleIds\":[]}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors[0].field").value("email"))
        .andExpect(jsonPath("$.errors[0].message").value("user.validation.email.invalid"))
        .andExpect(jsonPath("$.errors[0].params").doesNotExist())
        .andExpect(jsonPath("$.errors[1].field").value("name"))
        .andExpect(jsonPath("$.errors[1].message").value("user.validation.name.tooLong"))
        .andExpect(jsonPath("$.errors[1].params.max").value(100))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
  }

  // ---- 503 ----

  static Stream<Arguments> unavailableOnInvite() {
    return Stream.of(
        Arguments.of("招待の同時実行数の上限", new InvitationCapacityExceededException()),
        Arguments.of("同一emailの排他の待機超過", new EmailLockTimeoutException()),
        Arguments.of(
            "メール送信の失敗",
            new InvitationMailException(Failure.SEND_FAILED, "smtp: secret@host rejected")),
        Arguments.of(
            "DBのロック待ちタイムアウト", new PessimisticLockingFailureException("secret lock detail")),
        Arguments.of("DBのロック取得失敗", new CannotAcquireLockException("secret lock detail")),
        Arguments.of("ハッシュ計算の混雑", new HashCapacityExceededException("secret")));
  }

  @ParameterizedTest(name = "招待: {0}は503")
  @MethodSource("unavailableOnInvite")
  void inviteReturns503WithoutLeakingTheCause(String label, RuntimeException cause)
      throws Exception {
    when(invitationFacade.invite(any(), any())).thenThrow(cause);

    mockMvc
        .perform(asAdmin(post(USERS)).contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
  }

  @Test
  void updateAndDisableReturn503WhenTheRowLockTimesOut() throws Exception {
    when(userService.update(any(), any(), any()))
        .thenThrow(new PessimisticLockingFailureException("lock timeout"));
    doThrow(new PessimisticLockingFailureException("lock timeout"))
        .when(userService)
        .disable(any(), any());

    mockMvc
        .perform(
            asAdmin(put(USERS + "/u-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isServiceUnavailable());
    mockMvc.perform(asAdmin(delete(USERS + "/u-1"))).andExpect(status().isServiceUnavailable());
  }

  // ---- 413・400 ----

  @Test
  void aBodyLargerThan64KiBIsRejectedWith413BeforeAuthenticationAndWithoutReachingTheService()
      throws Exception {
    mockMvc
        .perform(
            post(USERS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.filler(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(413));

    verifyNoInteractions(userService, invitationFacade);
  }

  @Test
  void aChunkedBodyLargerThan64KiBIsRejectedWith413ThroughTheExceptionAdvice() throws Exception {
    mockMvc
        .perform(
            asAdmin(post(USERS))
                .with(ChunkedRequests.chunked())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.inviteBodyOfExactly(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Payload Too Large"));

    verifyNoInteractions(invitationFacade);
  }

  @Test
  void aBodyOfExactly64KiBPassesTheLimit() throws Exception {
    when(invitationFacade.invite(any(), any())).thenReturn(aUser());

    mockMvc
        .perform(
            asAdmin(post(USERS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.inviteBodyOfExactly(64 * 1024)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            asAdmin(post(USERS))
                .with(ChunkedRequests.chunked())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.inviteBodyOfExactly(64 * 1024)))
        .andExpect(status().isCreated());
  }

  @Test
  void malformedJsonIs400WithoutEchoingTheBody() throws Exception {
    mockMvc
        .perform(
            asAdmin(post(USERS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"SECRET-JSON"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
  }

  @Test
  void theInstanceOfAProblemIsTheRouteTemplateNotTheRawPath() throws Exception {
    when(userService.update(any(), any(), any())).thenThrow(new UserNotFoundException());

    mockMvc
        .perform(
            asAdmin(put(USERS + "/raw-user-id-1234"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.instance").value("/api/users/%7BuserId%7D"));
  }
}
