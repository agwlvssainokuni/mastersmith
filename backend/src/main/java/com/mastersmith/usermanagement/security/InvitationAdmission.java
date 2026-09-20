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

package com.mastersmith.usermanagement.security;

import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.exception.InvitationCapacityExceededException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 招待の同時実行数の許可(reliability-design.md NFR4.2、scalability-design.md
 * NFR3.4)。SMTP応答待ちの間、内部設定DBの接続を保持する招待を、同時に 最大5件(既定)に限る。取得は待たない({@code tryAcquire})。満杯なら、{@link
 * InvitationCapacityExceededException}を投げる(503)。
 *
 * <p>許可は、プロセスごとの上限である。取得は、同一emailの排他({@link EmailLockRegistry})の後に行い、コミットが終わってから返す。
 */
@Component
public class InvitationAdmission {

  /** 取得済みの許可。{@link #close()}で返す(冪等)。 */
  public final class Permit implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();

    private Permit() {}

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        semaphore.release();
      }
    }
  }

  private final Semaphore semaphore;

  @Autowired
  public InvitationAdmission(UserManagementProperties properties) {
    this(properties.invitation().maxConcurrent());
  }

  public InvitationAdmission(int maxConcurrent) {
    this.semaphore = new Semaphore(maxConcurrent);
  }

  /**
   * 許可を、待たずに取得する。
   *
   * @throws InvitationCapacityExceededException 満杯の場合
   */
  public Permit acquire() {
    if (!semaphore.tryAcquire()) {
      throw new InvitationCapacityExceededException();
    }
    return new Permit();
  }

  /** 現在取得できる許可数(テスト・診断用)。 */
  public int availablePermits() {
    return semaphore.availablePermits();
  }
}
