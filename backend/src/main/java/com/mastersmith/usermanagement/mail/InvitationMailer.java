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

import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.exception.InvitationMailException;
import com.mastersmith.usermanagement.exception.InvitationMailException.Failure;
import com.mastersmith.usermanagement.exception.SubjectRejectedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 招待メールの組み立てと送信(rules.md BR4.16、performance-design.md NFR1.4、security-design.md NFR2.7)。
 *
 * <p>HTMLメールのみを送る(テキスト版は送らない)。件名は、レンダリング後のHTMLの{@code <title>}から{@link
 * SubjectExtractor}で取り出す。宛先には、検証・正規化済みのemailだけを用い、件名以外のヘッダーに利用者の入力値を差し込まない(氏名は本文にのみ、
 * エンジンがHTMLエスケープして差し込む)。送信は{@link InvitationMailExecutor}の専用スレッドで行い、呼び出し側は結果を、設定の上限(既定10秒)で待つ。
 * 超過した場合は、送信の完了を待たずに失敗として扱う。自動リトライはしない(管理者が再招待する)。
 *
 * <p>失敗は{@link InvitationMailException}で表し、SMTPの不調({@code
 * user.invitation.mail.failed})と、テンプレート起因の件名の拒否 ({@code
 * user.invitation.subject.rejected})を別のカウンタで数える。ログには、失敗の分類と例外の型名だけを出し、メールアドレス・氏名・件名・
 * トークンは出さない(NFR2.6)。
 */
@Component
public class InvitationMailer {

  private static final Logger LOG = LoggerFactory.getLogger(InvitationMailer.class);

  static final String ACCEPT_PATH = "/invitations/accept#token=";

  private final JavaMailSender mailSender;
  private final MailTemplateRenderer renderer;
  private final SubjectExtractor subjectExtractor;
  private final InvitationMailExecutor executor;
  private final InvitationMailProperties mailProperties;
  private final Duration sendTimeout;
  private final Counter mailFailedCounter;
  private final Counter subjectRejectedCounter;

  public InvitationMailer(
      JavaMailSender mailSender,
      MailTemplateRenderer renderer,
      SubjectExtractor subjectExtractor,
      InvitationMailExecutor executor,
      InvitationMailProperties mailProperties,
      UserManagementProperties properties,
      MeterRegistry meterRegistry) {
    this.mailSender = mailSender;
    this.renderer = renderer;
    this.subjectExtractor = subjectExtractor;
    this.executor = executor;
    this.mailProperties = mailProperties;
    this.sendTimeout = properties.invitation().mailTimeout();
    this.mailFailedCounter =
        Counter.builder("user.invitation.mail.failed")
            .description("招待メール送信の失敗(タイムアウト・接続エラー・プール満杯、BR4.16)")
            .register(meterRegistry);
    this.subjectRejectedCounter =
        Counter.builder("user.invitation.subject.rejected")
            .description("テンプレート起因で、件名を拒否した回数(SMTPの不調のカウンタとは別)")
            .register(meterRegistry);
  }

  /** 招待リンク({@code <ベースURL>/invitations/accept#token=<トークン>}、NFR2.10)。 */
  public String inviteLink(String invitationToken) {
    return mailProperties.baseUrl() + ACCEPT_PATH + invitationToken;
  }

  /**
   * 招待メールを送信する。
   *
   * @param recipientEmail 宛先(BR4.15で検証・正規化済みのemail)
   * @param recipientName 招待されたユーザーの氏名(本文にのみ差し込む)
   * @param locale 招待メールの言語
   * @param invitationToken 招待トークン(リンクにのみ用いる)
   * @throws InvitationMailException 生成・送信に失敗した場合(件名の拒否は{@link SubjectRejectedException})
   */
  public void send(
      String recipientEmail, String recipientName, UiLocale locale, String invitationToken) {
    String subject;
    String html;
    try {
      html = renderer.render(locale, recipientName, inviteLink(invitationToken));
      subject = subjectExtractor.extract(html);
    } catch (SubjectRejectedException e) {
      subjectRejectedCounter.increment();
      LOG.warn("Invitation mail subject rejected: rejection={}", e.getRejection());
      throw e;
    } catch (RuntimeException e) {
      subjectRejectedCounter.increment();
      LOG.warn("Invitation mail template rendering failed: {}", e.getClass().getName());
      throw new InvitationMailException(
          Failure.TEMPLATE_INVALID, "Invitation mail template rendering failed");
    }

    Map<String, String> mdc = MDC.getCopyOfContextMap();
    Future<?> future;
    try {
      future = executor.submit(() -> sendWithMdc(mdc, recipientEmail, subject, html));
    } catch (RejectedExecutionException e) {
      throw fail(Failure.POOL_EXHAUSTED, "Invitation mail pool exhausted", null);
    }
    awaitResult(future);
  }

  private void awaitResult(Future<?> future) {
    try {
      future.get(sendTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw fail(Failure.TIMEOUT, "Invitation mail send timed out", null);
    } catch (ExecutionException e) {
      throw fail(Failure.SEND_FAILED, "Invitation mail send failed", e.getCause());
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw fail(Failure.INTERRUPTED, "Invitation mail send interrupted", null);
    }
  }

  private InvitationMailException fail(Failure failure, String message, Throwable cause) {
    mailFailedCounter.increment();
    // SMTPライブラリの例外のメッセージは宛先を含みうるため、型名だけを記録する。
    LOG.warn(
        "Invitation mail failed: failure={}, cause={}",
        failure,
        cause == null ? "-" : cause.getClass().getName());
    return new InvitationMailException(failure, message);
  }

  private void sendWithMdc(
      Map<String, String> mdc, String recipientEmail, String subject, String html) {
    if (mdc != null) {
      MDC.setContextMap(mdc);
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(mailProperties.from());
      helper.setTo(recipientEmail);
      helper.setSubject(subject);
      helper.setText(html, true);
      mailSender.send(message);
    } catch (MessagingException e) {
      throw new IllegalStateException("Invitation mail could not be composed", e);
    } finally {
      MDC.clear();
    }
  }
}
