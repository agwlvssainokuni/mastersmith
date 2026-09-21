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

package com.mastersmith.auth.testsupport;

import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.repository.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 起動時の全Sessionの失効({@code
 * revoke-all-on-startup})の実行のタイミングを確認するための、テスト用の設定。Sessionを、Beanの初期化の時点(すべての{@code
 * SmartInitializingSingleton}の実行より前)で作り、Webサーバーが起動した時点({@link WebServerInitializedEvent})の件数を記録する。
 */
@TestConfiguration(proxyBeanMethods = false)
public class StartupSessionProbeConfig {

  /** 記録した値。 */
  public static class Probe {
    public final AtomicLong seeded = new AtomicLong(-1);
    public final AtomicLong sessionsWhenTheWebServerStarted = new AtomicLong(-1);
  }

  @Bean
  public Probe startupSessionProbe() {
    return new Probe();
  }

  /** Beanの初期化の時点(Flywayの移行の後、すべてのSmartInitializingSingletonより前)で、Sessionを3件作る。 */
  @Bean
  public InitializingBean startupSessionSeeder(
      SessionRepository repository, PlatformTransactionManager transactionManager, Probe probe) {
    return () -> {
      Instant now = Instant.now();
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                for (int i = 0; i < 3; i++) {
                  repository.save(
                      Session.issue(
                          AuthTestFactory.newSessionId(),
                          AuthTestFactory.uniqueUserId(),
                          null,
                          AuthTestFactory.newHash(),
                          now,
                          now.plus(Duration.ofMinutes(30))));
                }
              });
      probe.seeded.set(repository.count());
    };
  }

  /** Webサーバーが起動した時点(リクエストを受け付ける状態)の、Sessionの件数を記録する。 */
  @Bean
  public ApplicationListener<WebServerInitializedEvent> webServerStartedRecorder(
      SessionRepository repository, Probe probe) {
    return event -> probe.sessionsWhenTheWebServerStarted.set(repository.count());
  }
}
