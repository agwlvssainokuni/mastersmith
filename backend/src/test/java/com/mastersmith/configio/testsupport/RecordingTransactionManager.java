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

package com.mastersmith.configio.testsupport;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * トランザクションの開始・確定・ロールバック(と、分離レベル・伝播・読み取り専用)を記録する、テスト用のトランザクションマネージャー(実際のDBには触れない)。トランザクション同期({@code
 * afterCommit}など)は、 Springの標準のとおり動く。{@code afterCommit}の順序・ロールバック・分離レベル・確定時の失敗を、実DBなしで確認するために用いる。
 */
public class RecordingTransactionManager extends AbstractPlatformTransactionManager {

  private static final long serialVersionUID = 1L;

  /** 記録(開始・確定・ロールバック)。順序どおり。 */
  public final transient List<String> log = new ArrayList<>();

  /** trueなら、確定(doCommit)で例外を投げる(コミット時の失敗の再現)。 */
  public volatile boolean failOnCommit;

  private static final class Tx {
    boolean active;
  }

  private final transient ThreadLocal<Tx> current = ThreadLocal.withInitial(Tx::new);

  @Override
  protected Object doGetTransaction() {
    return current.get();
  }

  @Override
  protected boolean isExistingTransaction(Object transaction) {
    return ((Tx) transaction).active;
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    ((Tx) transaction).active = true;
    synchronized (log) {
      log.add(
          "begin isolation=%d readOnly=%s propagation=%d"
              .formatted(
                  definition.getIsolationLevel(),
                  definition.isReadOnly(),
                  definition.getPropagationBehavior()));
    }
  }

  @Override
  protected Object doSuspend(Object transaction) {
    Tx tx = (Tx) transaction;
    boolean was = tx.active;
    tx.active = false;
    synchronized (log) {
      log.add("suspend");
    }
    return was;
  }

  @Override
  protected void doResume(Object transaction, Object suspendedResources) {
    ((Tx) transaction).active = (Boolean) suspendedResources;
    synchronized (log) {
      log.add("resume");
    }
  }

  @Override
  protected void doCommit(DefaultTransactionStatus status) {
    if (failOnCommit) {
      throw new TransactionException("commit failed (test)") {
        private static final long serialVersionUID = 1L;
      };
    }
    ((Tx) status.getTransaction()).active = false;
    synchronized (log) {
      log.add("commit");
    }
  }

  @Override
  protected void doRollback(DefaultTransactionStatus status) {
    ((Tx) status.getTransaction()).active = false;
    synchronized (log) {
      log.add("rollback");
    }
  }

  /** 記録の、指定の接頭辞で始まる行の数。 */
  public long count(String prefix) {
    synchronized (log) {
      return log.stream().filter(l -> l.startsWith(prefix)).count();
    }
  }
}
