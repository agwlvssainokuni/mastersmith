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

package com.mastersmith.auth.service;

import com.mastersmith.auth.cache.SessionCache;
import com.mastersmith.auth.dto.LoginResponse;
import com.mastersmith.auth.dto.RefreshResponse;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.LoginFailedException;
import com.mastersmith.auth.exception.RefreshRejectedException;
import com.mastersmith.auth.exception.RoleNotHeldException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.observation.AuthObservations;
import com.mastersmith.auth.service.LoginAttemptGate.Reservation;
import com.mastersmith.auth.service.SessionService.IssuedSession;
import com.mastersmith.auth.service.SessionService.RefreshClassification;
import com.mastersmith.auth.token.AccessTokenIssuer;
import com.mastersmith.auth.token.RefreshTokenGenerator;
import com.mastersmith.auth.token.RefreshTokenHasher;
import com.mastersmith.common.security.Operator;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.entity.UserStatus;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ログイン(W1)・リフレッシュ(W2)・ログアウト(W3)・アクティブロールの選択(W4)の流れ。
 *
 * <p><b>トランザクションの境界を、この部品だけが所有する</b>(reliability-design.md NFR4.1): 外側のトランザクションを作らず(クラスにも、メソッドにも
 * {@code @Transactional}を付けない)、C11({@link UserAccountClient})を、トランザクションの外で呼び、内側の短いトランザクションを、{@link
 * TransactionTemplate}で明示的に区切る。ハッシュ計算の許可を待つ間・保持する間は、内部設定DBの接続を保持しない(NFR1.3・BR5.15)。{@link
 * LoginAttemptGate}・{@link SessionService}は、トランザクションに参加するだけ({@code
 * MANDATORY})である。トランザクションのタイムアウトは3秒 (内部設定DBが応答しない場合に、長く保持しない、NFR4.2)。
 *
 * <p>コミットの後に、{@link SessionCache}を無効化する(コミットより前に無効化すると、その間に別のリクエストが古い内容を読み込んで、キャッシュに入れうる。NFR4.3)。
 * 内部設定DBの障害は、{@link
 * AuthStorageUnavailableException}(503)に変換する。パスワード・トークン・ハッシュ・鍵・メールアドレスを、ログ・メトリクス・例外に出さない (BR5.14)。
 */
@Service
public class AuthenticationApplicationService {

  /** 予約の一意制約違反(行の初回作成の同時実行)を、新しいトランザクションでやり直す上限(NFR4.1)。 */
  private static final int MAX_RESERVE_ATTEMPTS = 3;

  /** 認証の各トランザクションのタイムアウト(秒)。 */
  private static final int TRANSACTION_TIMEOUT_SECONDS = 3;

  private final UserAccountClient users;
  private final LoginAttemptGate gate;
  private final SessionService sessions;
  private final SessionCache cache;
  private final AccessTokenIssuer accessTokenIssuer;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final RefreshTokenHasher refreshTokenHasher;
  private final AuthExceptionTranslator translator;
  private final AuthMetrics metrics;
  private final AuthEventLogger logger;
  private final AuthObservations observations;
  private final TransactionTemplate writeTx;
  private final TransactionTemplate readTx;

  public AuthenticationApplicationService(
      UserAccountClient users,
      LoginAttemptGate gate,
      SessionService sessions,
      SessionCache cache,
      AccessTokenIssuer accessTokenIssuer,
      RefreshTokenGenerator refreshTokenGenerator,
      RefreshTokenHasher refreshTokenHasher,
      AuthExceptionTranslator translator,
      AuthMetrics metrics,
      AuthEventLogger logger,
      AuthObservations observations,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
    this.users = users;
    this.gate = gate;
    this.sessions = sessions;
    this.cache = cache;
    this.accessTokenIssuer = accessTokenIssuer;
    this.refreshTokenGenerator = refreshTokenGenerator;
    this.refreshTokenHasher = refreshTokenHasher;
    this.translator = translator;
    this.metrics = metrics;
    this.logger = logger;
    this.observations = observations;
    this.writeTx = new TransactionTemplate(transactionManager);
    this.writeTx.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
    this.readTx.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
  }

  // ---- W1: ログイン ----

  /**
   * ログイン(W1、BR5.1〜BR5.3・BR5.5・BR5.8・BR5.9・BR5.15)。
   *
   * @throws LoginFailedException 認証に失敗した場合(原因を区別しない、401)
   * @throws HashCapacityExceededException ハッシュ計算の待機超過(実際・ダミーの検証のどちらでも、503)
   * @throws AuthStorageUnavailableException 内部設定DBの障害(503)
   */
  public LoginResponse login(String email, String password) {
    Timer.Sample sample = Timer.start();
    try {
      return observations.observe("auth.login", () -> doLogin(email, password));
    } finally {
      sample.stop(metrics.loginDuration());
    }
  }

  private LoginResponse doLogin(String email, String password) {
    if (isBlank(email) || isBlank(password)) {
      throw loginFailed(); // C11を呼ばない
    }
    String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
    Optional<UserAccount> found = users.findByEmail(normalizedEmail);
    if (found.isEmpty() || found.get().status() != UserStatus.ACTIVE) {
      throw failedWithDummyVerify(password); // 失敗回数を記録しない
    }
    UserAccount user = found.get();
    Reservation reservation = reserve(user.userId());
    if (!reservation.acquired()) {
      throw failedWithDummyVerify(password); // ロック中: 検証せず、数えず、ロック期間も延長しない
    }
    if (!verify(user.userId(), password, reservation)) {
      throw loginFailed(); // 枠は、確保の時点で、失敗として数えられている
    }
    return succeed(user, reservation);
  }

  /** 試行の枠を確保する。行の初回作成の同時実行による一意制約の違反は、新しいトランザクションで、やり直す(最大3回)。 */
  private Reservation reserve(String userId) {
    RuntimeException last = null;
    for (int attempt = 0; attempt < MAX_RESERVE_ATTEMPTS; attempt++) {
      try {
        return translator.translate(() -> writeTx.execute(status -> gate.reserve(userId)));
      } catch (DataIntegrityViolationException e) {
        last = e; // 利用者からは観測されない(500にも401にもならない)
      }
    }
    throw translator.unavailable(last);
  }

  /** パスワードを検証する。上限超過・内部設定DBの障害では、確保した枠を返して(補償)、例外を伝える。想定外の例外では、補償しない(安全側)。 */
  private boolean verify(String userId, String password, Reservation reservation) {
    try {
      return users.verifyPasswordHash(userId, password);
    } catch (HashCapacityExceededException e) {
      compensateQuietly(userId, reservation);
      hashCapacityExceeded();
      throw e;
    } catch (AuthStorageUnavailableException e) {
      compensateQuietly(userId, reservation);
      throw e;
    }
  }

  private LoginResponse succeed(UserAccount user, Reservation reservation) {
    String userId = user.userId();
    String activeRoleId = SessionService.initialActiveRole(user.roleIds());
    IssuedSession issued;
    try {
      // 失敗回数のリセットとSessionの作成は、同一のトランザクション(どちらかが失敗すれば、両方をロールバックする)。
      issued =
          translator.translate(
              () ->
                  writeTx.execute(
                      status -> {
                        gate.succeed(userId);
                        return sessions.create(userId, activeRoleId);
                      }));
    } catch (AuthStorageUnavailableException e) {
      compensateQuietly(userId, reservation);
      throw e;
    }
    Session session = issued.session();
    String accessToken = accessTokenIssuer.issue(userId, session.getSessionId());
    logger.loginSucceeded(userId, session.getSessionId());
    return new LoginResponse(accessToken, issued.refreshToken(), user.roleIds(), activeRoleId);
  }

  /** 実際の検証を行わない場合のダミーの検証のあと、原因を区別しない失敗を返す(BR5.2)。 */
  private LoginFailedException failedWithDummyVerify(String password) {
    try {
      users.dummyVerify(password);
    } catch (HashCapacityExceededException e) {
      hashCapacityExceeded();
      throw e;
    }
    return loginFailed();
  }

  private LoginFailedException loginFailed() {
    metrics.loginFailed();
    logger.loginFailed();
    return new LoginFailedException();
  }

  private void hashCapacityExceeded() {
    metrics.hashCapacityExceeded();
    logger.hashCapacityExceeded();
  }

  /** 補償の更新を、1回だけ試みる。失敗した場合(内部設定DBの障害を含む)は、枠を失敗として数えたままにする(安全側)。 */
  private void compensateQuietly(String userId, Reservation reservation) {
    try {
      writeTx.executeWithoutResult(status -> gate.compensate(userId, reservation));
    } catch (RuntimeException e) {
      logger.compensationFailed(e);
    }
  }

  // ---- W2: リフレッシュ ----

  /**
   * リフレッシュ(W2、BR5.6・BR5.7・BR5.10)。更新のたびに、新しいリフレッシュトークンを発行して、有効期限を延長する(ローテーション)。
   *
   * @throws RefreshRejectedException このリフレッシュトークンでは更新できない場合(全原因を区別しない、401)
   * @throws AuthStorageUnavailableException 内部設定DBの障害(503。トークンは更新されない)
   */
  public RefreshResponse refresh(String refreshToken) {
    return observations.observe("auth.refresh", () -> doRefresh(refreshToken));
  }

  private RefreshResponse doRefresh(String refreshToken) {
    if (isBlank(refreshToken)) {
      throw new RefreshRejectedException();
    }
    String oldHash = refreshTokenHasher.hash(refreshToken);
    Session current = requireCurrentSession(oldHash);
    String sessionId = current.getSessionId();
    String userId = current.getUserId();

    // 無効化の確認(引き込み型、BR5.7)。無効化されている(disabledまたは不存在)なら、Sessionを失効させて拒否する。
    Optional<UserAccount> account =
        users.isDisabled(userId) ? Optional.empty() : users.findByUserId(userId);
    if (account.isEmpty()) {
      revokeAndInvalidate(sessionId);
      logger.sessionRevokedForDisabledUser(userId, sessionId);
      throw new RefreshRejectedException();
    }
    List<String> roleIds = account.get().roleIds();

    String readRoleId = current.getActiveRoleId();
    String newRoleId = SessionService.reconfirmActiveRole(readRoleId, roleIds);
    String newRefreshToken = refreshTokenGenerator.generate();
    String newHash = refreshTokenHasher.hash(newRefreshToken);
    int updated = rotate(sessionId, oldHash, newHash, readRoleId, newRoleId);
    if (updated == 0) {
      // 条件付きの更新に負けた。Sessionを読み直して、原因を切り分ける(security-design.md NFR2.3の判定表)。
      Optional<Session> reread =
          translator
              .translate(() -> readTx.execute(status -> sessions.find(sessionId)))
              .filter(session -> session.getRefreshTokenHash().equals(oldHash));
      if (reread.isEmpty()) {
        throw new RefreshRejectedException(); // 同時のリフレッシュに負けた(Sessionは失効させない)
      }
      // ハッシュは同じで、アクティブロールだけが変わった(並行するロール選択に負けた): 1回だけ、やり直す。
      readRoleId = reread.get().getActiveRoleId();
      newRoleId = SessionService.reconfirmActiveRole(readRoleId, roleIds);
      updated = rotate(sessionId, oldHash, newHash, readRoleId, newRoleId);
      if (updated == 0) {
        throw new RefreshRejectedException();
      }
    }
    cache.invalidate(sessionId);
    if (!Objects.equals(current.getActiveRoleId(), newRoleId)) {
      logger.activeRoleChanged(userId, sessionId, newRoleId);
    }
    String accessToken = accessTokenIssuer.issue(userId, sessionId);
    return new RefreshResponse(accessToken, newRefreshToken, roleIds, newRoleId);
  }

  /** ハッシュに一致する有効なSessionを返す。それ以外は、判定表に従って処理し、拒否する。 */
  private Session requireCurrentSession(String refreshTokenHash) {
    RefreshClassification classification =
        translator.translate(() -> readTx.execute(status -> sessions.classify(refreshTokenHash)));
    switch (classification.kind()) {
      case CURRENT_VALID:
        return classification.session();
      case PREVIOUS_WITHIN_GRACE:
        // 正当な再送・複数タブ・同時実行による競合とみなし、401だけを返す(Sessionは失効させない)。
        metrics.refreshReuseWithinGrace();
        logger.refreshReuseWithinGrace(classification.session().getSessionId());
        throw new RefreshRejectedException();
      case PREVIOUS_REUSED:
        // 猶予を超えた、無効なトークンの再使用(盗用の疑い)。Sessionを失効させる。
        revokeAndInvalidate(classification.session().getSessionId());
        metrics.refreshReuseDetected();
        logger.refreshTokenReuseDetected(
            classification.session().getUserId(), classification.session().getSessionId());
        throw new RefreshRejectedException();
      default:
        throw new RefreshRejectedException();
    }
  }

  private int rotate(
      String sessionId, String oldHash, String newHash, String readRoleId, String newRoleId) {
    return translator.translate(
        () ->
            writeTx.execute(
                status -> sessions.rotate(sessionId, oldHash, newHash, readRoleId, newRoleId)));
  }

  // ---- W3: ログアウト ----

  /** ログアウト(W3、BR5.8)。認証フィルタを通ったSessionだけを失効させる。同一ユーザーの他のSession(他の端末)には影響しない。 */
  public void logout(Operator operator) {
    observations.run(
        "auth.logout",
        () -> {
          revokeAndInvalidate(operator.sessionId());
          logger.loggedOut(operator.userId(), operator.sessionId());
        });
  }

  // ---- W4: アクティブロールの選択 ----

  /**
   * アクティブロールの選択(W4、BR5.9)。指定された{@code roleId}が、そのユーザーのその時点の選択可能なロール(直接付与分とGroup経由分の和集合)に含まれない場合は、
   * Sessionを変更せず、{@link RoleNotHeldException}(403)。
   *
   * @return 選択したロール
   */
  public String selectActiveRole(Operator operator, String roleId) {
    return observations.observe("auth.active-role", () -> doSelectActiveRole(operator, roleId));
  }

  private String doSelectActiveRole(Operator operator, String roleId) {
    if (isBlank(roleId)) {
      throw new RoleNotHeldException();
    }
    UserAccount account =
        users.findByUserId(operator.userId()).orElseThrow(RoleNotHeldException::new);
    if (!account.roleIds().contains(roleId)) {
      throw new RoleNotHeldException();
    }
    boolean updated =
        translator.translate(
            () ->
                Boolean.TRUE.equals(
                    writeTx.execute(
                        status -> sessions.selectActiveRole(operator.sessionId(), roleId))));
    cache.invalidate(operator.sessionId());
    if (!updated) {
      throw new SessionExpiredException(); // 認証の後、選択の前に、Sessionが有効でなくなった
    }
    logger.activeRoleChanged(operator.userId(), operator.sessionId(), roleId);
    return roleId;
  }

  // ---- 共通 ----

  /** Sessionを失効させ(冪等)、コミットの後に、キャッシュを無効化する(NFR4.3)。 */
  private void revokeAndInvalidate(String sessionId) {
    translator.translate(() -> writeTx.executeWithoutResult(status -> sessions.revoke(sessionId)));
    cache.invalidate(sessionId);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
