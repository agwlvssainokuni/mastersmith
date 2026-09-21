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

package com.mastersmith.auth.observation;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 認証の出来事のログの窓口(nfr-design/observability-design.md NFR5.2、security-design.md
 * NFR2.7)。認証の出来事のログは、この部品だけが出力する。
 *
 * <p>引数は、userId・sessionId・roleId・日時・分類(列挙型)・件数・所要時間・例外の型名に限る。任意の文字列を渡せないため、パスワード・トークン(平文・ハッシュ)・鍵・
 * メールアドレスを、誤って出力する経路を作らない(BR5.14)。ログイン失敗は、原因の区別も、試行されたメールアドレスも記録しない。例外は、メッセージ全文・SQLを出さず、型名だけを記録する。
 */
@Component
public class AuthEventLogger {

  private static final Logger LOG = LoggerFactory.getLogger(AuthEventLogger.class);

  public void loginSucceeded(String userId, String sessionId) {
    LOG.info("Login succeeded: userId={}, sessionId={}", userId, sessionId);
  }

  public void loginFailed() {
    LOG.info("Login failed");
  }

  public void accountLocked(String userId, Instant lockedUntil) {
    LOG.warn("Account locked: userId={}, lockedUntil={}", userId, lockedUntil);
  }

  public void refreshTokenReuseDetected(String userId, String sessionId) {
    LOG.warn(
        "Refresh token reuse detected, session revoked: userId={}, sessionId={}",
        userId,
        sessionId);
  }

  public void refreshReuseWithinGrace(String sessionId) {
    LOG.info("Refresh token reuse within grace: sessionId={}", sessionId);
  }

  public void sessionRevokedForDisabledUser(String userId, String sessionId) {
    LOG.info(
        "Session revoked on refresh, user is disabled: userId={}, sessionId={}", userId, sessionId);
  }

  public void loggedOut(String userId, String sessionId) {
    LOG.info("Logged out: userId={}, sessionId={}", userId, sessionId);
  }

  public void activeRoleChanged(String userId, String sessionId, String roleId) {
    LOG.info("Active role changed: userId={}, sessionId={}, roleId={}", userId, sessionId, roleId);
  }

  public void hashCapacityExceeded() {
    LOG.warn("Login rejected: password hash capacity exceeded");
  }

  public void dbUnavailable(Throwable cause) {
    LOG.error("Authentication storage unavailable: cause={}", cause.getClass().getName());
  }

  /** 補償の更新の失敗(枠を、失敗として数えたままにする。安全側)。 */
  public void compensationFailed(Throwable cause) {
    LOG.warn("Login attempt compensation failed: cause={}", cause.getClass().getName());
  }

  public void unauthorized(UnauthorizedReason reason) {
    LOG.debug("Authentication filter rejected the request: reason={}", reason.tag());
  }

  public void cleanupCompleted(long deletedRows, long elapsedMillis) {
    LOG.info(
        "Session cleanup completed: deletedRows={}, elapsedMillis={}", deletedRows, elapsedMillis);
  }

  public void cleanupFailed(long deletedRows, Throwable cause) {
    LOG.error(
        "Session cleanup failed: deletedRows={}, cause={}",
        deletedRows,
        cause.getClass().getName());
  }

  public void allSessionsRevokedOnStartup(long deletedRows) {
    LOG.warn("All sessions were deleted on startup: deletedRows={}", deletedRows);
  }
}
