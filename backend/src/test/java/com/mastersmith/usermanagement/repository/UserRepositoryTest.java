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

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** {@link UserRepository}のテスト: 検索、一覧の射影、条件付き更新の更新件数。 */
@DataJpaTest
class UserRepositoryTest {

  @Autowired private UserRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void findsByEmailAndInvitationToken() {
    String token = UserTestFactory.newToken();
    User invited =
        repository.saveAndFlush(
            UserTestFactory.invitedUser(UserTestFactory.uniqueEmail(), List.of("r1"), token));

    assertThat(repository.findByEmail(invited.getEmail())).contains(invited);
    assertThat(repository.findByInvitationToken(token)).contains(invited);
    assertThat(repository.findByEmail("nobody@example.test")).isEmpty();
    assertThat(repository.findByInvitationToken(UserTestFactory.newToken())).isEmpty();
  }

  @Test
  void listsSummariesOrderedByEmailWithRolesAndWithoutSensitiveValues() {
    String prefix = "list-" + UserTestFactory.newToken();
    User b =
        repository.save(
            UserTestFactory.activeUser(prefix + "-b@example.test", List.of("role-z", "role-a")));
    User a = repository.save(UserTestFactory.activeUser(prefix + "-a@example.test", List.of()));
    User c =
        repository.save(
            UserTestFactory.invitedUser(
                prefix + "-c@example.test", List.of("role-x"), UserTestFactory.newToken()));
    repository.flush();
    entityManager.clear();

    List<UserSummary> summaries =
        repository.findAllSummariesOrderByEmail().stream()
            .filter(s -> s.email().startsWith(prefix))
            .toList();

    assertThat(summaries)
        .extracting(UserSummary::userId)
        .containsExactly(a.getUserId(), b.getUserId(), c.getUserId());
    assertThat(summaries.get(0).roleIds()).isEmpty();
    assertThat(summaries.get(1).roleIds()).containsExactly("role-a", "role-z");
    assertThat(summaries.get(2).roleIds()).containsExactly("role-x");
    assertThat(summaries.get(2).status()).isEqualTo(UserStatus.INVITED);
    // 射影の型は、passwordHash・invitationTokenを持たない。
    assertThat(Arrays.stream(UserSummary.class.getRecordComponents()).map(rc -> rc.getName()))
        .containsExactly("userId", "name", "email", "status", "roleIds");
  }

  @Test
  void activateInvitationSucceedsOnceAndClearsTheToken() {
    String token = UserTestFactory.newToken();
    User invited =
        repository.saveAndFlush(
            UserTestFactory.invitedUser(UserTestFactory.uniqueEmail(), List.of("r1"), token));

    int first = repository.activateInvitation(token, UserTestFactory.DUMMY_PASSWORD_HASH, "新氏名");
    int second = repository.activateInvitation(token, UserTestFactory.DUMMY_PASSWORD_HASH, "新氏名");

    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    User found = repository.findById(invited.getUserId()).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(found.getPasswordHash()).isEqualTo(UserTestFactory.DUMMY_PASSWORD_HASH);
    assertThat(found.getName()).isEqualTo("新氏名");
    assertThat(found.getInvitationToken()).isNull();
    assertThat(found.getRoleIds()).containsExactly("r1");
  }

  @Test
  void activateInvitationUpdatesNothingForUnknownOrCancelledTokens() {
    String token = UserTestFactory.newToken();
    User invited =
        repository.saveAndFlush(
            UserTestFactory.invitedUser(UserTestFactory.uniqueEmail(), List.of(), token));
    invited.disable();
    repository.saveAndFlush(invited);

    assertThat(repository.activateInvitation(token, "hash", "n")).isZero();
    assertThat(repository.activateInvitation(UserTestFactory.newToken(), "hash", "n")).isZero();
  }

  @Test
  void reinviteReplacesTheTokenOnlyWhileInvited() {
    String oldToken = UserTestFactory.newToken();
    User invited =
        repository.saveAndFlush(
            UserTestFactory.invitedUser(UserTestFactory.uniqueEmail(), List.of(), oldToken));
    String newToken = UserTestFactory.newToken();

    assertThat(repository.reinvite(invited.getUserId(), "再招待 名", newToken)).isEqualTo(1);

    User reloaded = repository.findById(invited.getUserId()).orElseThrow();
    assertThat(reloaded.getInvitationToken()).isEqualTo(newToken);
    assertThat(reloaded.getName()).isEqualTo("再招待 名");
    assertThat(repository.findByInvitationToken(oldToken)).isEmpty();

    // 受諾された後は、再招待の条件付き更新は0件になる。
    repository.activateInvitation(newToken, "hash", "n");
    assertThat(repository.reinvite(invited.getUserId(), "x", UserTestFactory.newToken())).isZero();
  }

  @Test
  void updatePasswordHashIfUnchangedRequiresTheExpectedHashAndActiveStatus() {
    User active = repository.saveAndFlush(UserTestFactory.activeUser());

    // 検証時と異なるハッシュが保存されている場合(パスワード変更との競合)は、更新しない。
    assertThat(repository.updatePasswordHashIfUnchanged(active.getUserId(), "other", "new"))
        .isZero();
    assertThat(
            repository.updatePasswordHashIfUnchanged(
                active.getUserId(), UserTestFactory.DUMMY_PASSWORD_HASH, "new-hash"))
        .isEqualTo(1);
    assertThat(repository.findById(active.getUserId()).orElseThrow().getPasswordHash())
        .isEqualTo("new-hash");

    User reloaded = repository.findById(active.getUserId()).orElseThrow();
    reloaded.disable();
    repository.saveAndFlush(reloaded);
    assertThat(repository.updatePasswordHashIfUnchanged(active.getUserId(), "new-hash", "x"))
        .isZero();
  }

  @Test
  void findByIdForUpdateReturnsTheUserWithItsRoles() {
    User user =
        repository.saveAndFlush(
            UserTestFactory.activeUser(UserTestFactory.uniqueEmail(), List.of("r1", "r2")));
    entityManager.clear();

    Optional<User> locked = repository.findByIdForUpdate(user.getUserId());

    assertThat(locked).isPresent();
    assertThat(locked.get().getRoleIds()).containsExactly("r1", "r2");
    assertThat(repository.findByIdForUpdate("no-such-user")).isEmpty();
  }

  @Test
  void listProjectionQueryDoesNotSelectSensitiveColumns() {
    // 射影クエリのJPQLが、機微な列を選択していないこと(型に加えて、クエリ文字列でも確認する)。
    String query =
        Arrays.stream(UserRepository.class.getMethods())
            .filter(m -> m.getName().equals("findAllListRowsOrderByEmail"))
            .findFirst()
            .orElseThrow()
            .getAnnotation(org.springframework.data.jpa.repository.Query.class)
            .value();

    assertThat(query).doesNotContain("passwordHash").doesNotContain("invitationToken");
  }
}
