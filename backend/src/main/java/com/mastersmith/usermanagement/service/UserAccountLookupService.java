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
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.UserAccountLookupApi;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.observation.UserObservations;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.PasswordHasher;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * C11 {@link UserAccountLookupApi}の実装(functional-spec.md W8、rules.md BR4.13、security-design.md
 * NFR2.2)。
 *
 * <p>ハッシュの計算は、{@link PasswordHasher}(許可の中)で行い、DB接続を保持したまま許可を取らない(呼び出し元がトランザクションの外で呼ぶ前提)。
 * ログイン成功時のハッシュ更新は、呼び出し元のトランザクション属性(読み取り専用を含む)に依存しないよう、独立した新しいトランザクション({@code
 * REQUIRES_NEW})で、検証時と同じ{@code passwordHash}の場合に限って更新する。
 */
@Service
public class UserAccountLookupService implements UserAccountLookupApi {

  private static final Logger LOG = LoggerFactory.getLogger(UserAccountLookupService.class);

  private final UserRepository userRepository;
  private final PermissionEngineApi permissionEngineApi;
  private final PasswordHasher passwordHasher;
  private final TransactionTemplate newTransaction;

  /** 観測(スパン)。既定は何もしない。アプリケーションでは、{@link UserObservations}のBeanが注入される(NFR5.3)。 */
  private UserObservations observations = UserObservations.NOOP;

  @Autowired
  public void setObservations(UserObservations observations) {
    this.observations = observations;
  }

  public UserAccountLookupService(
      UserRepository userRepository,
      PermissionEngineApi permissionEngineApi,
      PasswordHasher passwordHasher,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
    this.userRepository = userRepository;
    this.permissionEngineApi = permissionEngineApi;
    this.passwordHasher = passwordHasher;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public Optional<UserAccount> findByEmail(String email) {
    return observations.observe("user.account.find_by_email", () -> doFindByEmail(email));
  }

  private Optional<UserAccount> doFindByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(email.strip().toLowerCase(Locale.ROOT)).map(this::toAccount);
  }

  @Override
  public boolean verifyPasswordHash(String userId, String rawPassword) {
    return observations.observe(
        "user.account.verify_password", () -> doVerifyPasswordHash(userId, rawPassword));
  }

  private boolean doVerifyPasswordHash(String userId, String rawPassword) {
    Optional<User> found = userId == null ? Optional.empty() : userRepository.findById(userId);
    if (found.isEmpty()
        || found.get().getStatus() != UserStatus.ACTIVE
        || found.get().getPasswordHash() == null) {
      return false;
    }
    String storedHash = found.get().getPasswordHash();
    PasswordHasher.VerifyResult result = passwordHasher.verifyAndUpgrade(rawPassword, storedHash);
    if (!result.matches()) {
      return false;
    }
    if (result.upgradedHash() != null) {
      upgradeHash(userId, storedHash, result.upgradedHash());
    }
    return true;
  }

  @Override
  public boolean isDisabled(String userId) {
    return observations.observe("user.account.is_disabled", () -> doIsDisabled(userId));
  }

  private boolean doIsDisabled(String userId) {
    if (userId == null) {
      return true;
    }
    // 最新のstatusを参照する(キャッシュしない)。不存在・disabledはtrue(fail closed)。
    return userRepository
        .findStatusById(userId)
        .map(status -> status == UserStatus.DISABLED)
        .orElse(true);
  }

  private UserAccount toAccount(User user) {
    // 直接付与分とGroup経由分の和集合(ソート済み)。passwordHashは返さない(null)。
    TreeSet<String> roleIds = new TreeSet<>(user.getRoleIds());
    roleIds.addAll(permissionEngineApi.getGroupDerivedRoleIds(user.getUserId()));
    return new UserAccount(
        user.getUserId(), null, user.getStatus(), java.util.List.copyOf(roleIds));
  }

  /** 更新の失敗は、ログインの成否に影響させず、警告ログ(userIdのみ。ハッシュ値・パスワードは含めない)に記録する。イベントは発行しない。 */
  private void upgradeHash(String userId, String expectedHash, String upgradedHash) {
    try {
      newTransaction.executeWithoutResult(
          status ->
              userRepository.updatePasswordHashIfUnchanged(userId, expectedHash, upgradedHash));
    } catch (RuntimeException e) {
      LOG.warn("Password hash upgrade failed: userId={}, cause={}", userId, e.getClass().getName());
    }
  }
}
