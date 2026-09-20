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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.dto.UpdateUserRequest;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.Operator;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import com.mastersmith.usermanagement.testsupport.EventRecorder;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同一Userへの同時更新で、beforeValueが直前の確定した値になること(行ロックによる直列化、reliability-design.md
 * NFR4.1、NFR8.2)。コミットを伴う複数スレッドの 検証のため、テストはトランザクションの外で実行し、作成したUserは後始末で削除する。
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserApplicationServiceConcurrencyTest {

  private static final long AWAIT_SECONDS = 30;

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    preferenceRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void concurrentUpdatesOfTheSameUserAreSerializedSoEachBeforeValueIsThePreviousCommittedValue()
      throws Exception {
    PermissionEngineApi permissionEngineApi = mock(PermissionEngineApi.class);
    when(permissionEngineApi.canAccessScreen(anyString(), anyString())).thenReturn(true);
    when(permissionEngineApi.roleExists(anyString())).thenReturn(true);
    EventRecorder events = new EventRecorder();
    UserApplicationService service =
        new UserApplicationService(
            userRepository,
            transactionManager,
            new UserAuthorizer(permissionEngineApi),
            permissionEngineApi,
            events.publisher(),
            new SimpleMeterRegistry());
    User target =
        userRepository.saveAndFlush(
            UserTestFactory.activeUser(UserTestFactory.uniqueEmail(), List.of("role-a")));
    String original = target.getName();

    CountDownLatch start = new CountDownLatch(1);
    Future<?> first = executor.submit(() -> updateAfter(start, service, target.getUserId(), "更新A"));
    Future<?> second =
        executor.submit(() -> updateAfter(start, service, target.getUserId(), "更新B"));
    start.countDown();
    first.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    second.get(AWAIT_SECONDS, TimeUnit.SECONDS);

    List<UserChangedEvent> published = events.events();
    assertThat(published).hasSize(2);
    UserChangedEvent fromOriginal =
        published.stream()
            .filter(e -> e.beforeValue().name().equals(original))
            .findFirst()
            .orElseThrow();
    UserChangedEvent fromFirstCommit =
        published.stream().filter(e -> e != fromOriginal).findFirst().orElseThrow();
    // 後から確定した更新のbeforeValueは、先に確定した更新のafterValueと一致する(行ロックによる直列化)。
    assertThat(fromFirstCommit.beforeValue().name()).isEqualTo(fromOriginal.afterValue().name());
    assertThat(userRepository.findById(target.getUserId()).orElseThrow().getName())
        .isEqualTo(fromFirstCommit.afterValue().name());
  }

  private void updateAfter(
      CountDownLatch start, UserApplicationService service, String userId, String newName) {
    try {
      start.await(AWAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    service.update(
        new Operator("admin-user", "admin-role"),
        userId,
        new UpdateUserRequest(newName, List.of("role-a")));
  }
}
