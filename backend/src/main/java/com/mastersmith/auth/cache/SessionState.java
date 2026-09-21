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
