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

import com.mastersmith.auth.entity.AccountLoginState;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ロックの状態({@code account_login_state})の、予約型の原子的な更新(BR5.3、reliability-design.md NFR4.1)。
 *
 * <p>検証の前に、試行の枠を確保する1つの原子的な更新({@link #reserve})を行い、回数がしきい値に達する更新と同時に{@code locked_until}も設定する。
 * 更新の対象の行は、更新のトランザクションが終わるまで、他のトランザクションの更新を待たせる(行のロック)ため、同時の試行が何件あっても、確保できる枠はしきい値を
 * 超えない。SET句の各式は、更新前の値で評価される(標準のSQL・H2の動作)。<b>MySQL・MariaDBは、SET句を左から順に、更新後の値で評価する</b>ため、内部設定DBを
 * これらに変更する場合は、{@link #reserve}の書き直しが必要になる。
 *
 * <p>時刻は、呼び出し側が注入された{@code Clock}の値をパラメータとして渡す(DBの時計は使わない、NFR4.6)。書き込みのメソッドは、トランザクションに参加するだけで、
 * 更新のあと、永続化コンテキストを破棄する。
 */
public interface AccountLoginStateRepository extends JpaRepository<AccountLoginState, String> {

  /**
   * 試行の枠の確保。ロック中でない(しきい値未満、またはロックの解除済み)場合だけ、1行を更新する。ロックの解除済みなら、回数を1(0に戻して数える)、世代を1増やし、
   * それ以外は回数を1増やす。増やした結果がしきい値に達した場合は、同じ更新の中で、{@code locked_until}に{@code newLockedUntil}を設定する。
   *
   * @return 更新の件数(1: 確保できた、0: 行がない・ロック中・想定外の状態)
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE account_login_state
             SET consecutive_failures =
                     CASE WHEN locked_until IS NOT NULL THEN 1 ELSE consecutive_failures + 1 END,
                 generation =
                     CASE WHEN locked_until IS NOT NULL THEN generation + 1 ELSE generation END,
                 locked_until =
                     CASE WHEN (CASE WHEN locked_until IS NOT NULL THEN 1
                                     ELSE consecutive_failures + 1 END) >= :threshold
                          THEN CAST(:newLockedUntil AS TIMESTAMP(3) WITH TIME ZONE)
                          ELSE NULL END
           WHERE user_id = :userId
             AND ((locked_until IS NOT NULL AND locked_until <= :now)
                  OR (locked_until IS NULL AND consecutive_failures < :threshold))
          """,
      nativeQuery = true)
  int reserve(
      @Param("userId") String userId,
      @Param("now") Instant now,
      @Param("newLockedUntil") Instant newLockedUntil,
      @Param("threshold") int threshold);

  /** 行がない場合の、最初の試行の枠の確保(回数1、しきい値に達しない)。同時の作成で主キーの一意制約に違反した場合は、例外になる(呼び出し側がやり直す)。 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO account_login_state (user_id, consecutive_failures, locked_until,"
              + " generation) VALUES (:userId, 1, NULL, 0)",
      nativeQuery = true)
  int insertCounted(@Param("userId") String userId);

  /** 行がない場合の、最初の試行の枠の確保(しきい値が1で、同時にロックする場合)。 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO account_login_state (user_id, consecutive_failures, locked_until,"
              + " generation) VALUES (:userId, 1, :lockedUntil, 0)",
      nativeQuery = true)
  int insertLocked(@Param("userId") String userId, @Param("lockedUntil") Instant lockedUntil);

  /**
   * 想定外の状態(しきい値以上で{@code locked_until}が空)の自己修復。{@code locked_until}を設定する(永続的にロックされたままにならない)。
   *
   * @return 更新の件数
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE account_login_state
             SET locked_until = :newLockedUntil
           WHERE user_id = :userId
             AND locked_until IS NULL
             AND consecutive_failures >= :threshold
          """,
      nativeQuery = true)
  int repair(
      @Param("userId") String userId,
      @Param("newLockedUntil") Instant newLockedUntil,
      @Param("threshold") int threshold);

  /**
   * 条件付きの補償の更新(この予約が、ロックを設定した場合)。世代が予約の時点と同じで、回数が0より大きい場合に限り、回数を1戻し、{@code locked_until}が
   * この予約が設定した値と一致する場合に限り、ロックを解く。条件を満たさない場合は、何もしない(件数0)。
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE account_login_state
             SET consecutive_failures = consecutive_failures - 1,
                 locked_until = CASE WHEN locked_until = :myLockedUntil THEN NULL
                                     ELSE locked_until END
           WHERE user_id = :userId AND generation = :generation AND consecutive_failures > 0
          """,
      nativeQuery = true)
  int compensateWithLock(
      @Param("userId") String userId,
      @Param("generation") long generation,
      @Param("myLockedUntil") Instant myLockedUntil);

  /** 条件付きの補償の更新(この予約が、ロックを設定しなかった場合)。回数だけを1戻し、ロックは変更しない。 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE account_login_state
             SET consecutive_failures = consecutive_failures - 1
           WHERE user_id = :userId AND generation = :generation AND consecutive_failures > 0
          """,
      nativeQuery = true)
  int compensateCount(@Param("userId") String userId, @Param("generation") long generation);

  /** ログイン成功のリセット(回数0、ロックの解除、世代+1)。正しいパスワードでの成功は、しきい値に達した試行であっても、ロックを解く。 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE account_login_state
             SET consecutive_failures = 0, locked_until = NULL, generation = generation + 1
           WHERE user_id = :userId
          """,
      nativeQuery = true)
  int reset(@Param("userId") String userId);
}
