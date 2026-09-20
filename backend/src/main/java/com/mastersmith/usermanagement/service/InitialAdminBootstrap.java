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

import com.mastersmith.usermanagement.config.InitialAdminProperties;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserPreference;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserChangedEventPublisher;
import com.mastersmith.usermanagement.event.UserSnapshot;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 起動時の、初期管理者アカウントの自動作成(W5、rules.md BR4.7、reliability-design.md NFR4.4)。
 *
 * <p>対応するUserが(statusを問わず)存在しない場合に限り、status=activeの管理者を作成する(冪等)。パスワードのハッシュ計算は、存在を先に確認してから、
 * トランザクションの外側で行い、そのあと、User・UserPreference(既定値)の作成を、1つのトランザクションで行う(資源の取得順序の不変条件)。並行して複数の
 * プロセスが起動して、emailの一意制約違反になった場合は、「既に存在した」として、何もしなかったこととする。作成後、コミット後に
 * UserChangedEvent(BOOTSTRAPPED、actorは{@code system})を発行する。
 *
 * <p>起動時にハッシュ計算の許可を取れない場合(待機2秒を超えた場合を含む)は、503ではなく、起動失敗とする(例外を握りつぶさない)。ログには、
 * 「作成した」「既に存在したため何もしなかった」の事実だけを記録し、emailもパスワードも含めない。
 */
@Component
public class InitialAdminBootstrap implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(InitialAdminBootstrap.class);

  private final InitialAdminProperties properties;
  private final UserRepository userRepository;
  private final UserPreferenceRepository preferenceRepository;
  private final TransactionTemplate transaction;
  private final PasswordHasher passwordHasher;
  private final UserChangedEventPublisher eventPublisher;

  public InitialAdminBootstrap(
      InitialAdminProperties properties,
      UserRepository userRepository,
      UserPreferenceRepository preferenceRepository,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      PasswordHasher passwordHasher,
      UserChangedEventPublisher eventPublisher) {
    this.properties = properties;
    this.userRepository = userRepository;
    this.preferenceRepository = preferenceRepository;
    this.transaction = new TransactionTemplate(transactionManager);
    this.passwordHasher = passwordHasher;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void run(ApplicationArguments args) {
    bootstrap();
  }

  /** 初期管理者を作成する。作成した場合はtrue、すでに存在して何もしなかった場合はfalse。 */
  boolean bootstrap() {
    if (userRepository.findByEmail(properties.email()).isPresent()) {
      LOG.info("Initial administrator already exists; nothing to do");
      return false;
    }
    String passwordHash = passwordHasher.hash(properties.password());
    UserChangedEvent event;
    try {
      event = transaction.execute(status -> create(passwordHash));
    } catch (DataIntegrityViolationException e) {
      LOG.info("Initial administrator already exists; nothing to do");
      return false;
    }
    eventPublisher.publish(event);
    LOG.info("Initial administrator created");
    return true;
  }

  private UserChangedEvent create(String passwordHash) {
    User admin =
        userRepository.saveAndFlush(
            User.activeAdmin(
                properties.name(), properties.email(), passwordHash, properties.roleIds()));
    preferenceRepository.saveAndFlush(UserPreference.withDefaults(admin.getUserId()));
    return UserChangedEvent.of(
        UserChangeOperation.BOOTSTRAPPED,
        admin.getUserId(),
        null,
        UserSnapshot.from(admin),
        UserChangedEvent.SYSTEM_ACTOR);
  }
}
