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

package com.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.StartupSessionProbeConfig;
import com.mastersmith.auth.testsupport.StartupSessionProbeConfig.Probe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 起動時の全Sessionの失効({@code mastersmith.auth.session.revoke-all-on-startup=true}、NFR2.5・NFR4.7)のテスト:
 * 起動時に、すべてのSessionが削除されること、その削除が、
 * <b>Webサーバーがリクエストを受け付ける前</b>に終わっていること(Webサーバーの起動の時点で、Sessionが0件)。Sessionは、Beanの初期化の時点で作る ({@link
 * StartupSessionProbeConfig})。
 */
@SpringBootTest(
    classes = MastersmithApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "mastersmith.auth.session.revoke-all-on-startup=true")
@Import(StartupSessionProbeConfig.class)
class RevokeAllOnStartupTest {

  @Autowired private Probe probe;
  @Autowired private SessionRepository repository;
  @Autowired private SessionStartupRevoker revoker;

  @Test
  void everySessionIsDeletedAtStartupBeforeTheWebServerAcceptsRequests() {
    // Sessionは、起動の途中で作られた(3件)。
    assertThat(probe.seeded.get()).isEqualTo(3);
    // Webサーバーが起動した時点で、すでに0件(実行のタイミングは、Webサーバーの起動より前)。
    assertThat(probe.sessionsWhenTheWebServerStarted.get()).isZero();
    assertThat(repository.count()).isZero();
  }

  @Test
  void theRevokerRunsAsASmartInitializingSingletonNotAsAnApplicationRunner() {
    // ApplicationRunnerは、Webサーバーの起動の後に実行され、その間に作られたSessionを削除しうるため、使わない。
    assertThat(revoker).isInstanceOf(SmartInitializingSingleton.class);
    assertThat(revoker).isNotInstanceOf(org.springframework.boot.ApplicationRunner.class);
  }
}
