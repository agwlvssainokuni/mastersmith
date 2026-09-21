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

import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

/**
 * 内部設定DBの障害を表す例外(接続の取得の失敗・クエリ・トランザクションのタイムアウト・ロックの待機の超過など、一時的・接続の障害)を、{@link
 * AuthStorageUnavailableException}に変換する(reliability-design.md NFR4.2)。
 *
 * <p>変換の対象: {@link DataAccessResourceFailureException}・{@link
 * CannotCreateTransactionException}・{@link QueryTimeoutException}・{@link
 * TransientDataAccessException}(ペシミスティックなロックの失敗を含む)・{@link
 * TransactionTimedOutException}。バグや制約違反({@code
 * DataIntegrityViolationException}など)は対象外で、そのまま伝える(500として扱う)。 変換した件数は、{@code
 * auth.db.unavailable}に記録する。例外のメッセージには、SQL・接続情報を含めない。
 */
@Component
public class AuthExceptionTranslator {

  private final AuthMetrics metrics;
  private final AuthEventLogger logger;

  public AuthExceptionTranslator(AuthMetrics metrics, AuthEventLogger logger) {
    this.metrics = metrics;
    this.logger = logger;
  }

  /** 内部設定DBの障害を表す例外か(原因の連鎖も含める)。 */
  public static boolean isStorageFailure(Throwable throwable) {
    Throwable cause = throwable;
    for (int depth = 0; cause != null && depth < 10; depth++) {
      if (cause instanceof DataAccessResourceFailureException
          || cause instanceof CannotCreateTransactionException
          || cause instanceof QueryTimeoutException
          || cause instanceof TransientDataAccessException
          || cause instanceof TransactionTimedOutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /** 処理を実行し、内部設定DBの障害を{@link AuthStorageUnavailableException}に変換する。 */
  public <T> T translate(Supplier<T> action) {
    try {
      return action.get();
    } catch (AuthStorageUnavailableException e) {
      throw e;
    } catch (RuntimeException e) {
      if (isStorageFailure(e)) {
        throw unavailable(e);
      }
      throw e;
    }
  }

  /** 戻り値のない処理を実行し、内部設定DBの障害を{@link AuthStorageUnavailableException}に変換する。 */
  public void translate(Runnable action) {
    translate(
        () -> {
          action.run();
          return null;
        });
  }

  /** 内部設定DBの障害として、503の例外を作る(メトリクス・ログを記録する)。 */
  public AuthStorageUnavailableException unavailable(Throwable cause) {
    metrics.dbUnavailable();
    logger.dbUnavailable(cause);
    return new AuthStorageUnavailableException("Authentication storage is unavailable", cause);
  }
}
