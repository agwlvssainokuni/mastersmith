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

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 時刻をずらせる{@link MutableClock}を、{@code
 * Clock}として供給するテスト用の設定(NFR4.6)。ロックの自動解除・有効期限・再送の猶予の境界を、実時間の経過を待たずに確認する。 本番の{@code
 * Clock}(UTC)より{@code @Primary}で優先する。
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthTestClockConfig {

  @Bean
  @Primary
  public MutableClock testMutableClock() {
    return new MutableClock();
  }
}
