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

package com.mastersmith.usermanagement.repository;

import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link User}のSpring Data JPAリポジトリ(内部設定DB)。
 *
 * <p>条件付き更新(招待受諾・再招待・ログイン成功時のハッシュ更新)は、更新件数を返す。0件なら、条件(状態・トークン・ハッシュ)が
 * すでに満たされていないことを表す。バルク更新のため、永続化コンテキストは更新前にフラッシュし、更新後に破棄する。
 */
public interface UserRepository extends JpaRepository<User, String>, UserRepositoryCustom {

  /** 正規化後(trim・小文字)のemailで検索する。 */
  Optional<User> findByEmail(String email);

  Optional<User> findByInvitationToken(String invitationToken);

  /**
   * 最新のstatusだけを読む(C11の{@code isDisabled}用)。エンティティではなく列の値を問い合わせるため、呼び出し元のトランザクションの永続化コンテキストに
   * 残った古いエンティティではなく、常にDBの最新の値を返す(rules.md BR4.13)。
   */
  @Query("select u.status from User u where u.userId = :userId")
  Optional<UserStatus> findStatusById(@Param("userId") String userId);

  /** ユーザー一覧の射影クエリ(機微項目を含まない)。ロールごとに1行。{@link #findAllSummariesOrderByEmail()}を使う。 */
  @Query(
      "select new com.mastersmith.usermanagement.repository.UserListRow("
          + "u.userId, u.name, u.email, u.status, r) "
          + "from User u left join u.roleIds r order by u.email asc, r asc")
  List<UserListRow> findAllListRowsOrderByEmail();

  /** email昇順の全件(W7、BR4.14)。{@code passwordHash}・{@code invitationToken}は読み出さない。 */
  default List<UserSummary> findAllSummariesOrderByEmail() {
    Map<String, UserListRow> heads = new LinkedHashMap<>();
    Map<String, List<String>> roles = new LinkedHashMap<>();
    for (UserListRow row : findAllListRowsOrderByEmail()) {
      heads.putIfAbsent(row.userId(), row);
      List<String> roleIds = roles.computeIfAbsent(row.userId(), k -> new ArrayList<>());
      if (row.roleId() != null) {
        roleIds.add(row.roleId());
      }
    }
    List<UserSummary> summaries = new ArrayList<>(heads.size());
    heads.forEach(
        (userId, head) ->
            summaries.add(
                new UserSummary(
                    userId,
                    head.name(),
                    head.email(),
                    head.status(),
                    List.copyOf(roles.get(userId)))));
    return summaries;
  }

  /**
   * 招待受諾の条件付き更新(W2、BR4.2): トークンが一致しstatus=invitedの場合に限り、status=active・passwordHash・nameを設定し、
   * invitationTokenをnullにする。並行受諾では、1件だけが1を返し、他方は0を返す。
   *
   * @return 更新件数(1: 成功、0: 未知・使用済み・取消済みのトークン)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update User u set u.status = com.mastersmith.usermanagement.entity.UserStatus.ACTIVE, "
          + "u.passwordHash = :passwordHash, u.name = :name, u.invitationToken = null "
          + "where u.invitationToken = :token "
          + "and u.status = com.mastersmith.usermanagement.entity.UserStatus.INVITED")
  int activateInvitation(
      @Param("token") String token,
      @Param("passwordHash") String passwordHash,
      @Param("name") String name);

  /**
   * 再招待の条件付き更新(W1、BR4.11): status=invitedの場合に限り、nameと新しい招待トークンを設定する(旧トークンは無効になる)。
   * ロールの更新は、この更新の後に、エンティティで行う。
   *
   * @return 更新件数(1: 成功、0: すでに招待中ではない(受諾または取消された))
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update User u set u.name = :name, u.invitationToken = :invitationToken "
          + "where u.userId = :userId "
          + "and u.status = com.mastersmith.usermanagement.entity.UserStatus.INVITED")
  int reinvite(
      @Param("userId") String userId,
      @Param("name") String name,
      @Param("invitationToken") String invitationToken);

  /**
   * ログイン成功時のハッシュ更新の条件付き更新(NFR2.2): status=activeで、passwordHashが検証時と同じ値の場合に限り更新する
   * (パスワードの変更などと競合した場合は更新しない)。
   *
   * @return 更新件数(1: 更新、0: 条件を満たさない)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update User u set u.passwordHash = :newPasswordHash "
          + "where u.userId = :userId "
          + "and u.status = com.mastersmith.usermanagement.entity.UserStatus.ACTIVE "
          + "and u.passwordHash = :expectedPasswordHash")
  int updatePasswordHashIfUnchanged(
      @Param("userId") String userId,
      @Param("expectedPasswordHash") String expectedPasswordHash,
      @Param("newPasswordHash") String newPasswordHash);
}
