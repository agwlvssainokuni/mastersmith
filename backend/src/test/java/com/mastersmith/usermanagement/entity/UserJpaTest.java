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

package com.mastersmith.usermanagement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@link User}のJPAマッピングとマイグレーション(V4)の整合(ddl-auto: validate)・制約の確認。Flywayのマイグレーションを適用したスキーマに対して
 * 検証する(スキーマの自動生成には置き換えない)。
 */
@DataJpaTest
class UserJpaTest {

  @Autowired private UserRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void persistsAndReloadsAnInvitedUserWithoutPasswordHash() {
    User invited = UserTestFactory.invitedUser();
    repository.saveAndFlush(invited);
    entityManager.clear();

    User found = repository.findById(invited.getUserId()).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(UserStatus.INVITED);
    assertThat(found.getPasswordHash()).isNull();
    assertThat(found.getInvitationToken()).isEqualTo(invited.getInvitationToken());
    assertThat(found.getName()).isEqualTo("招待 太郎");
    assertThat(found.getEmail()).isEqualTo(invited.getEmail());
  }

  @Test
  void persistsAnActiveUserWithPasswordHashAndNoInvitationToken() {
    User active = UserTestFactory.activeUser();
    repository.saveAndFlush(active);
    entityManager.clear();

    User found = repository.findById(active.getUserId()).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(found.getPasswordHash()).isEqualTo(UserTestFactory.DUMMY_PASSWORD_HASH);
    assertThat(found.getInvitationToken()).isNull();
  }

  @Test
  void persistsRoleIdsAsAnElementCollectionAndReturnsThemSorted() {
    User user =
        UserTestFactory.activeUser(UserTestFactory.uniqueEmail(), List.of("role-z", "role-a"));
    repository.saveAndFlush(user);
    entityManager.clear();

    assertThat(repository.findById(user.getUserId()).orElseThrow().getRoleIds())
        .containsExactly("role-a", "role-z");
  }

  @Test
  void replacesRoleIds() {
    User user = UserTestFactory.activeUser(UserTestFactory.uniqueEmail(), List.of("role-a"));
    repository.saveAndFlush(user);

    user.replaceRoleIds(List.of("role-b", "role-c"));
    repository.saveAndFlush(user);
    entityManager.clear();

    assertThat(repository.findById(user.getUserId()).orElseThrow().getRoleIds())
        .containsExactly("role-b", "role-c");
  }

  @Test
  void allowsAUserWithoutRoleIds() {
    User user = UserTestFactory.activeUser(UserTestFactory.uniqueEmail(), List.of());
    repository.saveAndFlush(user);
    entityManager.clear();

    assertThat(repository.findById(user.getUserId()).orElseThrow().getRoleIds()).isEmpty();
  }

  @Test
  void rejectsADuplicateEmail() {
    String email = UserTestFactory.uniqueEmail();
    repository.saveAndFlush(UserTestFactory.activeUser(email, List.of()));

    assertThatThrownBy(() -> repository.saveAndFlush(UserTestFactory.activeUser(email, List.of())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsADuplicateInvitationToken() {
    String token = UserTestFactory.newToken();
    repository.saveAndFlush(
        UserTestFactory.invitedUser(UserTestFactory.uniqueEmail(), List.of(), token));

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(
                    UserTestFactory.invitedUser(UserTestFactory.uniqueEmail(), List.of(), token)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void allowsManyUsersWithoutAnInvitationToken() {
    // invitationTokenは非nullのときだけ一意(nullは一意制約の対象外)。
    repository.saveAndFlush(UserTestFactory.activeUser());
    repository.saveAndFlush(UserTestFactory.activeUser());
    repository.saveAndFlush(UserTestFactory.activeUser());

    assertThat(repository.count()).isGreaterThanOrEqualTo(3);
  }

  @Test
  void disableChangesStatusAndClearsTheInvitationToken() {
    User invited = UserTestFactory.invitedUser();
    repository.saveAndFlush(invited);

    invited.disable();
    repository.saveAndFlush(invited);
    entityManager.clear();

    User found = repository.findById(invited.getUserId()).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(found.getInvitationToken()).isNull();
  }

  @Test
  void toStringDoesNotExposeSecretsOrPersonalInformation() {
    User user = UserTestFactory.activeUser();

    assertThat(user.toString())
        .doesNotContain(UserTestFactory.DUMMY_PASSWORD_HASH)
        .doesNotContain(user.getEmail())
        .doesNotContain(user.getName());
  }
}
