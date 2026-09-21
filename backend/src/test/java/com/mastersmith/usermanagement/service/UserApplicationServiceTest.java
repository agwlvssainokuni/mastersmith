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

package com.mastersmith.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.common.security.Operator;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.dto.UpdateUserRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.UserAccessDeniedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserNotFoundException;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import com.mastersmith.usermanagement.testsupport.EventRecorder;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link
 * UserApplicationService}のテスト。永続化はH2(Flyway適用後)に対して検証し、PermissionEngineApi(C10)はモックで、許可・拒否・{@code
 * roleExists}の真偽を 独立に検証する。テーブル駆動の認可拒否専用テスト(team.md必須テスト種別(c)、rules.md BR4.10・BR4.12)を含む。
 */
@DataJpaTest
class UserApplicationServiceTest {

  private static final Operator ADMIN = new Operator("admin-user", "session-1", "admin-role");

  @Autowired private UserRepository userRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TestEntityManager entityManager;

  private final PermissionEngineApi permissionEngineApi = mock(PermissionEngineApi.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final EventRecorder events = new EventRecorder();
  private UserApplicationService service;

  @BeforeEach
  void setUp() {
    when(permissionEngineApi.canAccessScreen("admin-role", "user-management")).thenReturn(true);
    when(permissionEngineApi.canAccessScreen("denied-role", "user-management")).thenReturn(false);
    when(permissionEngineApi.roleExists(anyString())).thenReturn(true);
    service =
        new UserApplicationService(
            userRepository,
            transactionManager,
            new UserAuthorizer(permissionEngineApi),
            permissionEngineApi,
            events.publisher(),
            meterRegistry);
  }

  private User savedUser(String... roleIds) {
    User user =
        userRepository.saveAndFlush(
            UserTestFactory.activeUser(UserTestFactory.uniqueEmail(), Arrays.asList(roleIds)));
    entityManager.clear();
    return user;
  }

  private User reload(String userId) {
    entityManager.clear();
    return userRepository.findById(userId).orElseThrow();
  }

  private double escalationCount() {
    return meterRegistry.get("user.role.escalation.denied").counter().count();
  }

  private static List<String> keys(UserValidationException e) {
    return e.getErrors().stream().map(UserFieldError::message).toList();
  }

  // ---- 認可拒否専用テスト(テーブル駆動): 操作 × 操作者 ----

  private record Operation(String name, BiConsumer<UserApplicationService, Operator> call) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Operation> operations() {
    return Stream.of(
        new Operation("一覧", (s, op) -> s.list(op)),
        new Operation(
            "更新", (s, op) -> s.update(op, "target", new UpdateUserRequest("名前", List.of()))),
        new Operation("無効化", (s, op) -> s.disable(op, "target")));
  }

  static Stream<Arguments> deniedOperators() {
    return operations()
        .flatMap(
            operation ->
                Stream.of(
                    // 操作者(C15)を解決できない場合だけが401。
                    Arguments.of(operation, null, OperatorUnresolvedException.class),
                    // アクティブロールが未選択(null)は、自前で401にせず、そのままC10へ渡し、権限なしとして403
                    // (authentication-serviceの機能設計 BR5.12)。
                    Arguments.of(
                        operation,
                        new Operator("u", "session-1", null),
                        UserAccessDeniedException.class),
                    Arguments.of(
                        operation,
                        new Operator("u", "session-1", "denied-role"),
                        UserAccessDeniedException.class)));
  }

  @ParameterizedTest(name = "{0}: {1} -> {2}")
  @MethodSource("deniedOperators")
  void everyOperationRejectsUnauthenticatedAndUnauthorizedOperatorsWithoutAnyEffect(
      Operation operation, Operator operator, Class<? extends RuntimeException> expected) {
    User target = savedUser("r1");
    long before = userRepository.count();

    assertThatThrownBy(() -> operation.call().accept(service, operator)).isInstanceOf(expected);

    assertThat(userRepository.count()).isEqualTo(before);
    assertThat(reload(target.getUserId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(events.events()).isEmpty();
    verify(permissionEngineApi, never()).roleExists(any());
  }

  // ---- 一覧(W7) ----

  @Test
  void listsUsersOrderedByEmailWithoutSensitiveValues() {
    String prefix = "svc-list-" + UserTestFactory.newToken();
    userRepository.save(UserTestFactory.activeUser(prefix + "-b@example.test", List.of("r2")));
    userRepository.save(
        UserTestFactory.invitedUser(
            prefix + "-a@example.test", List.of(), UserTestFactory.newToken()));
    userRepository.flush();
    entityManager.clear();

    List<UserResponse> listed =
        service.list(ADMIN).stream().filter(u -> u.email().startsWith(prefix)).toList();

    assertThat(listed)
        .extracting(UserResponse::email)
        .containsExactly(prefix + "-a@example.test", prefix + "-b@example.test");
    assertThat(listed.get(0).status()).isEqualTo("invited");
    assertThat(listed.get(1).roleIds()).containsExactly("r2");
    assertThat(listed.toString())
        .doesNotContain(UserTestFactory.DUMMY_PASSWORD_HASH)
        .doesNotContain("invitationToken");
  }

  // ---- 更新(W3) ----

  @Test
  void updatesNameAndRolesAndPublishesAnUpdatedEventWithBeforeAndAfter() {
    User target = savedUser("role-a");

    UserResponse response =
        service.update(
            ADMIN,
            target.getUserId(),
            new UpdateUserRequest("  新しい名前  ", Arrays.asList("role-b", "role-a", "role-b")));

    assertThat(response.name()).isEqualTo("新しい名前");
    assertThat(response.roleIds()).containsExactly("role-a", "role-b");
    User stored = reload(target.getUserId());
    assertThat(stored.getName()).isEqualTo("新しい名前");
    assertThat(stored.getRoleIds()).containsExactly("role-a", "role-b");
    assertThat(stored.getEmail()).isEqualTo(target.getEmail());
    assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(stored.getPasswordHash()).isEqualTo(UserTestFactory.DUMMY_PASSWORD_HASH);

    UserChangedEvent event = events.single();
    assertThat(event.operation()).isEqualTo(UserChangeOperation.UPDATED);
    assertThat(event.targetId()).isEqualTo(target.getUserId());
    assertThat(event.actor()).isEqualTo("admin-user");
    assertThat(event.beforeValue().name()).isEqualTo("有効 花子");
    assertThat(event.beforeValue().roleIds()).containsExactly("role-a");
    assertThat(event.afterValue().name()).isEqualTo("新しい名前");
    assertThat(event.afterValue().roleIds()).containsExactly("role-a", "role-b");
  }

  @Test
  void updatingAnUnknownUserIs404() {
    assertThatThrownBy(
            () -> service.update(ADMIN, "no-such-user", new UpdateUserRequest("名前", List.of())))
        .isInstanceOf(UserNotFoundException.class);

    assertThat(events.events()).isEmpty();
  }

  @Test
  void anAdministratorCannotChangeTheirOwnRoleIds() {
    User self = savedUser("role-a");
    Operator operator = new Operator(self.getUserId(), "session-1", "admin-role");

    assertThatThrownBy(
            () ->
                service.update(
                    operator,
                    self.getUserId(),
                    new UpdateUserRequest("名前", List.of("role-a", "role-admin"))))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> assertThat(keys(e)).contains("user.validation.roleIds.selfChange"));

    assertThat(escalationCount()).isEqualTo(1.0);
    assertThat(events.events()).isEmpty();
    assertThat(reload(self.getUserId()).getRoleIds()).containsExactly("role-a");
    assertThat(reload(self.getUserId()).getName()).isEqualTo("有効 花子");
  }

  @Test
  void removingOwnRolesIsAlsoAChangeAndIsRejected() {
    User self = savedUser("role-a", "role-b");

    assertThatThrownBy(
            () ->
                service.update(
                    new Operator(self.getUserId(), "session-1", "admin-role"),
                    self.getUserId(),
                    new UpdateUserRequest("名前", List.of("role-a"))))
        .isInstanceOf(UserValidationException.class);
    assertThat(escalationCount()).isEqualTo(1.0);
  }

  @Test
  void anAdministratorMayUpdateTheirOwnNameWhenTheRolesAreUnchangedRegardlessOfOrder() {
    User self = savedUser("role-a", "role-b");

    service.update(
        new Operator(self.getUserId(), "session-1", "admin-role"),
        self.getUserId(),
        new UpdateUserRequest("自分の新しい名前", List.of("role-b", "role-a")));

    assertThat(reload(self.getUserId()).getName()).isEqualTo("自分の新しい名前");
    assertThat(escalationCount()).isZero();
    assertThat(events.events()).hasSize(1);
  }

  @Test
  void anAdministratorMayChangeAnotherUsersRoles() {
    User other = savedUser("role-a");

    service.update(ADMIN, other.getUserId(), new UpdateUserRequest("名前", List.of("role-z")));

    assertThat(reload(other.getUserId()).getRoleIds()).containsExactly("role-z");
    assertThat(escalationCount()).isZero();
  }

  @Test
  void aNewRoleThatDoesNotExistIsRejectedWith422NamingTheRoleId() {
    User target = savedUser("role-a");
    when(permissionEngineApi.roleExists("ghost-role")).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN,
                    target.getUserId(),
                    new UpdateUserRequest("名前", List.of("role-a", "ghost-role"))))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> {
              assertThat(keys(e)).containsExactly("user.validation.roleIds.unknown");
              assertThat(e.getErrors().get(0).params()).containsEntry("roleId", "ghost-role");
            });

    assertThat(reload(target.getUserId()).getRoleIds()).containsExactly("role-a");
    assertThat(events.events()).isEmpty();
  }

  @Test
  void anAlreadyAssignedRoleIsNotCheckedAgainSoADanglingRoleDoesNotBlockOtherUpdates() {
    User target = savedUser("dangling-role");
    when(permissionEngineApi.roleExists("dangling-role")).thenReturn(false);

    service.update(
        ADMIN, target.getUserId(), new UpdateUserRequest("名前だけ変更", List.of("dangling-role")));

    assertThat(reload(target.getUserId()).getName()).isEqualTo("名前だけ変更");
    verify(permissionEngineApi, never()).roleExists("dangling-role");
  }

  static Stream<Arguments> invalidNames() {
    return Stream.of(
        Arguments.of(null, "user.validation.name.required"),
        Arguments.of("   ", "user.validation.name.required"),
        Arguments.of("a".repeat(101), "user.validation.name.tooLong"),
        Arguments.of("a\r\nBcc: x@example.test", "user.validation.name.controlCharacter"));
  }

  @ParameterizedTest
  @MethodSource("invalidNames")
  void anInvalidNameIs422WithAFieldLevelKey(String name, String expectedKey) {
    User target = savedUser("role-a");

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN, target.getUserId(), new UpdateUserRequest(name, List.of("role-a"))))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> {
              assertThat(keys(e)).containsExactly(expectedKey);
              assertThat(e.getErrors().get(0).field()).isEqualTo("name");
            });

    assertThat(reload(target.getUserId()).getName()).isEqualTo("有効 花子");
    assertThat(events.events()).isEmpty();
  }

  @Test
  void specifyingFieldsOtherThanNameAndRoleIdsIs422InsteadOfBeingIgnored() {
    User target = savedUser("role-a");

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN,
                    target.getUserId(),
                    new UpdateUserRequest(
                        "名前", List.of("role-a"), List.of("email", "status", "passwordHash"))))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> {
              assertThat(e.getErrors())
                  .extracting(UserFieldError::field)
                  .containsExactly("email", "status", "passwordHash");
              assertThat(keys(e)).containsOnly("user.validation.field.unsupported");
            });

    User stored = reload(target.getUserId());
    assertThat(stored.getName()).isEqualTo("有効 花子");
    assertThat(stored.getEmail()).isEqualTo(target.getEmail());
  }

  @Test
  void aMissingRoleIdsListIsRejectedBecauseThePutReplacesTheRoles() {
    User target = savedUser("role-a");

    assertThatThrownBy(
            () -> service.update(ADMIN, target.getUserId(), new UpdateUserRequest("名前", null)))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> assertThat(keys(e)).containsExactly("user.validation.roleIds.required"));
  }

  @Test
  void allValidationErrorsAreReportedTogether() {
    User target = savedUser("role-a");
    when(permissionEngineApi.roleExists("ghost")).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN,
                    target.getUserId(),
                    new UpdateUserRequest(" ", List.of("ghost"), List.of("status"))))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e ->
                assertThat(keys(e))
                    .containsExactly(
                        "user.validation.name.required",
                        "user.validation.field.unsupported",
                        "user.validation.roleIds.unknown"));
  }

  // ---- 無効化・招待の取消(W4) ----

  @Test
  void disablesAnActiveUserAndPublishesADisabledEvent() {
    User target = savedUser("role-a");

    service.disable(ADMIN, target.getUserId());

    User stored = reload(target.getUserId());
    assertThat(stored.getStatus()).isEqualTo(UserStatus.DISABLED);
    // 物理削除・匿名化はしない(NFR2.6)。
    assertThat(stored.getEmail()).isEqualTo(target.getEmail());
    UserChangedEvent event = events.single();
    assertThat(event.operation()).isEqualTo(UserChangeOperation.DISABLED);
    assertThat(event.actor()).isEqualTo("admin-user");
    assertThat(event.beforeValue().status()).isEqualTo("active");
    assertThat(event.afterValue().status()).isEqualTo("disabled");
  }

  @Test
  void cancellingAnInvitationDisablesTheUserAndClearsTheToken() {
    User invited =
        userRepository.saveAndFlush(
            UserTestFactory.invitedUser(
                UserTestFactory.uniqueEmail(), List.of("r1"), UserTestFactory.newToken()));
    entityManager.clear();

    service.disable(ADMIN, invited.getUserId());

    User stored = reload(invited.getUserId());
    assertThat(stored.getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(stored.getInvitationToken()).isNull();
    assertThat(userRepository.findByInvitationToken(invited.getInvitationToken())).isEmpty();
    assertThat(events.single().beforeValue().status()).isEqualTo("invited");
  }

  @Test
  void disablingAnAlreadyDisabledUserIsIdempotentAndPublishesNothing() {
    User target = savedUser("role-a");
    service.disable(ADMIN, target.getUserId());
    events.events();

    service.disable(ADMIN, target.getUserId());

    assertThat(reload(target.getUserId()).getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(events.events()).hasSize(1);
  }

  @Test
  void anAdministratorCannotDisableThemselves() {
    User self = savedUser("role-a");

    assertThatThrownBy(
            () ->
                service.disable(
                    new Operator(self.getUserId(), "session-1", "admin-role"), self.getUserId()))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> assertThat(keys(e)).containsExactly("user.validation.self.disable"));

    assertThat(reload(self.getUserId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(events.events()).isEmpty();
  }

  @Test
  void disablingAnUnknownUserIs404() {
    assertThatThrownBy(() -> service.disable(ADMIN, "no-such-user"))
        .isInstanceOf(UserNotFoundException.class);
    assertThat(events.events()).isEmpty();
  }
}
