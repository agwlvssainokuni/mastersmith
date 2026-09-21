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
 * <p>Sessionが存在しない場合は{@link SessionNotFoundException}、有効でない場合(BR5.11の定義:
 * revoked、またはリフレッシュの有効期限の経過)は{@link
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
    SessionState state = cache.find(sessionId).orElseThrow(SessionNotFoundException::new);
    if (!state.isValidAt(clock.instant())) {
      throw new SessionExpiredException();
    }
    return state.activeRoleId();
  }
}
