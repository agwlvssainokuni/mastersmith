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

package com.mastersmith.configio.exception;

import jakarta.persistence.PersistenceException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.TransactionException;

/**
 * 例外の翻訳と分類(config-import-export nfr-design/reliability-design.md NFR4.3「例外と応答の対応」、レビュー指摘R-15)。
 *
 * <p>取り込みの反映の中の{@code flush()}(各ユニットが、{@code EntityManager}を直接用いる)は、Spring Data
 * JPAのリポジトリ({@code @Repository}の例外の翻訳を受ける)と異なり、素のJPAの例外 ({@code PessimisticLockException}・{@code
 * LockTimeoutException}など。H2の更新の競合・ロック待ちのタイムアウト)を投げる。{@link
 * #translate}が、SpringのDataAccessExceptionの系統に翻訳し、 {@link
 * #isServiceUnavailable}が、503にする例外(競合・タイムアウト・資源の障害・一時的な障害・トランザクションの障害)かを判定する。それ以外の想定外の例外は、500。
 */
public final class ConfigImportExceptions {

  private ConfigImportExceptions() {}

  /** 素のJPAの例外は、SpringのDataAccessExceptionに翻訳する(できなければ、そのまま)。 */
  public static RuntimeException translate(RuntimeException e) {
    if (e instanceof PersistenceException) {
      RuntimeException translated =
          EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(e);
      if (translated != null) {
        return translated;
      }
    }
    return e;
  }

  /** 503(内部設定DBの障害・接続の取得の失敗・更新の競合・ロック待ちのタイムアウト)にする例外か。 */
  public static boolean isServiceUnavailable(RuntimeException e) {
    RuntimeException translated = translate(e);
    return translated instanceof ConcurrencyFailureException
        || translated instanceof QueryTimeoutException
        || translated instanceof DataAccessResourceFailureException
        || translated instanceof TransientDataAccessException
        || translated instanceof TransactionException;
  }
}
