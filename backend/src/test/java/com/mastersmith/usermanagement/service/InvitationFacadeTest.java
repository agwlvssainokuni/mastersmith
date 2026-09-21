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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.common.security.Operator;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.dto.InviteUserRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.exception.EmailLockTimeoutException;
import com.mastersmith.usermanagement.exception.InvitationCapacityExceededException;
import com.mastersmith.usermanagement.exception.InvitationMailException;
import com.mastersmith.usermanagement.exception.InvitationMailException.Failure;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.SubjectRejectedException;
import com.mastersmith.usermanagement.exception.SubjectRejectedException.Rejection;
import com.mastersmith.usermanagement.exception.UserAccessDeniedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.mail.InvitationMailer;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.EmailLockRegistry;
import com.mastersmith.usermanagement.security.InvitationAdmission;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import com.mastersmith.usermanagement.testsupport.EventRecorder;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link InvitationFacade}のテスト(W1、reliability-design.md NFR4.2):
 * 作成・再招待・重複・メール送信失敗のロールバック・排他と許可の返却の時機・許可の満杯・同一emailの
 * 直列化・他のemailの非干渉・競合・DBのロック待ちタイムアウト。コミットを伴う検証のため、テストはトランザクションの外で実行し、作成したUserは後始末で削除する。
 * メール送信(InvitationMailer)とPermissionEngineApi(C10)はモックする。
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvitationFacadeTest {

  private static final Operator ADMIN = new Operator("admin-user", "session-1", "admin-role");
  private static final long AWAIT_SECONDS = 30;

  /** 取得の試行を記録する排他(待っているスレッドの特定用)。 */
  private static final class RecordingLocks extends EmailLockRegistry {
    final List<Thread> attempts = Collections.synchronizedList(new ArrayList<>());

    RecordingLocks(Duration wait) {
      super(wait);
    }

    @Override
    public Held acquire(String normalizedEmail) {
      attempts.add(Thread.currentThread());
      return super.acquire(normalizedEmail);
    }
  }

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final PermissionEngineApi permissionEngineApi = mock(PermissionEngineApi.class);
  private final InvitationMailer mailer = mock(InvitationMailer.class);
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private EventRecorder events = new EventRecorder();
  private RecordingLocks locks;
  private InvitationAdmission admission;
  private InvitationFacade facade;

  @BeforeEach
  void setUp() {
    when(permissionEngineApi.canAccessScreen("admin-role", "user-management")).thenReturn(true);
    when(permissionEngineApi.canAccessScreen("denied-role", "user-management")).thenReturn(false);
    when(permissionEngineApi.roleExists(anyString())).thenReturn(true);
    locks = new RecordingLocks(Duration.ofSeconds(AWAIT_SECONDS));
    admission = new InvitationAdmission(5);
    facade = newFacade(userRepository, events, locks, admission);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    preferenceRepository.deleteAll();
    userRepository.deleteAll();
  }

  private InvitationFacade newFacade(
      UserRepository repository,
      EventRecorder recorder,
      EmailLockRegistry emailLocks,
      InvitationAdmission invitationAdmission) {
    return new InvitationFacade(
        repository,
        transactionManager,
        new UserAuthorizer(permissionEngineApi),
        permissionEngineApi,
        emailLocks,
        invitationAdmission,
        mailer,
        recorder.publisher());
  }

  private static InviteUserRequest request(String email) {
    return new InviteUserRequest(email, "招待 太郎", List.of("r1"), null);
  }

  private static List<String> keys(UserValidationException e) {
    return e.getErrors().stream().map(UserFieldError::message).toList();
  }

  private void assertResourcesReturned() {
    assertThat(locks.size()).isZero();
    assertThat(admission.availablePermits()).isEqualTo(5);
  }

  // ---- 作成 ----

  @Test
  void createsAnInvitedUserSendsTheMailAndPublishesAnInvitedEvent() {
    UserResponse response =
        facade.invite(
            ADMIN,
            new InviteUserRequest(
                "  Invitee@Example.TEST ", "  招待 太郎  ", Arrays.asList("r1", "r1", "r2"), "en"));

    assertThat(response.email()).isEqualTo("invitee@example.test");
    assertThat(response.name()).isEqualTo("招待 太郎");
    assertThat(response.status()).isEqualTo("invited");
    assertThat(response.roleIds()).containsExactly("r1", "r2");
    User stored = userRepository.findByEmail("invitee@example.test").orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(UserStatus.INVITED);
    assertThat(stored.getPasswordHash()).isNull();
    assertThat(stored.getInvitationToken()).isNotBlank();
    assertThat(stored.getRoleIds()).containsExactly("r1", "r2");
    verify(mailer).send("invitee@example.test", "招待 太郎", UiLocale.EN, stored.getInvitationToken());

    UserChangedEvent event = events.single();
    assertThat(event.operation()).isEqualTo(UserChangeOperation.INVITED);
    assertThat(event.beforeValue()).isNull();
    assertThat(event.afterValue().status()).isEqualTo("invited");
    assertThat(event.afterValue().email()).isEqualTo("invitee@example.test");
    assertThat(event.actor()).isEqualTo("admin-user");
    assertThat(event.targetId()).isEqualTo(stored.getUserId());
    assertResourcesReturned();
  }

  @Test
  void theMailLanguageDefaultsToJapanese() {
    facade.invite(ADMIN, request(UserTestFactory.uniqueEmail()));

    verify(mailer).send(anyString(), anyString(), eq(UiLocale.JA), anyString());
  }

  // ---- 再招待 ----

  @Test
  void anInviteForAnInvitedEmailIsAReinviteThatReplacesTheTokenAndKeepsTheUser() {
    String email = UserTestFactory.uniqueEmail();
    UserResponse first = facade.invite(ADMIN, request(email));
    String oldToken = userRepository.findByEmail(email).orElseThrow().getInvitationToken();

    UserResponse second =
        facade.invite(
            ADMIN, new InviteUserRequest(email.toUpperCase(), "再招待 名", List.of("r2"), "en"));

    assertThat(second.userId()).isEqualTo(first.userId());
    User stored = userRepository.findByEmail(email).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(UserStatus.INVITED);
    assertThat(stored.getInvitationToken()).isNotEqualTo(oldToken);
    assertThat(userRepository.findByInvitationToken(oldToken)).isEmpty();
    assertThat(stored.getName()).isEqualTo("再招待 名");
    assertThat(stored.getRoleIds()).containsExactly("r2");
    verify(mailer).send(email, "再招待 名", UiLocale.EN, stored.getInvitationToken());

    List<UserChangedEvent> published = events.events();
    assertThat(published).hasSize(2);
    UserChangedEvent reinvite = published.get(1);
    assertThat(reinvite.operation()).isEqualTo(UserChangeOperation.INVITED);
    assertThat(reinvite.beforeValue()).isNotNull();
    assertThat(reinvite.beforeValue().name()).isEqualTo("招待 太郎");
    assertThat(reinvite.beforeValue().roleIds()).containsExactly("r1");
    assertThat(reinvite.afterValue().name()).isEqualTo("再招待 名");
    assertResourcesReturned();
  }

  // ---- 重複 ----

  @Test
  void anEmailOfAnActiveOrDisabledUserIsADuplicateAndNothingIsSent() {
    String activeEmail = UserTestFactory.uniqueEmail();
    userRepository.saveAndFlush(UserTestFactory.activeUser(activeEmail, List.of()));
    String disabledEmail = UserTestFactory.uniqueEmail();
    User disabled = UserTestFactory.activeUser(disabledEmail, List.of());
    disabled.disable();
    userRepository.saveAndFlush(disabled);

    for (String email : List.of(activeEmail, disabledEmail)) {
      assertThatThrownBy(() -> facade.invite(ADMIN, request(email.toUpperCase())))
          .isInstanceOfSatisfying(
              UserValidationException.class,
              e -> {
                assertThat(keys(e)).containsExactly("user.validation.email.duplicate");
                assertThat(e.getErrors().get(0).field()).isEqualTo("email");
              });
    }

    verify(mailer, never()).send(anyString(), anyString(), any(UiLocale.class), anyString());
    assertThat(events.events()).isEmpty();
    assertResourcesReturned();
  }

  // ---- ロールの実在検証 ----

  @Test
  void anUnknownRoleIsRejectedWith422AndNothingIsCreatedOrSent() {
    when(permissionEngineApi.roleExists("ghost")).thenReturn(false);
    String email = UserTestFactory.uniqueEmail();

    assertThatThrownBy(
            () ->
                facade.invite(
                    ADMIN, new InviteUserRequest(email, "名前", List.of("r1", "ghost"), null)))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> {
              assertThat(keys(e)).containsExactly("user.validation.roleIds.unknown");
              assertThat(e.getErrors().get(0).params()).containsEntry("roleId", "ghost");
            });

    assertThat(userRepository.findByEmail(email)).isEmpty();
    verify(mailer, never()).send(anyString(), anyString(), any(UiLocale.class), anyString());
    assertThat(events.events()).isEmpty();
    assertResourcesReturned();
  }

  // ---- メール送信の失敗: ロールバックと503(イベントなし) ----

  @Test
  void aMailFailureRollsBackTheCreationAndPublishesNoEvent() {
    doThrow(new InvitationMailException(Failure.SEND_FAILED, "smtp down"))
        .when(mailer)
        .send(anyString(), anyString(), any(UiLocale.class), anyString());
    String email = UserTestFactory.uniqueEmail();

    assertThatThrownBy(() -> facade.invite(ADMIN, request(email)))
        .isInstanceOfSatisfying(
            InvitationMailException.class,
            e -> assertThat(e.getFailure()).isEqualTo(Failure.SEND_FAILED));

    assertThat(userRepository.findByEmail(email)).isEmpty();
    assertThat(events.events()).isEmpty();
    assertResourcesReturned();
  }

  @Test
  void aMailFailureOnAReinviteRestoresTheOriginalTokenNameAndRoles() {
    String email = UserTestFactory.uniqueEmail();
    facade.invite(ADMIN, request(email));
    User original = userRepository.findByEmail(email).orElseThrow();
    doThrow(new InvitationMailException(Failure.TIMEOUT, "timed out"))
        .when(mailer)
        .send(anyString(), anyString(), any(UiLocale.class), anyString());

    assertThatThrownBy(
            () -> facade.invite(ADMIN, new InviteUserRequest(email, "別の名前", List.of("r9"), null)))
        .isInstanceOf(InvitationMailException.class);

    User stored = userRepository.findByEmail(email).orElseThrow();
    assertThat(stored.getInvitationToken()).isEqualTo(original.getInvitationToken());
    assertThat(stored.getName()).isEqualTo("招待 太郎");
    assertThat(stored.getRoleIds()).containsExactly("r1");
    assertThat(events.events()).hasSize(1);
    assertResourcesReturned();
  }

  @Test
  void aRejectedSubjectRollsBackLikeAMailFailure() {
    doThrow(new SubjectRejectedException(Rejection.CONTROL_CHARACTER))
        .when(mailer)
        .send(anyString(), anyString(), any(UiLocale.class), anyString());
    String email = UserTestFactory.uniqueEmail();

    assertThatThrownBy(() -> facade.invite(ADMIN, request(email)))
        .isInstanceOf(SubjectRejectedException.class);

    assertThat(userRepository.findByEmail(email)).isEmpty();
    assertThat(events.events()).isEmpty();
    assertResourcesReturned();
  }

  // ---- 排他・許可の返却と、コミット後の発行の時機 ----

  @Test
  void locksAndPermitsAreReturnedAndTheTransactionIsCommittedBeforeTheEventIsPublished() {
    String email = UserTestFactory.uniqueEmail();
    AtomicInteger checks = new AtomicInteger();
    EventRecorder checking =
        new EventRecorder(
            event -> {
              // コミット後の発行: 排他・許可が返却済みで、トランザクションが終わり、Userが確定して読める。
              assertThat(locks.size()).isZero();
              assertThat(admission.availablePermits()).isEqualTo(5);
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(userRepository.findByEmail(email)).isPresent();
              checks.incrementAndGet();
            });

    newFacade(userRepository, checking, locks, admission).invite(ADMIN, request(email));

    assertThat(checks.get()).isEqualTo(1);
    assertThat(checking.events()).hasSize(1);
  }

  // ---- 許可の満杯 ----

  @Test
  void aFullAdmissionIsRejectedWithoutWaitingAndTheEmailLockIsReturned() {
    InvitationAdmission full = new InvitationAdmission(1);
    InvitationAdmission.Permit held = full.acquire();
    String email = UserTestFactory.uniqueEmail();

    long start = System.nanoTime();
    assertThatThrownBy(
            () -> newFacade(userRepository, events, locks, full).invite(ADMIN, request(email)))
        .isInstanceOf(InvitationCapacityExceededException.class);
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertThat(elapsedMillis).isLessThan(5_000);
    assertThat(locks.size()).isZero();
    assertThat(userRepository.findByEmail(email)).isEmpty();
    verify(mailer, never()).send(anyString(), anyString(), any(UiLocale.class), anyString());
    held.close();
    assertThat(full.availablePermits()).isEqualTo(1);
  }

  // ---- 排他 ----

  @Test
  void anEmailLockThatCannotBeAcquiredWithinTheWaitTimesOutAndConsumesNoPermit() throws Exception {
    RecordingLocks shortWait = new RecordingLocks(Duration.ofMillis(200));
    String email = UserTestFactory.uniqueEmail();
    Future<UserResponse> waiter;
    try (EmailLockRegistry.Held held = shortWait.acquire(email)) {
      // 別のスレッドから、同じemailの招待を試みる(同じスレッドでは、排他が再入可能なため)。
      waiter =
          executor.submit(
              () ->
                  newFacade(userRepository, events, shortWait, admission)
                      .invite(ADMIN, request(email)));
      assertThatThrownBy(() -> waiter.get(AWAIT_SECONDS, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(EmailLockTimeoutException.class);
    }

    assertThat(admission.availablePermits()).isEqualTo(5);
    assertThat(userRepository.findByEmail(email)).isEmpty();
    verify(mailer, never()).send(anyString(), anyString(), any(UiLocale.class), anyString());
  }

  @Test
  void concurrentInvitesOfTheSameEmailAreSerializedAndTheSecondBecomesAReinvite() throws Exception {
    String email = UserTestFactory.uniqueEmail();
    AtomicInteger inside = new AtomicInteger();
    AtomicInteger maxInside = new AtomicInteger();
    doAnswer(
            invocation -> {
              maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
              Thread.yield();
              inside.decrementAndGet();
              return null;
            })
        .when(mailer)
        .send(anyString(), anyString(), any(UiLocale.class), anyString());
    CountDownLatch start = new CountDownLatch(1);

    Future<UserResponse> first = executor.submit(() -> inviteAfter(start, email));
    Future<UserResponse> second = executor.submit(() -> inviteAfter(start, email));
    start.countDown();
    UserResponse a = first.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    UserResponse b = second.get(AWAIT_SECONDS, TimeUnit.SECONDS);

    assertThat(maxInside.get()).isEqualTo(1);
    assertThat(a.userId()).isEqualTo(b.userId());
    assertThat(userRepository.findAll()).filteredOn(u -> u.getEmail().equals(email)).hasSize(1);
    List<UserChangedEvent> published = events.events();
    assertThat(published).hasSize(2);
    assertThat(published).filteredOn(e -> e.beforeValue() == null).hasSize(1);
    assertResourcesReturned();
  }

  private UserResponse inviteAfter(CountDownLatch start, String email) throws InterruptedException {
    start.await(AWAIT_SECONDS, TimeUnit.SECONDS);
    return facade.invite(ADMIN, request(email));
  }

  @Test
  void anInviteInProgressForOneEmailDoesNotBlockAnInviteForAnotherEmail() throws Exception {
    String slowEmail = UserTestFactory.uniqueEmail();
    String otherEmail = UserTestFactory.uniqueEmail();
    CountDownLatch slowInMailer = new CountDownLatch(1);
    CountDownLatch otherDone = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              if (slowEmail.equals(invocation.getArgument(0))) {
                slowInMailer.countDown();
                // 別のemailの招待が完了するまで、この招待は、排他・許可・DB接続を保持したまま待つ。
                if (!otherDone.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("the other invite was blocked");
                }
              }
              return null;
            })
        .when(mailer)
        .send(anyString(), anyString(), any(UiLocale.class), anyString());

    Future<UserResponse> slow = executor.submit(() -> facade.invite(ADMIN, request(slowEmail)));
    assertThat(slowInMailer.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    facade.invite(ADMIN, request(otherEmail));
    otherDone.countDown();

    assertThat(slow.get(AWAIT_SECONDS, TimeUnit.SECONDS).email()).isEqualTo(slowEmail);
    assertThat(userRepository.findByEmail(otherEmail)).isPresent();
    assertResourcesReturned();
  }

  @Test
  void aBurstOfInvitesForOneEmailDoesNotOccupyThePermitsNeededByOtherEmails() throws Exception {
    InvitationAdmission twoPermits = new InvitationAdmission(2);
    RecordingLocks recording = new RecordingLocks(Duration.ofSeconds(AWAIT_SECONDS));
    InvitationFacade twoPermitFacade = newFacade(userRepository, events, recording, twoPermits);
    String busyEmail = UserTestFactory.uniqueEmail();
    String otherEmail = UserTestFactory.uniqueEmail();
    CountDownLatch busyInMailer = new CountDownLatch(1);
    CountDownLatch otherDone = new CountDownLatch(1);
    AtomicInteger busyCalls = new AtomicInteger();
    AtomicInteger permitsSeenByOther = new AtomicInteger(-1);
    doAnswer(
            invocation -> {
              if (busyEmail.equals(invocation.getArgument(0))) {
                if (busyCalls.incrementAndGet() == 1) {
                  busyInMailer.countDown();
                  otherDone.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                }
              } else {
                permitsSeenByOther.set(twoPermits.availablePermits());
              }
              return null;
            })
        .when(mailer)
        .send(anyString(), anyString(), any(UiLocale.class), anyString());

    Future<UserResponse> holder =
        executor.submit(() -> twoPermitFacade.invite(ADMIN, request(busyEmail)));
    assertThat(busyInMailer.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    // 同じemailの2件目は、排他の順番待ちになる(許可は取らない)。
    Future<UserResponse> waiter =
        executor.submit(() -> twoPermitFacade.invite(ADMIN, request(busyEmail)));
    Thread waitingThread = awaitSecondAttempt(recording);
    awaitBlocked(waitingThread);

    // 待機者は許可を占有していないため、別のemailの招待は、残りの許可で成功する(503にならない)。
    twoPermitFacade.invite(ADMIN, request(otherEmail));
    otherDone.countDown();

    assertThat(permitsSeenByOther.get()).isZero();
    holder.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    waiter.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    assertThat(twoPermits.availablePermits()).isEqualTo(2);
    assertThat(recording.size()).isZero();
  }

  private static Thread awaitSecondAttempt(RecordingLocks recording) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
    while (recording.attempts.size() < 2) {
      assertThat(System.nanoTime()).isLessThan(deadline);
      Thread.sleep(5);
    }
    return recording.attempts.get(1);
  }

  private static void awaitBlocked(Thread thread) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
    while (thread.getState() != Thread.State.WAITING
        && thread.getState() != Thread.State.TIMED_WAITING) {
      assertThat(System.nanoTime()).isLessThan(deadline);
      Thread.sleep(5);
    }
  }

  // ---- 認可・入力の検証は、排他・許可の取得より前 ----

  @Test
  void authorizationAndValidationHappenBeforeAnyLockOrPermitIsTaken() {
    when(permissionEngineApi.canAccessScreen("denied-role", "user-management")).thenReturn(false);
    InviteUserRequest invalid = new InviteUserRequest("not-an-email", " ", List.of(), "fr");

    assertThatThrownBy(() -> facade.invite(null, request(UserTestFactory.uniqueEmail())))
        .isInstanceOf(OperatorUnresolvedException.class);
    // アクティブロールが未選択(null)は、自前で401にせず、そのままC10へ渡し、権限なしとして403
    // (authentication-serviceの機能設計 BR5.12)。
    assertThatThrownBy(
            () ->
                facade.invite(
                    new Operator("u", "session-1", null), request(UserTestFactory.uniqueEmail())))
        .isInstanceOf(UserAccessDeniedException.class);
    assertThatThrownBy(
            () ->
                facade.invite(
                    new Operator("u", "session-1", "denied-role"),
                    request(UserTestFactory.uniqueEmail())))
        .isInstanceOf(UserAccessDeniedException.class);
    // 認可のない呼び出し元は、入力が不正でも、検証の結果(422)ではなく401/403を受ける。
    assertThatThrownBy(() -> facade.invite(new Operator("u", "session-1", "denied-role"), invalid))
        .isInstanceOf(UserAccessDeniedException.class);
    assertThatThrownBy(() -> facade.invite(ADMIN, invalid))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e ->
                assertThat(keys(e))
                    .containsExactly(
                        "user.validation.email.invalid",
                        "user.validation.name.required",
                        "user.validation.locale.invalid"));

    assertThat(locks.attempts).isEmpty();
    assertThat(admission.availablePermits()).isEqualTo(5);
    assertThat(userRepository.count()).isZero();
    assertThat(events.events()).isEmpty();
  }

  // ---- 競合・DBの異常(リポジトリをモックして再現) ----

  @Test
  void aReinviteThatRacesWithAnAcceptOrCancelIs422() {
    UserRepository racing = mock(UserRepository.class);
    String email = UserTestFactory.uniqueEmail();
    User invited = UserTestFactory.invitedUser(email, List.of("r1"), UserTestFactory.newToken());
    when(racing.findByEmail(email)).thenReturn(Optional.of(invited));
    // 検索の後、更新の前に、受諾または取消されて、招待中でなくなった: 条件付き更新は0件。
    when(racing.reinvite(eq(invited.getUserId()), anyString(), anyString())).thenReturn(0);

    assertThatThrownBy(
            () -> newFacade(racing, events, locks, admission).invite(ADMIN, request(email)))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> assertThat(keys(e)).containsExactly("user.validation.invitation.notInvited"));

    verify(mailer, never()).send(anyString(), anyString(), any(UiLocale.class), anyString());
    assertThat(events.events()).isEmpty();
    assertResourcesReturned();
  }

  @Test
  void aDatabaseLockTimeoutPropagatesAndReturnsLocksAndPermits() {
    UserRepository locked = mock(UserRepository.class);
    String email = UserTestFactory.uniqueEmail();
    User invited = UserTestFactory.invitedUser(email, List.of("r1"), UserTestFactory.newToken());
    when(locked.findByEmail(email)).thenReturn(Optional.of(invited));
    when(locked.reinvite(anyString(), anyString(), anyString()))
        .thenThrow(new PessimisticLockingFailureException("lock timeout"));

    assertThatThrownBy(
            () -> newFacade(locked, events, locks, admission).invite(ADMIN, request(email)))
        .isInstanceOf(PessimisticLockingFailureException.class);

    assertResourcesReturned();
    assertThat(events.events()).isEmpty();
  }

  @Test
  void aUniqueConstraintViolationThatSlippedThroughTheLockIsAFieldLevel422() {
    UserRepository racing = mock(UserRepository.class);
    String email = UserTestFactory.uniqueEmail();
    when(racing.findByEmail(email)).thenReturn(Optional.empty());
    when(racing.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("uk_users_email"));

    assertThatThrownBy(
            () -> newFacade(racing, events, locks, admission).invite(ADMIN, request(email)))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> assertThat(keys(e)).containsExactly("user.validation.email.duplicate"));

    verify(mailer, never()).send(anyString(), anyString(), any(UiLocale.class), anyString());
    assertResourcesReturned();
  }
}
