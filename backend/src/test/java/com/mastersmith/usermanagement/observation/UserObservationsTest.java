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

package com.mastersmith.usermanagement.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.testsupport.RecordingObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link UserObservations}: 観測の名前・固定の属性・エラーの記録、NOOPでも処理の結果が変わらないこと(observability-design.md NFR5.3)。
 */
class UserObservationsTest {

  private final RecordingObservationHandler handler = new RecordingObservationHandler();
  private final ObservationRegistry registry = ObservationRegistry.create();
  private final UserObservations observations = new UserObservations(registry);

  UserObservationsTest() {
    registry.observationConfig().observationHandler(handler);
  }

  @Test
  void observesTheActionWithTheGivenNameAndOnlyFixedAttributes() {
    String result = observations.observe("user.api.test", () -> "result");

    assertThat(result).isEqualTo("result");
    assertThat(handler.recorded()).hasSize(1);
    assertThat(handler.recorded().get(0).name()).startsWith("user.api.test");
    assertThat(handler.recorded().get(0).keyValues()).containsExactly("unit=user-management");
    assertThat(handler.recorded().get(0).error()).isNull();
  }

  @Test
  void anOperationNameIsAFixedLowCardinalityAttribute() {
    observations.observe("user.password.compute", "hash", () -> 1);

    assertThat(handler.recorded().get(0).keyValues())
        .containsExactlyInAnyOrder("unit=user-management", "operation=hash");
  }

  @Test
  void runObservesActionsWithoutAResult() {
    AtomicInteger executions = new AtomicInteger();

    observations.run("user.mail.test", executions::incrementAndGet);

    assertThat(executions.get()).isEqualTo(1);
    assertThat(handler.named("user.mail.test")).hasSize(1);
  }

  @Test
  void aFailureIsRecordedAsTheErrorOfTheObservationAndRethrown() {
    IllegalStateException failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                observations.observe(
                    "user.api.failing",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    assertThat(handler.recorded()).hasSize(1);
    assertThat(handler.recorded().get(0).error())
        .contains("IllegalStateException")
        .contains("boom");
  }

  @Test
  void theNoopInstanceRunsTheActionWithoutObserving() {
    assertThat(UserObservations.NOOP.observe("user.api.noop", () -> "ok")).isEqualTo("ok");
    assertThatThrownBy(
            () ->
                UserObservations.NOOP.observe(
                    "user.api.noop",
                    () -> {
                      throw new IllegalArgumentException("x");
                    }))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(handler.recorded()).isEmpty();
  }
}
