package com.mastersmith.auth.service;

import com.mastersmith.auth.SessionContextApi;
import com.mastersmith.auth.cache.SessionCache;
import com.mastersmith.auth.cache.SessionState;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.exception.SessionNotFoundException;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * C14({@link SessionContextApi})の実装(BR5.13)。認証フィルタと同じ{@link SessionCache}(同じ読み込み)から、Sessionを引く。
 *
 * <p>Sessionが存在しない場合は{@link SessionNotFoundException}、有効でない場合(BR5.11の定義: revoked、またはリフレッシュの有効期限の経過)は{@link
 * SessionExpiredException}を投げる。アクティブロールが未選択の場合は、nullを返す。キャッシュミスで、内部設定DBが使えない場合は、{@link
 * com.mastersmith.auth.exception.AuthStorageUnavailableException}(503)を投げる。
 */
@Service
public class SessionContextService implements SessionContextApi {

  private final SessionCache cache;
  private final Clock clock;

  public SessionContextService(SessionCache cache, Clock clock) {
    this.cache = cache;
    this.clock = clock;
  }

  @Override
  public String getActiveRoleId(String sessionId) {
    SessionState state =
        cache.find(sessionId).orElseThrow(SessionNotFoundException::new);
    if (!state.isValidAt(clock.instant())) {
      throw new SessionExpiredException();
    }
    return state.activeRoleId();
  }
}
