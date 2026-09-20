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

package com.mastersmith.usermanagement.mail;

import com.mastersmith.usermanagement.config.UserManagementProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 招待メール送信の専用のスレッドプール(performance-design.md NFR1.4)。スレッド数は設定値(既定5)、待ち行列は持たない。満杯のとき(打ち切り後の
 * 送信スレッドが残っている場合など)は、投入時に{@link RejectedExecutionException}を投げる。
 */
@Component
public class InvitationMailExecutor {

  private final ThreadPoolExecutor executor;

  @Autowired
  public InvitationMailExecutor(UserManagementProperties properties) {
    this(properties.invitation().mailPoolSize());
  }

  public InvitationMailExecutor(int poolSize) {
    AtomicInteger sequence = new AtomicInteger();
    ThreadFactory threadFactory =
        runnable -> {
          Thread thread = new Thread(runnable, "invitation-mail-" + sequence.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        };
    this.executor =
        new ThreadPoolExecutor(
            poolSize,
            poolSize,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy());
  }

  /**
   * タスクを投入する。
   *
   * @throws RejectedExecutionException プールが満杯の場合
   */
  public Future<?> submit(Runnable task) {
    return executor.submit(task);
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }
}
