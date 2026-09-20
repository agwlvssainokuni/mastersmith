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

package com.mastersmith.audit.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.dto.AcceptInvitationRequest;
import com.mastersmith.usermanagement.dto.InviteUserRequest;
import com.mastersmith.usermanagement.dto.UpdateUserRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserChangedEventPublisher;
import com.mastersmith.usermanagement.event.UserSnapshot;
import com.mastersmith.usermanagement.exception.InvitationMailException;
import com.mastersmith.usermanagement.exception.InvitationMailException.Failure;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.mail.InvitationMailer;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.Operator;
import com.mastersmith.usermanagement.service.InvitationAcceptService;
import com.mastersmith.usermanagement.service.InvitationFacade;
import com.mastersmith.usermanagement.service.UserApplicationService;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * user-managementのUserChangedEventが、コミット後の発行で、監査ログ(audit-logging)の行として**実際に永続化される**ことの統合テスト(nfr-design
 * reliability-design.md NFR4.3): 発行後に、
 * **別のスレッド(別のトランザクション)から読める**こと、ロールバック・メール送信の失敗・検証エラーの場合は行が作られないこと、初期管理者の自動作成(BOOTSTRAPPED)が
 * 起動時に記録されること。実際のPublisher・リスナー・サービス・H2を用い、PermissionEngineApi(C10)と招待メールの組み立て・送信(InvitationMailer)だけをモックする。
 */
@SpringBootTest(classes = MastersmithApplication.class)
class UserChangedEventAuditIntegrationTest {

  private static final Operator ADMIN = new Operator("audit-it-admin", "admin-role");

  @Autowired private UserChangedEventPublisher publisher;
  @Autowired private UserApplicationService userService;
  @Autowired private InvitationFacade invitationFacade;
  @Autowired private InvitationAcceptService acceptService;
  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private AuditLogEntryRepository auditRepository;

  @MockitoBean private PermissionEngineApi permissionEngineApi;
  @MockitoBean private InvitationMailer invitationMailer;

  private final List<String> createdEmails = new ArrayList<>();

  @BeforeEach
  void setUp() {
    when(permissionEngineApi.canAccessScreen("admin-role", "user-management")).thenReturn(true);
    when(permissionEngineApi.roleExists(anyString())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    for (String email : createdEmails) {
      userRepository
          .findByEmail(email)
          .ifPresent(
              user -> {
                preferenceRepository.deleteById(user.getUserId());
                userRepository.delete(user);
              });
    }
  }

  /** 監査ログの全行(新しい順)から、条件に合う行を、呼び出しスレッドのトランザクションとは独立に読む。 */
  private List<AuditLogEntry> entries(Predicate<AuditLogEntry> filter) {
    return auditRepository
        .findAll(PageRequest.of(0, 2000, Sort.by(Sort.Direction.DESC, "occurredAt")))
        .stream()
        .filter(filter)
        .toList();
  }

  private List<AuditLogEntry> entriesOfUser(String userId) {
    return entries(e -> userId.equals(e.getTargetId()));
  }

  private List<AuditLogEntry> entriesWithEmail(String email) {
    return entries(e -> e.getAfterValue() != null && email.equals(e.getAfterValue().get("email")));
  }

  private String newEmail() {
    String email = UserTestFactory.uniqueEmail();
    createdEmails.add(email);
    return email;
  }

  // ---- コミット後の発行 → 永続化 ----

  @Test
  void aPublishedEventIsPersistedAndReadableFromAnotherTransaction() throws Exception {
    String userId = "audit-it-" + UUID.randomUUID();
    UserChangedEvent event =
        UserChangedEvent.of(
            UserChangeOperation.UPDATED,
            userId,
            new UserSnapshot("変更前", "before@example.test", "active", List.of("r1")),
            new UserSnapshot("変更後", "after@example.test", "active", List.of("r1", "r2")),
            "audit-it-admin");

    publisher.publish(event);

    // 別のスレッド(別のトランザクション)から読める = 発行側の独立したトランザクションが、確定(コミット)している。
    List<AuditLogEntry> seen =
        CompletableFuture.supplyAsync(() -> entriesOfUser(userId)).get(30, TimeUnit.SECONDS);
    assertThat(seen).hasSize(1);
    AuditLogEntry row = seen.get(0);
    assertThat(row.getTargetType()).isEqualTo("User");
    assertThat(row.getOperationType()).isEqualTo("UPDATED");
    assertThat(row.getActorUserId()).isEqualTo("audit-it-admin");
    assertThat(row.getActorRaw()).isNull();
    assertThat(row.getOccurredAt()).isEqualTo(event.occurredAt());
    assertThat(row.getBeforeValue())
        .containsEntry("name", "変更前")
        .containsEntry("email", "before@example.test")
        .containsEntry("status", "active")
        .containsEntry("roleIds", List.of("r1"));
    assertThat(row.getAfterValue())
        .containsEntry("name", "変更後")
        .containsEntry("roleIds", List.of("r1", "r2"));
    assertThat(row.getAfterValue()).containsOnlyKeys("name", "email", "status", "roleIds");
  }

  @Test
  void anInviteRecordsAnInvitedRowAfterTheCommit() throws Exception {
    String email = newEmail();

    UserResponse invited =
        invitationFacade.invite(ADMIN, new InviteUserRequest(email, "監査 太郎", List.of("r1"), "ja"));

    List<AuditLogEntry> rows =
        CompletableFuture.supplyAsync(() -> entriesOfUser(invited.userId()))
            .get(30, TimeUnit.SECONDS);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getOperationType()).isEqualTo("INVITED");
    assertThat(rows.get(0).getActorUserId()).isEqualTo("audit-it-admin");
    assertThat(rows.get(0).getBeforeValue()).isNull();
    assertThat(rows.get(0).getAfterValue())
        .containsEntry("email", email)
        .containsEntry("status", "invited");
    assertThat(rows.get(0).getAfterValue().toString())
        .doesNotContain("invitationToken")
        .doesNotContain("passwordHash");
  }

  @Test
  void aFailedMailRollsBackTheInviteAndNoAuditRowIsCreated() {
    doThrow(new InvitationMailException(Failure.SEND_FAILED, "smtp down"))
        .when(invitationMailer)
        .send(anyString(), anyString(), any(UiLocale.class), anyString());
    String email = newEmail();

    assertThatThrownBy(
            () ->
                invitationFacade.invite(
                    ADMIN, new InviteUserRequest(email, "監査 花子", List.of(), null)))
        .isInstanceOf(InvitationMailException.class);

    assertThat(userRepository.findByEmail(email)).isEmpty();
    assertThat(entriesWithEmail(email)).isEmpty();
  }

  @Test
  void acceptUpdateAndDisableEachRecordOneRowAndFailuresRecordNone() {
    String email = newEmail();
    UserResponse invited =
        invitationFacade.invite(ADMIN, new InviteUserRequest(email, "監査 三郎", List.of("r1"), null));
    String token = userRepository.findByEmail(email).orElseThrow().getInvitationToken();

    acceptService.accept(
        token, new AcceptInvitationRequest("audit passphrase", "監査 三郎", null, null, null));
    userService.update(
        ADMIN, invited.userId(), new UpdateUserRequest("監査 三郎(更新)", List.of("r1", "r2")));
    userService.disable(ADMIN, invited.userId());

    // 失敗(検証エラー・冪等な再実行・自己の無効化・存在しないroleId)は、ロールバックされ、行を作らない。
    when(permissionEngineApi.roleExists("ghost")).thenReturn(false);
    assertThatThrownBy(
            () ->
                userService.update(
                    ADMIN, invited.userId(), new UpdateUserRequest(" ", List.of("ghost"))))
        .isInstanceOf(UserValidationException.class);
    userService.disable(ADMIN, invited.userId());
    assertThatThrownBy(
            () ->
                userService.disable(new Operator(invited.userId(), "admin-role"), invited.userId()))
        .isInstanceOf(UserValidationException.class);

    List<AuditLogEntry> rows = entriesOfUser(invited.userId());
    assertThat(rows)
        .extracting(AuditLogEntry::getOperationType)
        .containsExactlyInAnyOrder("INVITED", "ACTIVATED", "UPDATED", "DISABLED");
    AuditLogEntry activated =
        rows.stream()
            .filter(r -> r.getOperationType().equals("ACTIVATED"))
            .findFirst()
            .orElseThrow();
    assertThat(activated.getActorUserId()).isEqualTo(invited.userId());
    assertThat(activated.getBeforeValue()).containsEntry("status", "invited");
    assertThat(activated.getAfterValue()).containsEntry("status", "active");
    AuditLogEntry updated =
        rows.stream().filter(r -> r.getOperationType().equals("UPDATED")).findFirst().orElseThrow();
    assertThat(updated.getBeforeValue()).containsEntry("name", "監査 三郎");
    assertThat(updated.getAfterValue()).containsEntry("name", "監査 三郎(更新)");
    AuditLogEntry disabled =
        rows.stream()
            .filter(r -> r.getOperationType().equals("DISABLED"))
            .findFirst()
            .orElseThrow();
    assertThat(disabled.getActorUserId()).isEqualTo("audit-it-admin");
    assertThat(disabled.getAfterValue()).containsEntry("status", "disabled");
  }

  // ---- 起動時の初期管理者の自動作成 ----

  @Test
  void theInitialAdministratorCreationIsRecordedAsBootstrappedWithTheSystemActor() {
    UserResponse admin =
        userService.list(ADMIN).stream()
            .filter(u -> u.email().equals("initial-admin@example.test"))
            .findFirst()
            .orElseThrow();

    List<AuditLogEntry> rows = entriesOfUser(admin.userId());

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getOperationType()).isEqualTo("BOOTSTRAPPED");
    assertThat(rows.get(0).getActorUserId()).isNull();
    assertThat(rows.get(0).getActorRaw()).isEqualTo("system");
    assertThat(rows.get(0).getBeforeValue()).isNull();
    assertThat(rows.get(0).getAfterValue())
        .containsEntry("email", "initial-admin@example.test")
        .containsEntry("status", "active");
  }
}
