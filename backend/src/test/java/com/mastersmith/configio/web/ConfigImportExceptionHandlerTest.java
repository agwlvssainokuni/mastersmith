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

package com.mastersmith.configio.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.common.cache.CacheReloadUnavailableException;
import com.mastersmith.configio.exception.ConfigImportExceptions;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

/** {@link ConfigImportExportController}の保存名の規則(BR9.2)の単体テスト。 */
class ConfigImportExceptionHandlerTest {

  @Test
  void theFileNameRemovesTheSeparatorsOfTheIsoTimestamp() {
    assertThat(ConfigImportExportController.fileName("2026-09-21T01:02:03Z"))
        .isEqualTo("mastersmith-config-20260921T010203Z.json");
  }

  @Test
  void theHandlerCodesAreTheAgreedI18nKeys() {
    assertThat(ConfigImportExceptionHandler.CODE_UNAUTHORIZED).isEqualTo("auth.token.invalid");
    assertThat(ConfigImportExceptionHandler.CODE_FORBIDDEN).isEqualTo("config.import.forbidden");
    assertThat(ConfigImportExceptionHandler.CODE_VALIDATION_FAILED)
        .isEqualTo("config.import.validation.failed");
    assertThat(ConfigImportExceptionHandler.CODE_UNAVAILABLE)
        .isEqualTo("config.import.unavailable");
    assertThat(ConfigImportExceptionHandler.CODE_INTERNAL_ERROR)
        .isEqualTo("config.import.internal-error");
  }

  @Test
  void rawJpaLockFailuresAreTranslatedAndMappedTo503() {
    assertThat(ConfigImportExceptions.translate(new PessimisticLockException("x")))
        .isInstanceOf(ConcurrencyFailureException.class);
    assertThat(ConfigImportExceptions.isServiceUnavailable(new PessimisticLockException("x")))
        .isTrue();
    assertThat(ConfigImportExceptions.isServiceUnavailable(new LockTimeoutException("x"))).isTrue();
  }

  @Test
  void resourceTimeoutAndTransactionFailuresAre503WhileOthersAre500() {
    assertThat(ConfigImportExceptions.isServiceUnavailable(new QueryTimeoutException("x")))
        .isTrue();
    assertThat(
            ConfigImportExceptions.isServiceUnavailable(new CannotCreateTransactionException("x")))
        .isTrue();
    assertThat(
            ConfigImportExceptions.isServiceUnavailable(new CacheReloadUnavailableException("x")))
        .isTrue();
    assertThat(
            ConfigImportExceptions.isServiceUnavailable(new DataIntegrityViolationException("x")))
        .isFalse();
    assertThat(ConfigImportExceptions.isServiceUnavailable(new IllegalStateException("x")))
        .isFalse();
    assertThat(ConfigImportExceptions.isServiceUnavailable(new PersistenceException("x")))
        .isFalse();
  }

  @Test
  void nonPersistenceExceptionsAreLeftAsTheyAre() {
    IllegalStateException e = new IllegalStateException("x");

    assertThat(ConfigImportExceptions.translate(e)).isSameAs(e);
  }
}
