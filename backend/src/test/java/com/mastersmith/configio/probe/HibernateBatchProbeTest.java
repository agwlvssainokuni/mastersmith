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

import com.mastersmith.MastersmithApplication;
import com.mastersmith.permission.entity.Role;
import com.mastersmith.permission.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事前の確認(code-generation-plan.md Step 1(c))。内部設定DBのエンティティの主キーが、アプリケーションで採番するUUID(生成方式は{@code
 * IDENTITY}ではない)であり、 {@code hibernate.jdbc.batch_size}を設定すれば挿入のバッチが効くことと、Spring Data JPAの{@code
 * save}(採番済みのIDを持つ新規エンティティは{@code merge}になり、 挿入の前に1件ずつSELECTが走る)では、バッチの効果が得られず、{@link
 * EntityManager#persist}を使うべきことを、準備済みステートメントの数(バッチが効くと、1回のバッチで 1つのステートメントを再利用する)で確認する。
 */
@SpringBootTest(
    classes = MastersmithApplication.class,
    properties = {
      "spring.jpa.properties.hibernate.jdbc.batch_size=50",
      "spring.jpa.properties.hibernate.order_inserts=true",
      "spring.jpa.properties.hibernate.order_updates=true",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
class HibernateBatchProbeTest {

  private static final int ROWS = 200;

  @PersistenceContext private EntityManager entityManager;
  @Autowired private RoleRepository roleRepository;

  private List<Role> newRoles() {
    List<Role> roles = new ArrayList<>();
    String tag = UUID.randomUUID().toString();
    for (int i = 0; i < ROWS; i++) {
      roles.add(new Role("probe-" + tag + "-" + i));
    }
    return roles;
  }

  private Statistics statistics() {
    return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
  }

  @Test
  @Transactional
  void persistOfAssignedIdEntitiesIsBatched() {
    Statistics stats = statistics();
    stats.clear();
    newRoles().forEach(entityManager::persist);
    entityManager.flush();

    long prepared = stats.getPrepareStatementCount();
    System.out.println("BATCH_PROBE persist prepareStatementCount=" + prepared + " rows=" + ROWS);
    assertThat(stats.getEntityInsertCount()).isEqualTo(ROWS);
    // バッチが効けば、200件の挿入の準備済みステートメントは、少数(バッチの数程度)にとどまる。
    assertThat(prepared).isLessThan(ROWS / 4);
  }

  @Test
  @Transactional
  void springDataSaveOfAssignedIdEntitiesSelectsBeforeInsert() {
    Statistics stats = statistics();
    stats.clear();
    newRoles().forEach(roleRepository::save);
    entityManager.flush();

    long prepared = stats.getPrepareStatementCount();
    System.out.println("BATCH_PROBE save prepareStatementCount=" + prepared + " rows=" + ROWS);
    // 採番済みのIDを持つ新規エンティティのsaveは、mergeとなり、1件ずつSELECTが走る(準備済みステートメントが、件数に近づく)。
    assertThat(prepared).isGreaterThanOrEqualTo(ROWS / 4);
  }
}
