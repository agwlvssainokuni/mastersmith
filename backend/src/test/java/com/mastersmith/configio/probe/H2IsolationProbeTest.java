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

package com.mastersmith.configio.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.MastersmithApplication;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 事前の確認(code-generation-plan.md Step 1(b))。H2 2.4.240の{@code
 * REPEATABLE_READ}が、(1)別のトランザクションの確定を、トランザクションの途中で
 * 読み直さない(スナップショット)こと、(2)別のトランザクションの反映の途中(未確定)の状態を読まないこと、(3)スナップショットの後に別のトランザクションが確定した
 * 同じ行を書き込もうとすると更新の競合になり、Springでは{@link
 * ConcurrencyFailureException}に変換されること、(4)行が重ならない書き込みは、両方が確定することを、実際の
 * 組込みH2・実際のトランザクションで確認する(reliability-design.md NFR4.1・NFR4.4・NFR4.6)。
 *
 * <p>専用の表(テストごとに一意の名前)を、テストの内部で作って捨てる(他のテストと衝突しない)。
 */
@SpringBootTest(classes = MastersmithApplication.class)
class H2IsolationProbeTest {

  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("transactionManager")
  private PlatformTransactionManager transactionManager;

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private String table;

  @BeforeEach
  void createTable() {
    table = "probe_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, v INT NOT NULL)");
    jdbc.update("INSERT INTO " + table + " (id, v) VALUES (1, 0)");
    jdbc.update("INSERT INTO " + table + " (id, v) VALUES (2, 0)");
  }

  @AfterEach
  void dropTable() {
    executor.shutdownNow();
    jdbc.execute("DROP TABLE IF EXISTS " + table);
  }

  private TransactionTemplate template(int isolation) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(isolation);
    return template;
  }

  private int read(int id) {
    return jdbc.queryForObject("SELECT v FROM " + table + " WHERE id = ?", Integer.class, id);
  }

  /** 別のスレッド(別のトランザクション)で、指定の処理を実行して、確定まで待つ。 */
  private void inOtherTransaction(Runnable action) throws Exception {
    Future<?> future =
        executor.submit(
            () ->
                template(TransactionDefinition.ISOLATION_READ_COMMITTED)
                    .executeWithoutResult(s -> action.run()));
    future.get(10, TimeUnit.SECONDS);
  }

  @Test
  void repeatableReadDoesNotSeeLaterCommits() throws Exception {
    List<Integer> reads =
        template(TransactionDefinition.ISOLATION_REPEATABLE_READ)
            .execute(
                status -> {
                  int first = read(1);
                  try {
                    inOtherTransaction(
                        () -> jdbc.update("UPDATE " + table + " SET v = 1 WHERE id = 1"));
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                  return List.of(first, read(1));
                });

    assertThat(reads).containsExactly(0, 0);
    assertThat(read(1)).isEqualTo(1);
  }

  @Test
  void readCommittedSeesLaterCommitsForContrast() throws Exception {
    List<Integer> reads =
        template(TransactionDefinition.ISOLATION_READ_COMMITTED)
            .execute(
                status -> {
                  int first = read(1);
                  try {
                    inOtherTransaction(
                        () -> jdbc.update("UPDATE " + table + " SET v = 1 WHERE id = 1"));
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                  return List.of(first, read(1));
                });

    assertThat(reads).containsExactly(0, 1);
  }

  @Test
  void repeatableReadNeverSeesAHalfAppliedStateOfAnotherTransaction() throws Exception {
    CountDownLatch firstRowWritten = new CountDownLatch(1);
    CountDownLatch readerFinishedFirstRead = new CountDownLatch(1);
    CountDownLatch writerMayCommit = new CountDownLatch(1);
    Future<?> writer =
        executor.submit(
            () ->
                template(TransactionDefinition.ISOLATION_READ_COMMITTED)
                    .executeWithoutResult(
                        status -> {
                          jdbc.update("UPDATE " + table + " SET v = 1 WHERE id = 1");
                          firstRowWritten.countDown();
                          await(readerFinishedFirstRead);
                          jdbc.update("UPDATE " + table + " SET v = 1 WHERE id = 2");
                          writerMayCommit.countDown();
                        }));

    await(firstRowWritten);
    List<Integer> snapshot =
        template(TransactionDefinition.ISOLATION_REPEATABLE_READ)
            .execute(
                status -> {
                  int a = read(1);
                  readerFinishedFirstRead.countDown();
                  await(writerMayCommit);
                  // 書き込み側のトランザクションが、2行目の書き込みまで終えた(確定の直前か直後)後に読む。
                  int b = read(2);
                  return List.of(a, b);
                });
    writer.get(10, TimeUnit.SECONDS);

    // 1行目の読み取りの時点のスナップショットで、2行目も読まれる。新旧の混在(0,1)や(1,0)は見えない。
    assertThat(snapshot).containsExactly(0, 0);
    assertThat(read(1)).isEqualTo(1);
    assertThat(read(2)).isEqualTo(1);
  }

  @Test
  void writingARowChangedAfterTheSnapshotFailsAsAConcurrencyFailure() throws Exception {
    Throwable failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            Throwable.class,
            () ->
                template(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .executeWithoutResult(
                        status -> {
                          read(1);
                          try {
                            inOtherTransaction(
                                () -> jdbc.update("UPDATE " + table + " SET v = 5 WHERE id = 1"));
                          } catch (Exception e) {
                            throw new IllegalStateException(e);
                          }
                          jdbc.update("UPDATE " + table + " SET v = 9 WHERE id = 1");
                        }));

    // 観測値(H2 2.4.240): 更新の競合は、SQLState 40001の例外になり、Springでは、ConcurrencyFailureExceptionの系統に変換される。
    System.out.println("H2_PROBE conflict exception=" + failure.getClass().getName());
    assertThat(failure).isInstanceOf(ConcurrencyFailureException.class);
    assertThat(read(1)).isEqualTo(5);
  }

  @Test
  void writingDifferentRowsAfterTheSnapshotBothCommit() throws Exception {
    template(TransactionDefinition.ISOLATION_REPEATABLE_READ)
        .executeWithoutResult(
            status -> {
              read(1);
              try {
                inOtherTransaction(
                    () -> jdbc.update("UPDATE " + table + " SET v = 5 WHERE id = 1"));
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
              jdbc.update("UPDATE " + table + " SET v = 7 WHERE id = 2");
            });

    assertThat(read(1)).isEqualTo(5);
    assertThat(read(2)).isEqualTo(7);
  }

  @Test
  void rowsInsertedAfterTheSnapshotAreNotVisible() throws Exception {
    int count =
        template(TransactionDefinition.ISOLATION_REPEATABLE_READ)
            .execute(
                status -> {
                  jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                  try {
                    inOtherTransaction(
                        () -> jdbc.update("INSERT INTO " + table + " (id, v) VALUES (3, 3)"));
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                  return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                });

    assertThat(count).isEqualTo(2);
    assertThatThrownBy(
            () -> jdbc.queryForObject("SELECT v FROM " + table + " WHERE id = 99", Integer.class))
        .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
