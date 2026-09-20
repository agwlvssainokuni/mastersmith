package com.mastersmith.auth.service;

import com.mastersmith.auth.config.AuthProperties;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.token.RefreshTokenGenerator;
import com.mastersmith.auth.token.RefreshTokenHasher;
import com.mastersmith.auth.token.SessionIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessionの作成・ローテーション・失効・ロール選択の更新と、リフレッシュ時の判定(BR5.6・BR5.9・BR5.10、security-design.md NFR2.3・NFR2.6)。
 *
 * <p>トランザクションには、<b>参加するだけ</b>である({@code MANDATORY}。トランザクションの境界は{@link
 * AuthenticationApplicationService}が所有する)。キャッシュの無効化は、コミットの後に、呼び出し側が行う(NFR4.3)。時刻は、注入された{@link
 * Clock}から得る(NFR4.6)。
 */
@Component
public class SessionService {

  /**
   * リフレッシュトークンの提出に対する、Sessionの検索結果の分類(security-design.md NFR2.3の判定表)。
   *
   * <ul>
   *   <li>{@link Kind#CURRENT_VALID}: 現在のハッシュに一致し、Sessionが有効。
   *   <li>{@link Kind#REJECTED}: 未知、または、一致したSessionが有効でない(revoked・期限切れ)。Sessionは変更しない。
   *   <li>{@link Kind#PREVIOUS_WITHIN_GRACE}: 1つ前のハッシュに一致し、猶予内。401だけで、Sessionは失効させない。
   *   <li>{@link Kind#PREVIOUS_REUSED}: 1つ前のハッシュに一致し、猶予を超えている。無効なトークンの再使用(盗用の疑い)として、Sessionを失効させる。
   * </ul>
   */
  public enum Kind {
    CURRENT_VALID,
    REJECTED,
    PREVIOUS_WITHIN_GRACE,
    PREVIOUS_REUSED
  }

  /** 検索結果の分類と、一致したSession(未知の場合はnull)。 */
  public record RefreshClassification(Kind kind, Session session) {}

  /**
   * 作成したSessionと、リフレッシュトークンの平文(応答でだけ返し、保存・ログには出さない)。
   *
   * @param session 作成したSession
   * @param refreshToken リフレッシュトークンの平文
   */
  public record IssuedSession(Session session, String refreshToken) {

    /** リフレッシュトークンを出力しない。 */
    @Override
    public String toString() {
      return "IssuedSession[sessionId=" + session.getSessionId() + "]";
    }
  }

  private final SessionRepository repository;
  private final AuthProperties properties;
  private final Clock clock;
  private final SessionIdGenerator sessionIdGenerator;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final RefreshTokenHasher refreshTokenHasher;

  public SessionService(
      SessionRepository repository,
      AuthProperties properties,
      Clock clock,
      SessionIdGenerator sessionIdGenerator,
      RefreshTokenGenerator refreshTokenGenerator,
      RefreshTokenHasher refreshTokenHasher) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
    this.sessionIdGenerator = sessionIdGenerator;
    this.refreshTokenGenerator = refreshTokenGenerator;
    this.refreshTokenHasher = refreshTokenHasher;
  }

  /** ログイン成功(W1)で、有効な新しいSessionと、新しいリフレッシュトークンを作る(BR5.8: ログインのたびに、独立したSession)。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public IssuedSession create(String userId, String activeRoleId) {
    Instant now = clock.instant();
    String refreshToken = refreshTokenGenerator.generate();
    Session session =
        Session.issue(
            sessionIdGenerator.generate(),
            userId,
            activeRoleId,
            refreshTokenHasher.hash(refreshToken),
            now,
            now.plus(properties.refreshTokenTtl()));
    repository.saveAndFlush(session);
    return new IssuedSession(session, refreshToken);
  }

  /** ハッシュ済みのリフレッシュトークンで、Sessionを検索し、判定表に従って分類する(読み取りだけ)。 */
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public RefreshClassification classify(String refreshTokenHash) {
    Instant now = clock.instant();
    Optional<Session> current = repository.findByRefreshTokenHash(refreshTokenHash);
    if (current.isPresent()) {
      Session session = current.get();
      return session.isValidAt(now)
          ? new RefreshClassification(Kind.CURRENT_VALID, session)
          : new RefreshClassification(Kind.REJECTED, session);
    }
    Optional<Session> previous = repository.findByPreviousRefreshTokenHash(refreshTokenHash);
    if (previous.isEmpty()) {
      return new RefreshClassification(Kind.REJECTED, null);
    }
    Session session = previous.get();
    if (!session.isValidAt(now)) {
      return new RefreshClassification(Kind.REJECTED, session);
    }
    boolean withinGrace =
        !now.isAfter(session.getLastRefreshedAt().plus(properties.refreshReuseGrace()));
    return new RefreshClassification(
        withinGrace ? Kind.PREVIOUS_WITHIN_GRACE : Kind.PREVIOUS_REUSED, session);
  }

  /** sessionIdでSessionを読む(読み取りだけ)。 */
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Optional<Session> find(String sessionId) {
    return repository.findById(sessionId);
  }

  /**
   * リフレッシュのローテーション(条件付きの更新)。現在のハッシュが同一で、有効で、アクティブロールが読み取り時点({@code readRoleId})と等しい場合に限り、
   * 新しいハッシュへ更新し、有効期限を延長する。同一のリフレッシュトークンの同時の更新では、1件だけが成功する。
   *
   * @return 更新の件数(1: 成功、0: 条件を満たさない)
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public int rotate(
      String sessionId, String oldHash, String newHash, String readRoleId, String newRoleId) {
    Instant now = clock.instant();
    Instant newRefreshExpiresAt = now.plus(properties.refreshTokenTtl());
    return readRoleId == null
        ? repository.rotateWithoutRole(sessionId, oldHash, newHash, now, newRefreshExpiresAt, newRoleId)
        : repository.rotateWithRole(
            sessionId, oldHash, newHash, now, newRefreshExpiresAt, newRoleId, readRoleId);
  }

  /** Sessionを失効させる(冪等)。すでにrevokedでも、エラーにしない。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean revoke(String sessionId) {
    return repository.revoke(sessionId) > 0;
  }

  /** アクティブロールを更新する(ロール選択)。有効でない・存在しないSessionは、更新しない。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean selectActiveRole(String sessionId, String roleId) {
    return repository.updateActiveRole(sessionId, roleId) > 0;
  }

  /**
   * ログイン時のアクティブロール(BR5.9): ロールがちょうど1つなら、そのロール。それ以外(0個・2個以上)は、未選択(null)。
   */
  public static String initialActiveRole(Collection<String> roleIds) {
    return roleIds.size() == 1 ? roleIds.iterator().next() : null;
  }

  /**
   * リフレッシュ時のアクティブロールの再確認(BR5.10、security-design.md NFR2.6の判定表)。現在のロール{@code current}が選択済みで、最新のロール集合に
   * 含まれるなら、そのまま。それ以外は、ロール集合がちょうど1つなら、そのロール(自動選択)、0個・2個以上なら未選択(null)。
   */
  public static String reconfirmActiveRole(String current, Collection<String> roleIds) {
    if (current != null && roleIds.contains(current)) {
      return current;
    }
    return initialActiveRole(roleIds);
  }
}
