package com.mastersmith.auth.cache;

import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.entity.SessionStatus;
import java.time.Instant;

/**
 * キャッシュするSessionの内容(NFR3.2)。パスワード・トークン(平文・ハッシュ)・鍵を含めない。
 *
 * @param userId ログインしたユーザー
 * @param activeRoleId アクティブロール。未選択ならnull
 * @param status Sessionの状態
 * @param refreshExpiresAt リフレッシュの有効期限
 */
public record SessionState(
    String userId, String activeRoleId, SessionStatus status, Instant refreshExpiresAt) {

  static SessionState from(Session session) {
    return new SessionState(
        session.getUserId(),
        session.getActiveRoleId(),
        session.getStatus(),
        session.getRefreshExpiresAt());
  }

  /** Sessionが有効か(BR5.11: statusがactiveで、かつ{@code refreshExpiresAt}が経過していない)。認証フィルタとC14で、共通に用いる。 */
  public boolean isValidAt(Instant now) {
    return status == SessionStatus.ACTIVE && refreshExpiresAt.isAfter(now);
  }
}
