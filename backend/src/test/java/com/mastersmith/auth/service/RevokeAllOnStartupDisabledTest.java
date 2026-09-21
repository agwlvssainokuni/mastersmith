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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** {@code revoke-all-on-startup}が既定(false)の場合は、起動時に、Sessionを削除しないこと(対照)。 */
@SpringBootTest(
    classes = MastersmithApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StartupSessionProbeConfig.class)
class RevokeAllOnStartupDisabledTest {

  @Autowired private Probe probe;
  @Autowired private SessionRepository repository;

  @Test
  void theSessionsSurviveTheStartupByDefault() {
    assertThat(probe.seeded.get()).isEqualTo(3);
    assertThat(probe.sessionsWhenTheWebServerStarted.get()).isEqualTo(3);
    assertThat(repository.count()).isEqualTo(3);
  }
}
