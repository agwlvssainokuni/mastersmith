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

package com.mastersmith.auth.testsupport;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.usermanagement.UserAccountLookupApi;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * authentication-serviceの、実際のH2・実際のトランザクション・実際の部品の結線での、サービス層・同時実行・統合のテストの基底(実H2)。C11({@link
 * UserAccountLookupApi})だけを、モックする(ロック・Session・キャッシュに関する同時実行・統合のテストでも、C11はモックし、内部設定DB(H2)は実物を用いる。
 * unit-test-instructions.md)。
 *
 * <p>同じ設定({@code @MockitoBean}と{@code @Import})を共有することで、Springのテストコンテキスト(と、そのH2)を、テストクラスの間で再利用する。時刻は、{@link
 * MutableClock}で、実時間の経過を待たずに進める。各テストは、UUIDを含む一意のuserIdを用い、コミットを伴うため、後始末は不要(他のテストと衝突しない)。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@Import(AuthTestClockConfig.class)
public abstract class AuthIntegrationTestBase {

  /** テストの時刻の起点。 */
  protected static final java.time.Instant BASE_TIME =
      java.time.Instant.parse("2026-01-01T00:00:00Z");

  @MockitoBean protected UserAccountLookupApi userAccountLookupApi;

  @Autowired protected MutableClock clock;

  @BeforeEach
  void resetTheClock() {
    clock.set(BASE_TIME);
  }
}
