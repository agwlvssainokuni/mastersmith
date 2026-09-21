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

package com.mastersmith.common.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@link TestOperatorContext}を、{@link
 * OperatorContext}のBeanとして供給するテスト用の設定。{@code @WebMvcTest}(実物のC15の実装がないスライス)でも、
 * {@code @SpringBootTest}(実物の{@code
 * SecurityContextOperatorContext}があるコンテキスト)でも、{@code @Primary}で、こちらが使われる。
 *
 * <p>使い方: テストクラスに{@code @Import(TestOperatorContextConfig.class)}を付け、{@code @Autowired
 * TestOperatorContext operators}で受け取る。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestOperatorContextConfig {

  @Bean
  @Primary
  public TestOperatorContext testOperatorContext() {
    return new TestOperatorContext();
  }
}
