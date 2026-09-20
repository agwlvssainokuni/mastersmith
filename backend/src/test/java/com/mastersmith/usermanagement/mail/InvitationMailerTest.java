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

package com.mastersmith.usermanagement.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.exception.InvitationMailException;
import com.mastersmith.usermanagement.exception.InvitationMailException.Failure;
import com.mastersmith.usermanagement.exception.SubjectRejectedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Address;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * {@link InvitationMailer}:
 * 組み立て・送信・失敗時の例外とカウンタ・打ち切り・プール満杯・MDCの引き継ぎ(NFR1.4・NFR2.7、BR4.16)。JavaMailSenderはモックする。
 */
class InvitationMailerTest {

  private static final String TOKEN = "11111111-2222-3333-4444-555555555555";
  private static final String RECIPIENT = "recipient@example.test";

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final InvitationMailProperties mailProperties =
      new InvitationMailProperties("https://app.example.test/", "no-reply@example.test", false);
  private InvitationMailExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new InvitationMailExecutor(2);
    when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
    MDC.clear();
  }

  private InvitationMailer mailer(Duration timeout) {
    return mailer(timeout, new MailTemplateRenderer(), executor);
  }

  private InvitationMailer mailer(
      Duration timeout, MailTemplateRenderer renderer, InvitationMailExecutor mailExecutor) {
    UserManagementProperties properties =
        new UserManagementProperties(
            new UserManagementProperties.Hash(19456, 2, 1, 16, 32, 0, Duration.ofSeconds(2)),
            new UserManagementProperties.Invitation(5, Duration.ofSeconds(12), 2, timeout),
            Duration.ofSeconds(15));
    return new InvitationMailer(
        mailSender,
        renderer,
        new SubjectExtractor(),
        mailExecutor,
        mailProperties,
        properties,
        meterRegistry);
  }

  private double count(String name) {
    return meterRegistry.get(name).counter().count();
  }

  private MimeMessage sentMessage() {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    return captor.getValue();
  }

  @Test
  void sendsAnHtmlMailWithTheSubjectFromTheTitleAndTheInvitationLink() throws Exception {
    mailer(Duration.ofSeconds(10)).send(RECIPIENT, "太郎", UiLocale.JA, TOKEN);

    MimeMessage message = sentMessage();
    message.saveChanges(); // JavaMailSenderが送信時に行う処理(Content-Typeなどのヘッダーを確定する)。
    assertThat(message.getSubject()).isEqualTo("【MasterSmith】ユーザー招待のご案内");
    assertThat(message.getRecipients(Message.RecipientType.TO))
        .extracting(Address::toString)
        .containsExactly(RECIPIENT);
    assertThat(message.getFrom())
        .extracting(Address::toString)
        .containsExactly("no-reply@example.test");
    assertThat(message.getContentType()).startsWith("text/html");
    String body = (String) message.getContent();
    assertThat(body)
        .contains("太郎 様")
        .contains("https://app.example.test/invitations/accept#token=" + TOKEN);
    assertThat(count("user.invitation.mail.failed")).isZero();
    assertThat(count("user.invitation.subject.rejected")).isZero();
  }

  @Test
  void usesTheEnglishTemplateForTheEnglishLocale() throws Exception {
    mailer(Duration.ofSeconds(10)).send(RECIPIENT, "Taro", UiLocale.EN, TOKEN);

    assertThat(sentMessage().getSubject()).isEqualTo("[MasterSmith] You have been invited");
  }

  @Test
  void encodesANonAsciiSubjectAsAMailHeader() throws Exception {
    mailer(Duration.ofSeconds(10)).send(RECIPIENT, "太郎", UiLocale.JA, TOKEN);

    assertThat(sentMessage().getHeader("Subject", null)).startsWith("=?UTF-8?");
  }

  @Test
  void neverPutsTheRecipientNameIntoAnyHeader() throws Exception {
    String hostileName = "Mallory\r\nBcc: evil@example.test";

    mailer(Duration.ofSeconds(10)).send(RECIPIENT, hostileName, UiLocale.EN, TOKEN);

    MimeMessage message = sentMessage();
    assertThat(message.getHeader("Bcc")).isNull();
    for (Header header : Collections.list(message.getAllHeaders())) {
      assertThat(header.getValue())
          .as(header.getName())
          .doesNotContain("Mallory")
          .doesNotContain("evil");
    }
    assertThat(message.getAllRecipients()).hasSize(1);
  }

  @Test
  void buildsTheInvitationLinkFromTheConfiguredBaseUrlWithTheTokenInTheFragment() {
    assertThat(mailer(Duration.ofSeconds(10)).inviteLink(TOKEN))
        .isEqualTo("https://app.example.test/invitations/accept#token=" + TOKEN);
  }

  @Test
  void aSendFailureBecomesAnInvitationMailExceptionWithoutLeakingTheAddress() {
    doThrow(new MailSendException("Invalid Addresses: " + RECIPIENT))
        .when(mailSender)
        .send(any(MimeMessage.class));

    assertThatThrownBy(
            () -> mailer(Duration.ofSeconds(10)).send(RECIPIENT, "太郎", UiLocale.JA, TOKEN))
        .isInstanceOfSatisfying(
            InvitationMailException.class,
            e -> {
              assertThat(e.getFailure()).isEqualTo(Failure.SEND_FAILED);
              assertThat(e.getMessage()).doesNotContain(RECIPIENT);
              assertThat(e.getCause()).isNull();
            });

    assertThat(count("user.invitation.mail.failed")).isEqualTo(1.0);
    assertThat(count("user.invitation.subject.rejected")).isZero();
  }

  @Test
  void aSendThatExceedsTheTimeoutIsAbandonedAndCounted() throws Exception {
    CountDownLatch neverReleased = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              started.countDown();
              neverReleased.await(30, TimeUnit.SECONDS);
              return null;
            })
        .when(mailSender)
        .send(any(MimeMessage.class));

    assertThatThrownBy(
            () -> mailer(Duration.ofMillis(200)).send(RECIPIENT, "太郎", UiLocale.JA, TOKEN))
        .isInstanceOfSatisfying(
            InvitationMailException.class,
            e -> assertThat(e.getFailure()).isEqualTo(Failure.TIMEOUT));

    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(count("user.invitation.mail.failed")).isEqualTo(1.0);
    neverReleased.countDown();
  }

  @Test
  void aFullPoolIsRejectedAtSubmissionAndCounted() throws Exception {
    InvitationMailExecutor single = new InvitationMailExecutor(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch busy = new CountDownLatch(1);
    single.submit(
        () -> {
          busy.countDown();
          try {
            release.await(30, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    assertThat(busy.await(10, TimeUnit.SECONDS)).isTrue();

    try {
      assertThatThrownBy(
              () ->
                  mailer(Duration.ofSeconds(10), new MailTemplateRenderer(), single)
                      .send(RECIPIENT, "太郎", UiLocale.JA, TOKEN))
          .isInstanceOfSatisfying(
              InvitationMailException.class,
              e -> assertThat(e.getFailure()).isEqualTo(Failure.POOL_EXHAUSTED));
      verify(mailSender, never()).send(any(MimeMessage.class));
      assertThat(count("user.invitation.mail.failed")).isEqualTo(1.0);
    } finally {
      release.countDown();
      single.shutdown();
    }
  }

  @Test
  void aRejectedSubjectSendsNothingAndIsCountedSeparately() {
    MailTemplateRenderer renderer = mock(MailTemplateRenderer.class);
    when(renderer.render(any(), any(), any()))
        .thenReturn("<html><head><title>Hi&#10;Bcc: evil@example.test</title></head></html>");

    assertThatThrownBy(
            () ->
                mailer(Duration.ofSeconds(10), renderer, executor)
                    .send(RECIPIENT, "太郎", UiLocale.JA, TOKEN))
        .isInstanceOf(SubjectRejectedException.class);

    verify(mailSender, never()).send(any(MimeMessage.class));
    assertThat(count("user.invitation.subject.rejected")).isEqualTo(1.0);
    assertThat(count("user.invitation.mail.failed")).isZero();
  }

  @Test
  void aTemplateThatCannotBeRenderedIsATemplateFailure() {
    MailTemplateRenderer renderer = mock(MailTemplateRenderer.class);
    when(renderer.render(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(
            () ->
                mailer(Duration.ofSeconds(10), renderer, executor)
                    .send(RECIPIENT, "太郎", UiLocale.JA, TOKEN))
        .isInstanceOfSatisfying(
            InvitationMailException.class,
            e -> assertThat(e.getFailure()).isEqualTo(Failure.TEMPLATE_INVALID));

    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void carriesTheMdcIntoTheSendingThreadAndLeavesTheCallersMdcUntouched() {
    AtomicReference<String> seenInSender = new AtomicReference<>();
    doAnswer(
            invocation -> {
              seenInSender.set(MDC.get("requestId"));
              return null;
            })
        .when(mailSender)
        .send(any(MimeMessage.class));
    MDC.put("requestId", "req-42");

    mailer(Duration.ofSeconds(10)).send(RECIPIENT, "太郎", UiLocale.JA, TOKEN);

    assertThat(seenInSender.get()).isEqualTo("req-42");
    assertThat(MDC.get("requestId")).isEqualTo("req-42");
  }
}
