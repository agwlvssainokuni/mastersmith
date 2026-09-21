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

package com.mastersmith.auth.repository;

import com.mastersmith.auth.entity.Session;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Sessionの永続化(内部設定DB、{@code auth_session})。ローテーション・失効・ロール選択の更新は、条件付きの更新で、更新の件数(1件・0件)で成否を判定する
 * (reliability-design.md NFR4.1)。日時は、呼び出し側が注入された{@code Clock}の値をパラメータとして渡す(DBの時計は使わない、NFR4.6)。
 *
 * <p>書き込みのメソッドは、トランザクションに参加するだけである(トランザクションの境界は{@code
 * AuthenticationApplicationService}が所有する)。更新のあと、永続化コンテキストを破棄して、古い値を読まないようにする。
 */
public interface SessionRepository extends JpaRepository<Session, String> {

  Optional<Session> findByRefreshTokenHash(String refreshTokenHash);

  Optional<Session> findByPreviousRefreshTokenHash(String previousRefreshTokenHash);

  /**
   * リフレッシュのローテーション(アクティブロールが、読み取り時点で選択済みの場合)。現在のハッシュが同一で、有効で、アクティブロールが読み取り時点と等しい場合に限り、
   * 更新する(同一のトークンの同時の更新で、1件だけが成功する)。
   *
   * @return 更新の件数(1: 成功、0: 条件を満たさない)
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE auth_session
             SET previous_refresh_token_hash = refresh_token_hash,
                 refresh_token_hash = :newHash,
                 last_refreshed_at = :now,
                 refresh_expires_at = :newRefreshExpiresAt,
                 active_role_id = :newRoleId
           WHERE session_id = :sessionId
             AND refresh_token_hash = :oldHash
             AND status = 'ACTIVE'
             AND refresh_expires_at > :now
             AND active_role_id = :readRoleId
          """,
      nativeQuery = true)
  int rotateWithRole(
      @Param("sessionId") String sessionId,
      @Param("oldHash") String oldHash,
      @Param("newHash") String newHash,
      @Param("now") Instant now,
      @Param("newRefreshExpiresAt") Instant newRefreshExpiresAt,
      @Param("newRoleId") String newRoleId,
      @Param("readRoleId") String readRoleId);

  /** リフレッシュのローテーション(アクティブロールが、読み取り時点で未選択の場合)。 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE auth_session
             SET previous_refresh_token_hash = refresh_token_hash,
                 refresh_token_hash = :newHash,
                 last_refreshed_at = :now,
                 refresh_expires_at = :newRefreshExpiresAt,
                 active_role_id = :newRoleId
           WHERE session_id = :sessionId
             AND refresh_token_hash = :oldHash
             AND status = 'ACTIVE'
             AND refresh_expires_at > :now
             AND active_role_id IS NULL
          """,
      nativeQuery = true)
  int rotateWithoutRole(
      @Param("sessionId") String sessionId,
      @Param("oldHash") String oldHash,
      @Param("newHash") String newHash,
      @Param("now") Instant now,
      @Param("newRefreshExpiresAt") Instant newRefreshExpiresAt,
      @Param("newRoleId") String newRoleId);

  /**
   * Sessionを失効させる(冪等)。すでにrevokedでも、エラーにしない。
   *
   * @return 更新の件数(0: すでに失効済み、または存在しない)
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE auth_session SET status = 'REVOKED' WHERE session_id = :sessionId AND status ="
              + " 'ACTIVE'",
      nativeQuery = true)
  int revoke(@Param("sessionId") String sessionId);

  /**
   * アクティブロールを更新する(ロール選択)。{@code session_id}と{@code status = 'ACTIVE'}だけを条件とする(リフレッシュの更新が先にコミットされて
   * いれば、その後に、選択が反映される)。
   *
   * @return 更新の件数(0: 有効でない、または存在しない)
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE auth_session SET active_role_id = :roleId WHERE session_id = :sessionId AND"
              + " status = 'ACTIVE'",
      nativeQuery = true)
  int updateActiveRole(@Param("sessionId") String sessionId, @Param("roleId") String roleId);

  /** 定期削除の対象のSessionIdを、有効期限の昇順に、{@code pageable}の件数まで返す(statusを問わない)。 */
  @Query(
      "select s.sessionId from AuthSession s where s.refreshExpiresAt < :cutoff order by"
          + " s.refreshExpiresAt")
  List<String> findExpiredIds(@Param("cutoff") Instant cutoff, Pageable pageable);

  /**
   * 期限切れのSessionを削除する(定期削除)。{@code refresh_expires_at}の条件を再確認して、有効なSessionを削除しない。
   *
   * @return 削除した行数
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from AuthSession s where s.sessionId in :ids and s.refreshExpiresAt < :cutoff")
  int deleteExpiredByIds(@Param("ids") List<String> ids, @Param("cutoff") Instant cutoff);

  /**
   * すべてのSessionを削除する(起動時の全Sessionの失効、{@code revoke-all-on-startup})。
   *
   * @return 削除した行数
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from AuthSession")
  int deleteAllSessions();
}
