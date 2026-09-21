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

import com.mastersmith.common.security.Operator;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.dto.InviteUserRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserChangedEventPublisher;
import com.mastersmith.usermanagement.event.UserSnapshot;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.mail.InvitationMailer;
import com.mastersmith.usermanagement.observation.UserObservations;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.EmailLockRegistry;
import com.mastersmith.usermanagement.security.InvitationAdmission;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ユーザー招待・再招待(W1)の、外側の流れ(reliability-design.md NFR4.2)。このクラス自体はトランザクションを張らず、排他・許可の取得と返却を行い、 {@link
 * TransactionTemplate}で内側の処理を実行する。
 *
 * <pre>
 *  [トランザクションの外側]  認可(401/403) → 入力の検証・正規化(422)
 *                            → 正規化後emailの排他(待機12秒、取れなければ503)
 *                            → 招待の同時実行数の許可(待たない、満杯なら503)
 *  [TransactionTemplateの内側] 既存の検索 → 作成/再招待の条件付き更新/重複(422) → roleIdの実在検証(422)
 *                            → メール送信(失敗・打ち切りは、ロールバックして503)
 *  [トランザクションの外側]  許可・排他の返却(finally) → コミット後にUserChangedEvent(INVITED)を発行
 * </pre>
 *
 * <p>資源の取得順序は、排他 → 許可 → DB接続(逆順には取らない)。排他を先に取るのは、同一emailの待機者が許可を占有しないため。認可・emailの正規化は、
 * 排他・許可の取得より前に行う(認可のない呼び出し元が許可を占有できないようにする)。
 */
@Service
public class InvitationFacade {

  private record Outcome(UserChangedEvent event, UserResponse response) {}

  private final UserRepository userRepository;
  private final TransactionTemplate transaction;
  private final UserAuthorizer authorizer;
  private final PermissionEngineApi permissionEngineApi;
  private final EmailLockRegistry emailLocks;
  private final InvitationAdmission admission;
  private final InvitationMailer mailer;
  private final UserChangedEventPublisher eventPublisher;

  /** 観測(スパン)。既定は何もしない。アプリケーションでは、{@link UserObservations}のBeanが注入される(NFR5.3)。 */
  private UserObservations observations = UserObservations.NOOP;

  @Autowired
  public void setObservations(UserObservations observations) {
    this.observations = observations;
  }

  public InvitationFacade(
      UserRepository userRepository,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      UserAuthorizer authorizer,
      PermissionEngineApi permissionEngineApi,
      EmailLockRegistry emailLocks,
      InvitationAdmission admission,
      InvitationMailer mailer,
      UserChangedEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.transaction = new TransactionTemplate(transactionManager);
    this.authorizer = authorizer;
    this.permissionEngineApi = permissionEngineApi;
    this.emailLocks = emailLocks;
    this.admission = admission;
    this.mailer = mailer;
    this.eventPublisher = eventPublisher;
  }

  /**
   * ユーザーを招待する。同一(正規化後)emailのstatus=invitedのUserがあれば、再招待(新しいトークンを発行し、旧トークンを無効にする)として扱う。
   *
   * @throws UserValidationException 入力の不備・重複(active/disabled)・実在しないroleId・招待中でなくなった再招待の競合
   * @throws com.mastersmith.usermanagement.exception.EmailLockTimeoutException 同一emailの排他の待機超過
   * @throws com.mastersmith.usermanagement.exception.InvitationCapacityExceededException
   *     招待の同時実行数の上限超過
   * @throws com.mastersmith.usermanagement.exception.InvitationMailException
   *     メール送信の失敗・打ち切り・件名の拒否(ロールバック済み)
   */
  public UserResponse invite(Operator operator, InviteUserRequest request) {
    return observations.observe("user.api.invite", () -> doInvite(operator, request));
  }

  private UserResponse doInvite(Operator operator, InviteUserRequest request) {
    authorizer.requireUserAdmin(operator);

    List<UserFieldError> errors = new ArrayList<>();
    String email = UserInputValidator.normalizeEmail(request.email(), errors);
    String name = UserInputValidator.normalizeName(request.name(), errors);
    List<String> roleIds = UserInputValidator.normalizeRoleIds(request.roleIds(), false, errors);
    UiLocale locale = UserInputValidator.parseLocale(request.locale(), false, errors);
    UserValidationException.throwIfAny(errors);

    Outcome outcome;
    try (EmailLockRegistry.Held lock = emailLocks.acquire(email);
        InvitationAdmission.Permit permit = admission.acquire()) {
      outcome = inviteInTransaction(operator, email, name, roleIds, locale);
    }
    eventPublisher.publish(outcome.event());
    return outcome.response();
  }

  private Outcome inviteInTransaction(
      Operator operator, String email, String name, List<String> roleIds, UiLocale locale) {
    try {
      return transaction.execute(
          status -> inviteWithinTransaction(operator, email, name, roleIds, locale));
    } catch (DataIntegrityViolationException e) {
      // 排他をすり抜けた場合(複数プロセス構成など)の、emailの一意制約違反は、重複として422にする(500にしない)。
      throw new UserValidationException(
          UserFieldError.of("email", "user.validation.email.duplicate"));
    }
  }

  private Outcome inviteWithinTransaction(
      Operator operator, String email, String name, List<String> roleIds, UiLocale locale) {
    Optional<User> existing = userRepository.findByEmail(email);
    if (existing.isPresent() && existing.get().getStatus() != UserStatus.INVITED) {
      throw new UserValidationException(
          UserFieldError.of("email", "user.validation.email.duplicate"));
    }
    validateRolesExist(roleIds);

    String token = UUID.randomUUID().toString();
    UserSnapshot before = null;
    User saved;
    if (existing.isEmpty()) {
      saved = userRepository.saveAndFlush(User.invited(name, email, roleIds, token));
    } else {
      User current = existing.get();
      before = UserSnapshot.from(current);
      // 再招待(BR4.11): status=invitedを条件に、新しいトークンとnameを設定する。受諾または取消と競合して0件なら、422。
      if (userRepository.reinvite(current.getUserId(), name, token) == 0) {
        throw new UserValidationException(
            UserFieldError.of("email", "user.validation.invitation.notInvited"));
      }
      saved = userRepository.findById(current.getUserId()).orElseThrow();
      saved.replaceRoleIds(roleIds);
      saved = userRepository.saveAndFlush(saved);
    }

    // メール送信の失敗・打ち切りは、例外で、この内側のトランザクション全体をロールバックする(BR4.16)。
    mailer.send(email, name, locale, token);

    UserChangedEvent event =
        UserChangedEvent.of(
            UserChangeOperation.INVITED,
            saved.getUserId(),
            before,
            UserSnapshot.from(saved),
            operator.userId());
    return new Outcome(event, UserResponse.from(saved));
  }

  private void validateRolesExist(List<String> roleIds) {
    List<UserFieldError> errors = new ArrayList<>();
    for (String roleId : roleIds) {
      if (!permissionEngineApi.roleExists(roleId)) {
        errors.add(
            UserFieldError.of(
                "roleIds", "user.validation.roleIds.unknown", Map.of("roleId", roleId)));
      }
    }
    UserValidationException.throwIfAny(errors);
  }
}
