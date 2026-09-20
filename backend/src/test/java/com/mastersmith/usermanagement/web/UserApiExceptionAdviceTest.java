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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.exception.EmailLockTimeoutException;
import com.mastersmith.usermanagement.exception.InvitationCapacityExceededException;
import com.mastersmith.usermanagement.exception.InvitationMailException;
import com.mastersmith.usermanagement.exception.InvitationMailException.Failure;
import com.mastersmith.usermanagement.exception.InvitationTokenNotFoundException;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.RequestBodyTooLargeException;
import com.mastersmith.usermanagement.exception.SubjectRejectedException;
import com.mastersmith.usermanagement.exception.SubjectRejectedException.Rejection;
import com.mastersmith.usermanagement.exception.UserAccessDeniedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserNotFoundException;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.security.HeaderCurrentOperatorProvider;
import com.mastersmith.usermanagement.service.InvitationFacade;
import com.mastersmith.usermanagement.service.UserApplicationService;
import com.mastersmith.usermanagement.testsupport.ChunkedRequests;
import com.mastersmith.usermanagement.testsupport.JsonBodies;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@link UserApiExceptionAdvice}のテスト(security-design.md NFR2.9): 例外と、RFC
 * 9457のProblemDetails(ステータス・タイトル)の対応(テーブル駆動)、応答に例外の メッセージ・型名・スタックトレースを含めないこと、{@code
 * HttpMessageNotReadableException}の原因の判別(413)、{@code instance}のルートのテンプレート化、
 * 対象がU4のコントローラに限定されていること、{@code @Order}。
 */
@WebMvcTest(
    controllers = {UserController.class, UserApiExceptionAdviceTest.OtherUnitController.class})
@Import({HeaderCurrentOperatorProvider.class, UserApiExceptionAdviceTest.OtherUnitController.class})
class UserApiExceptionAdviceTest {

  /** U4以外のコントローラ(他ユニットの代役)。U4のアドバイスが、これに影響しないことの確認に用いる。 */
  @RestController
  static class OtherUnitController {
    @GetMapping("/other-unit/fail")
    String fail() {
      throw new UserNotFoundException();
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserApplicationService userService;
  @MockitoBean private InvitationFacade invitationFacade;

  static Stream<Arguments> mappings() {
    return Stream.of(
        Arguments.of(new OperatorUnresolvedException(), HttpStatus.UNAUTHORIZED, "Unauthorized"),
        Arguments.of(new UserAccessDeniedException(), HttpStatus.FORBIDDEN, "Forbidden"),
        Arguments.of(new UserNotFoundException(), HttpStatus.NOT_FOUND, "User not found"),
        Arguments.of(
            new InvitationTokenNotFoundException(), HttpStatus.NOT_FOUND, "Invitation not found"),
        Arguments.of(
            new UserValidationException(
                UserFieldError.of("email", "user.validation.email.invalid")),
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Validation failed"),
        Arguments.of(
            new HashCapacityExceededException("secret-detail"),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"),
        Arguments.of(
            new InvitationCapacityExceededException(),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"),
        Arguments.of(
            new EmailLockTimeoutException(), HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"),
        Arguments.of(
            new InvitationMailException(Failure.TIMEOUT, "secret-detail"),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"),
        Arguments.of(
            new SubjectRejectedException(Rejection.CONTROL_CHARACTER),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"),
        Arguments.of(
            new PessimisticLockingFailureException("secret-detail"),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"),
        Arguments.of(
            new CannotAcquireLockException("secret-detail"),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"),
        Arguments.of(
            new CannotCreateTransactionException("secret-detail"),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("mappings")
  void mapsEachExceptionToItsProblemDetailsWithoutLeakingInternals(
      RuntimeException exception, HttpStatus expectedStatus, String expectedTitle)
      throws Exception {
    when(userService.list(any())).thenThrow(exception);

    mockMvc
        .perform(get("/api/users"))
        .andExpect(status().is(expectedStatus.value()))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(expectedStatus.value()))
        .andExpect(jsonPath("$.title").value(expectedTitle))
        .andExpect(jsonPath("$.instance").value("/api/users"))
        .andExpect(content().string(not(containsString("secret-detail"))))
        .andExpect(content().string(not(containsString("Exception"))))
        .andExpect(content().string(not(containsString("stackTrace"))))
        .andExpect(content().string(not(containsString("com.mastersmith"))));
  }

  @Test
  void aChunkedBodyOverTheLimitIsMappedFromTheWrappedReadFailureTo413() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .with(ChunkedRequests.chunked())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.inviteBodyOfExactly(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Payload Too Large"))
        .andExpect(jsonPath("$.instance").value("/api/users"));
  }

  @Test
  void theCauseChainOfANotReadableExceptionIsInspectedForTheTooLargeException() {
    UserApiExceptionAdvice advice = new UserApiExceptionAdvice();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
    HttpMessageNotReadableException deep =
        new HttpMessageNotReadableException(
            "outer",
            new IOException(
                "middle", new IllegalStateException(new RequestBodyTooLargeException(65536))),
            new MockHttpInputMessage(new byte[0]));
    HttpMessageNotReadableException plain =
        new HttpMessageNotReadableException("malformed", new MockHttpInputMessage(new byte[0]));

    ResponseEntity<ProblemDetail> tooLarge = advice.handleNotReadable(deep, request);
    ResponseEntity<ProblemDetail> badRequest = advice.handleNotReadable(plain, request);

    assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badRequest.getBody().getDetail()).doesNotContain("malformed");
  }

  @Test
  void theInstanceFallsBackToTheAcceptRouteTemplateWhenNoHandlerWasMatched() {
    UserApiExceptionAdvice advice = new UserApiExceptionAdvice();
    MockHttpServletRequest accept =
        new MockHttpServletRequest("POST", "/api/users/invitations/SECRET-TOKEN-VALUE/accept");
    MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/users");

    ResponseEntity<ProblemDetail> onAccept = advice.handleTooLarge(accept);
    ResponseEntity<ProblemDetail> onOther = advice.handleTooLarge(other);

    assertThat(onAccept.getBody().getInstance().toString())
        .isEqualTo("/api/users/invitations/%7Btoken%7D/accept")
        .doesNotContain("SECRET-TOKEN-VALUE");
    assertThat(onOther.getBody().getInstance().toString()).isEqualTo("/api/users");
  }

  @Test
  void theInstanceOfAPathVariableRouteIsTheTemplate() throws Exception {
    when(userService.update(any(), any(), any())).thenThrow(new UserNotFoundException());

    mockMvc
        .perform(
            put("/api/users/raw-id-98765")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"roleIds\":[]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.instance").value("/api/users/%7BuserId%7D"));
  }

  @Test
  void theAdviceIsLimitedToTheUserManagementControllersAndDoesNotHandleOtherUnitsExceptions() {
    // U4以外のコントローラの例外は、このアドバイスに変換されない(他ユニットの例外処理に影響しない)。
    assertThatThrownBy(() -> mockMvc.perform(get("/other-unit/fail")))
        .isInstanceOf(ServletException.class)
        .hasRootCauseInstanceOf(UserNotFoundException.class);

    RestControllerAdvice annotation =
        UserApiExceptionAdvice.class.getAnnotation(RestControllerAdvice.class);
    assertThat(annotation.assignableTypes())
        .containsExactlyInAnyOrder(
            UserController.class, InvitationAcceptController.class, MePreferencesController.class);
  }

  @Test
  void theAdviceDeclaresTheHighestPrecedenceSoItWinsOverAGenericHandler() {
    Order order = UserApiExceptionAdvice.class.getAnnotation(Order.class);

    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }

  @Test
  void validationErrorsListEveryFieldInOrder() throws Exception {
    when(userService.list(any()))
        .thenThrow(
            new UserValidationException(
                List.of(
                    UserFieldError.of("a", "user.validation.a.required"),
                    UserFieldError.of("b", "user.validation.b.invalid"),
                    UserFieldError.of(
                        "c", "user.validation.c.tooLong", java.util.Map.of("max", 5)))));

    mockMvc
        .perform(get("/api/users"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0].field").value("a"))
        .andExpect(jsonPath("$.errors[1].message").value("user.validation.b.invalid"))
        .andExpect(jsonPath("$.errors[2].params.max").value(5));
  }
}
