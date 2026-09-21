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

package com.mastersmith.configio.service;

import com.mastersmith.common.configio.PostCommit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 取り込みのトランザクションに、<b>1つだけ</b>登録する{@link TransactionSynchronization}(config-import-export
 * nfr-design/reliability-design.md NFR4.2、レビュー指摘R-02)。 Springは、{@code
 * afterCommit}の同期を登録順に呼び、途中の同期が例外を投げると、後続を呼ばないため、3ユニットが、それぞれ登録する方式は採らない。
 *
 * <p>{@code afterCommit}では、次の順序で、<b>固定して</b>実行する。各動作は、それぞれ独立に{@code
 * try-catch}で包み、例外は、ERRORのログに出して、次へ進む(1つの動作の例外が、他のユニットの動作を 妨げない)。
 *
 * <ol>
 *   <li>3ユニットの{@code invalidateCaches}(config-engine・menu-navigation・permission-engineの順)。
 *   <li>3ユニットの{@code publishEvents}(同じ順)。
 *   <li>取り込みの成功の監査イベント。
 * </ol>
 *
 * <p>無効化を、イベントより先に、すべて行うのは、イベントのリスナー(audit-loggingなど)が、キャッシュを読む場合に、古い内容を読まないようにするためである。ロールバックでは、{@code
 * afterCommit}は呼ばれない (キャッシュは有効なまま。DBも変わっていないため、食い違わない)。
 */
public final class PostCommitCoordinator implements TransactionSynchronization {

  private static final Logger LOG = LoggerFactory.getLogger(PostCommitCoordinator.class);

  private static final List<String> UNIT_NAMES =
      List.of("config-engine", "menu-navigation", "permission-engine");

  private final List<PostCommit> postCommits;
  private final Runnable successAuditEvent;

  public PostCommitCoordinator(List<PostCommit> postCommits, Runnable successAuditEvent) {
    this.postCommits = List.copyOf(postCommits);
    this.successAuditEvent = successAuditEvent;
  }

  /** 現在のトランザクションに、1つだけ、登録する。 */
  public static void register(List<PostCommit> postCommits, Runnable successAuditEvent) {
    TransactionSynchronizationManager.registerSynchronization(
        new PostCommitCoordinator(postCommits, successAuditEvent));
  }

  @Override
  public void afterCommit() {
    for (int i = 0; i < postCommits.size(); i++) {
      run("invalidateCaches", unitName(i), postCommits.get(i).invalidateCaches());
    }
    for (int i = 0; i < postCommits.size(); i++) {
      run("publishEvents", unitName(i), postCommits.get(i).publishEvents());
    }
    run("successAuditEvent", "config-import-export", successAuditEvent);
  }

  private static String unitName(int index) {
    return index < UNIT_NAMES.size() ? UNIT_NAMES.get(index) : "unit-" + index;
  }

  private static void run(String action, String unit, Runnable runnable) {
    try {
      runnable.run();
    } catch (RuntimeException e) {
      // 内容(設定の値・名前)は、出さない。動作・ユニット・例外の型だけ。
      LOG.error(
          "event=config.import.post-commit-failed action={} unit={} cause={}",
          action,
          unit,
          e.getClass().getName());
    }
  }
}
