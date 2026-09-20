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

import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.dto.UpdateUserRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserChangedEventPublisher;
import com.mastersmith.usermanagement.event.UserSnapshot;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserNotFoundException;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.observation.UserObservations;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.Operator;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ユーザーの一覧・更新・無効化の業務処理(functional-spec.md W3・W4・W7、rules.md
 * BR4.5・BR4.6・BR4.9・BR4.10・BR4.12・BR4.14・BR4.15)。
 *
 * <p>認可({@link UserAuthorizer})を、各操作の先頭で、必ずサーバー側で行う(NFR2.1)。{@code @Transactional}は使わず、{@link
 * TransactionTemplate}で囲む(reliability-design.md「資源の取得順序」)。更新・無効化は、User行を行ロック付きで読み取り(悲観的な書き込みロック)、同一Userへの
 * 更新を直列化して、beforeValueが常に直前の確定した値になるようにする(NFR4.1)。{@link
 * UserChangedEvent}は、コミットが終わった後に発行する(NFR4.3)。
 */
@Service
public class UserApplicationService {

  private static final Logger LOG = LoggerFactory.getLogger(UserApplicationService.class);

  /** コミット後に発行するイベントと応答。イベントがnullなら、発行しない(冪等な再実行)。 */
  private record Change(UserChangedEvent event, UserResponse response) {}

  private final UserRepository userRepository;
  private final TransactionTemplate transaction;
  private final UserAuthorizer authorizer;
  private final PermissionEngineApi permissionEngineApi;
  private final UserChangedEventPublisher eventPublisher;
  private final Counter escalationDeniedCounter;

  /** 観測(スパン)。既定は何もしない。アプリケーションでは、{@link UserObservations}のBeanが注入される(NFR5.3)。 */
  private UserObservations observations = UserObservations.NOOP;

  @Autowired
  public void setObservations(UserObservations observations) {
    this.observations = observations;
  }

  public UserApplicationService(
      UserRepository userRepository,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      UserAuthorizer authorizer,
      PermissionEngineApi permissionEngineApi,
      UserChangedEventPublisher eventPublisher,
      MeterRegistry meterRegistry) {
    this.userRepository = userRepository;
    this.transaction = new TransactionTemplate(transactionManager);
    this.authorizer = authorizer;
    this.permissionEngineApi = permissionEngineApi;
    this.eventPublisher = eventPublisher;
    this.escalationDeniedCounter =
        Counter.builder("user.role.escalation.denied")
            .description("自分自身のroleIds変更の拒否(権限昇格の防止、BR4.12)")
            .register(meterRegistry);
  }

  /** ユーザー一覧(W7)。email昇順の全件。{@code passwordHash}・{@code invitationToken}は含めない。 */
  public List<UserResponse> list(Operator operator) {
    return observations.observe("user.api.list", () -> doList(operator));
  }

  private List<UserResponse> doList(Operator operator) {
    authorizer.requireUserAdmin(operator);
    return userRepository.findAllSummariesOrderByEmail().stream().map(UserResponse::from).toList();
  }

  /**
   * ユーザー情報の更新(W3)。更新可能な項目は{@code name}と{@code roleIds}のみ。
   *
   * @throws UserNotFoundException 対象が存在しない場合
   * @throws UserValidationException nameの不備・更新不可項目の指定・自己のroleIds変更・実在しないroleId(フィールド単位)
   */
  public UserResponse update(Operator operator, String userId, UpdateUserRequest request) {
    return observations.observe("user.api.update", () -> doUpdate(operator, userId, request));
  }

  private UserResponse doUpdate(Operator operator, String userId, UpdateUserRequest request) {
    authorizer.requireUserAdmin(operator);
    Change change = transaction.execute(status -> applyUpdate(operator, userId, request));
    eventPublisher.publish(change.event());
    return change.response();
  }

  /**
   * ユーザーの無効化・招待の取消(W4)。すでにdisabledなら、何もせず、イベントも発行しない(冪等)。
   *
   * @throws UserNotFoundException 対象が存在しない場合
   * @throws UserValidationException 自分自身の無効化
   */
  public void disable(Operator operator, String userId) {
    observations.run("user.api.disable", () -> doDisable(operator, userId));
  }

  private void doDisable(Operator operator, String userId) {
    authorizer.requireUserAdmin(operator);
    Change change = transaction.execute(status -> applyDisable(operator, userId));
    if (change.event() != null) {
      eventPublisher.publish(change.event());
    }
  }

  private Change applyUpdate(Operator operator, String userId, UpdateUserRequest request) {
    User user = userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);

    List<UserFieldError> errors = new ArrayList<>();
    String name = UserInputValidator.normalizeName(request.name(), errors);
    List<String> roleIds = UserInputValidator.normalizeRoleIds(request.roleIds(), true, errors);
    for (String unsupported : request.unsupportedFields()) {
      errors.add(UserFieldError.of(unsupported, "user.validation.field.unsupported"));
    }
    if (roleIds != null) {
      validateRoleChange(operator, user, roleIds, errors);
    }
    UserValidationException.throwIfAny(errors);

    UserSnapshot before = UserSnapshot.from(user);
    user.setName(name);
    user.replaceRoleIds(roleIds);
    userRepository.saveAndFlush(user);

    UserChangedEvent event =
        UserChangedEvent.of(
            UserChangeOperation.UPDATED,
            user.getUserId(),
            before,
            UserSnapshot.from(user),
            operator.userId());
    return new Change(event, UserResponse.from(user));
  }

  /** 自己のroleIds変更の拒否(BR4.12)と、新たに付与するroleIdの実在検証(BR4.5)。 */
  private void validateRoleChange(
      Operator operator, User target, List<String> roleIds, List<UserFieldError> errors) {
    List<String> current = target.getRoleIds();
    boolean changed = !new HashSet<>(current).equals(new HashSet<>(roleIds));
    if (changed && target.getUserId().equals(operator.userId())) {
      errors.add(UserFieldError.of("roleIds", "user.validation.roleIds.selfChange"));
      escalationDeniedCounter.increment();
      LOG.warn(
          "Role escalation denied: operatorUserId={}, targetUserId={}",
          operator.userId(),
          target.getUserId());
    }
    // 実在検証の対象は、新たに付与するroleIdのみ(すでに付与済みのroleIdが、後からpermission-engine側で削除されても、
    // 他の項目の更新を妨げない)。
    for (String roleId : roleIds) {
      if (!current.contains(roleId) && !permissionEngineApi.roleExists(roleId)) {
        errors.add(
            UserFieldError.of(
                "roleIds", "user.validation.roleIds.unknown", Map.of("roleId", roleId)));
      }
    }
  }

  private Change applyDisable(Operator operator, String userId) {
    User user = userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
    if (user.getUserId().equals(operator.userId())) {
      throw new UserValidationException(
          UserFieldError.of("userId", "user.validation.self.disable"));
    }
    if (user.getStatus() == UserStatus.DISABLED) {
      return new Change(null, UserResponse.from(user));
    }
    UserSnapshot before = UserSnapshot.from(user);
    user.disable();
    userRepository.saveAndFlush(user);
    UserChangedEvent event =
        UserChangedEvent.of(
            UserChangeOperation.DISABLED,
            user.getUserId(),
            before,
            UserSnapshot.from(user),
            operator.userId());
    return new Change(event, UserResponse.from(user));
  }
}
