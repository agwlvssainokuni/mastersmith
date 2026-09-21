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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.UserAccountLookupApi;
import com.mastersmith.usermanagement.entity.UserStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * {@link UserAccountClient}・{@link AuthExceptionTranslator}のテスト(reliability-design.md NFR4.2):
 * C11の呼び出しでの内部設定DBの障害が{@link AuthStorageUnavailableException}(503)に変換されること、{@link
 * HashCapacityExceededException}がそのまま伝わること、バグ・制約違反は変換しないこと、結果が素通しされること。
 */
class UserAccountClientTest {

  private final UserAccountLookupApi api = mock(UserAccountLookupApi.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final UserAccountClient client =
      new UserAccountClient(
          api, new AuthExceptionTranslator(new AuthMetrics(registry), new AuthEventLogger()));

  record Call(String name, Consumer<UserAccountClient> invoke) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Call> calls() {
    return Stream.of(
        new Call("findByEmail", c -> c.findByEmail("a@example.test")),
        new Call("findByUserId", c -> c.findByUserId("u")),
        new Call("verifyPasswordHash", c -> c.verifyPasswordHash("u", "pw")),
        new Call("dummyVerify", c -> c.dummyVerify("pw")),
        new Call("isDisabled", c -> c.isDisabled("u")));
  }

  private void stubEveryMethodToThrow(RuntimeException exception) {
    when(api.findByEmail(org.mockito.ArgumentMatchers.anyString())).thenThrow(exception);
    when(api.findByUserId(org.mockito.ArgumentMatchers.anyString())).thenThrow(exception);
    when(api.verifyPasswordHash(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenThrow(exception);
    org.mockito.Mockito.doThrow(exception)
        .when(api)
        .dummyVerify(org.mockito.ArgumentMatchers.anyString());
    when(api.isDisabled(org.mockito.ArgumentMatchers.anyString())).thenThrow(exception);
  }

  static Stream<Arguments> storageFailures() {
    return Stream.of(
        Arguments.of(new DataAccessResourceFailureException("connection failed")),
        Arguments.of(new CannotCreateTransactionException("no connection")),
        Arguments.of(new QueryTimeoutException("timeout")),
        Arguments.of(new CannotAcquireLockException("lock wait timeout")),
        Arguments.of(new TransactionTimedOutException("tx timeout")),
        // 原因の連鎖の中にある場合も、変換する。
        Arguments.of(new IllegalStateException("wrapper", new QueryTimeoutException("timeout"))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("storageFailures")
  void everyStorageFailureFromEveryMethodBecomesAStorageUnavailableException(
      RuntimeException failure) {
    stubEveryMethodToThrow(failure);

    calls()
        .forEach(
            call ->
                assertThatThrownBy(() -> call.invoke().accept(client))
                    .as(call.name())
                    .isInstanceOf(AuthStorageUnavailableException.class)
                    .hasCause(failure));
  }

  @Test
  void theCountOfStorageFailuresIsRecordedInTheMetric() {
    stubEveryMethodToThrow(new QueryTimeoutException("timeout"));

    assertThatThrownBy(() -> client.isDisabled("u"))
        .isInstanceOf(AuthStorageUnavailableException.class);
    assertThatThrownBy(() -> client.findByUserId("u"))
        .isInstanceOf(AuthStorageUnavailableException.class);

    assertThat(registry.get("auth.db.unavailable").counter().count()).isEqualTo(2.0);
  }

  @Test
  void aHashCapacityExceededExceptionPropagatesAsIsWithoutConversion() {
    HashCapacityExceededException exceeded = new HashCapacityExceededException("capacity");
    stubEveryMethodToThrow(exceeded);

    calls()
        .forEach(
            call ->
                assertThatThrownBy(() -> call.invoke().accept(client))
                    .as(call.name())
                    .isSameAs(exceeded));
    assertThat(registry.find("auth.db.unavailable").counter().count()).isZero();
  }

  @Test
  void bugsAndConstraintViolationsAreNotConvertedToAStorageFailure() {
    RuntimeException violation = new DataIntegrityViolationException("duplicate");
    stubEveryMethodToThrow(violation);

    calls()
        .forEach(
            call ->
                assertThatThrownBy(() -> call.invoke().accept(client))
                    .as(call.name())
                    .isSameAs(violation));
  }

  @Test
  void resultsPassThrough() {
    UserAccount account = new UserAccount("u", null, UserStatus.ACTIVE, List.of("r1"));
    when(api.findByEmail("a@example.test")).thenReturn(Optional.of(account));
    when(api.findByUserId("u")).thenReturn(Optional.of(account));
    when(api.verifyPasswordHash("u", "pw")).thenReturn(true);
    when(api.isDisabled("u")).thenReturn(true);

    assertThat(client.findByEmail("a@example.test")).contains(account);
    assertThat(client.findByUserId("u")).contains(account);
    assertThat(client.verifyPasswordHash("u", "pw")).isTrue();
    assertThat(client.isDisabled("u")).isTrue();
    client.dummyVerify("pw");
    org.mockito.Mockito.verify(api).dummyVerify("pw");
  }
}
