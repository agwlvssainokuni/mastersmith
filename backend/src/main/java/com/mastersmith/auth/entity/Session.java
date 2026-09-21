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

package com.mastersmith.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * 1回のログインに対して1つ発行される、認証の単位(端末ごとのログイン。entities.md Session)。リフレッシュトークンの状態と、その端末で選択された アクティブロールを保持する。
 *
 * <p>リフレッシュトークンの平文は保持せず、ハッシュだけを保持する(BR5.5)。ハッシュは{@link #toString()}にも出力しない。{@code userId}・{@code
 * activeRoleId}は、U4のUser・U3のRoleへの不透明な参照である。
 *
 * <p>ローテーション・失効・ロール選択の更新は、条件付きの更新({@code SessionRepository})で行い、このエンティティのセッターは持たない (読み取り時点の値を
 * 上書きする、失われた更新を防ぐ。NFR4.1)。
 */
// エンティティ名は、HQLの予約語・org.hibernate.Sessionとの混同を避けるため、AuthSessionとする。
@Entity(name = "AuthSession")
@Table(name = "auth_session")
public class Session implements Persistable<String> {

  @Id
  @Column(name = "session_id", nullable = false, updatable = false, length = 22)
  private String sessionId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private String userId;

  @Column(name = "active_role_id")
  private String activeRoleId;

  @Column(name = "refresh_token_hash", nullable = false, length = 43)
  private String refreshTokenHash;

  @Column(name = "previous_refresh_token_hash", length = 43)
  private String previousRefreshTokenHash;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "last_refreshed_at", nullable = false)
  private Instant lastRefreshedAt;

  @Column(name = "refresh_expires_at", nullable = false)
  private Instant refreshExpiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SessionStatus status;

  /** {@code persist}(新規)か{@code merge}(既存)かの判定のための印。新規に作った直後だけtrue。 */
  @Transient private boolean isNew;

  protected Session() {
    // JPA用
  }

  private Session(
      String sessionId,
      String userId,
      String activeRoleId,
      String refreshTokenHash,
      Instant issuedAt,
      Instant refreshExpiresAt) {
    this.sessionId = sessionId;
    this.userId = userId;
    this.activeRoleId = activeRoleId;
    this.refreshTokenHash = refreshTokenHash;
    this.issuedAt = issuedAt;
    this.lastRefreshedAt = issuedAt;
    this.refreshExpiresAt = refreshExpiresAt;
    this.status = SessionStatus.ACTIVE;
    this.isNew = true;
  }

  /** ログイン成功(W1)で作る、有効な新しいSession。 */
  public static Session issue(
      String sessionId,
      String userId,
      String activeRoleId,
      String refreshTokenHash,
      Instant issuedAt,
      Instant refreshExpiresAt) {
    return new Session(
        sessionId, userId, activeRoleId, refreshTokenHash, issuedAt, refreshExpiresAt);
  }

  /** Sessionが有効か(BR5.11: statusがactiveで、かつ{@code refreshExpiresAt}が経過していない)。 */
  public boolean isValidAt(Instant now) {
    return status == SessionStatus.ACTIVE && refreshExpiresAt.isAfter(now);
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getUserId() {
    return userId;
  }

  public String getActiveRoleId() {
    return activeRoleId;
  }

  public String getRefreshTokenHash() {
    return refreshTokenHash;
  }

  public String getPreviousRefreshTokenHash() {
    return previousRefreshTokenHash;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getLastRefreshedAt() {
    return lastRefreshedAt;
  }

  public Instant getRefreshExpiresAt() {
    return refreshExpiresAt;
  }

  public SessionStatus getStatus() {
    return status;
  }

  @Override
  public String getId() {
    return sessionId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  /** リフレッシュトークンのハッシュ・ロールの値を出力しない(NFR2.7)。 */
  @Override
  public String toString() {
    return "Session[sessionId=" + sessionId + ", status=" + status + "]";
  }
}
