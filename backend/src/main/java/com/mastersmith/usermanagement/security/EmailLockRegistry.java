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
import com.mastersmith.usermanagement.exception.EmailLockTimeoutException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 正規化後email単位の排他(プロセス内、reliability-design.md NFR4.2、scalability-design.md
 * NFR3.4)。同一emailの招待を直列化し、別のemailどうしは 直列化しない。
 *
 * <p>参照数つきのロックの表({@link ConcurrentHashMap})で、利用者がいなくなった(参照数が0の)エントリは表から取り除く。ストライプ方式(ハッシュで
 * 固定数のロックへ割り当てる)は、別のemailが直列化されるため採らない。取得は、待機の上限(既定12秒)まで待ち、取れなければ{@link
 * EmailLockTimeoutException}を投げる。
 *
 * <p><b>資源の取得順序</b>: 排他 → 許可({@link InvitationAdmission}) →
 * DB接続。ロックは、取得したスレッドで解放すること(try-with-resources)。
 * 複数プロセス構成では、プロセスをまたげない(DBの一意制約違反・ロック待ちタイムアウトが最後の防御になる)。
 */
@Component
public class EmailLockRegistry {

  private static final class Entry {
    final ReentrantLock lock = new ReentrantLock();
    int references;
  }

  /** 取得済みの排他。{@link #close()}で解放する(冪等)。 */
  public final class Held implements AutoCloseable {
    private final String email;
    private final Entry entry;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Held(String email, Entry entry) {
      this.email = email;
      this.entry = entry;
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        entry.lock.unlock();
        releaseReference(email);
      }
    }
  }

  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
  private final Duration waitTimeout;

  @Autowired
  public EmailLockRegistry(UserManagementProperties properties) {
    this(properties.invitation().emailLockWait());
  }

  public EmailLockRegistry(Duration waitTimeout) {
    this.waitTimeout = waitTimeout;
  }

  /**
   * 排他を取得する。
   *
   * @param normalizedEmail 正規化後(trim・小文字)のemail
   * @throws EmailLockTimeoutException 待機の上限内に取れなかった場合(または待機が中断された場合)
   */
  public Held acquire(String normalizedEmail) {
    Entry entry =
        entries.compute(
            normalizedEmail,
            (key, current) -> {
              Entry target = current != null ? current : new Entry();
              target.references++;
              return target;
            });
    boolean locked = false;
    try {
      locked = entry.lock.tryLock(waitTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!locked) {
      releaseReference(normalizedEmail);
      throw new EmailLockTimeoutException();
    }
    return new Held(normalizedEmail, entry);
  }

  private void releaseReference(String email) {
    entries.compute(
        email,
        (key, current) -> {
          if (current == null) {
            return null;
          }
          current.references--;
          return current.references <= 0 ? null : current;
        });
  }

  /** 表に残っているエントリ数(テスト・診断用。利用者がいなければ0)。 */
  public int size() {
    return entries.size();
  }
}
