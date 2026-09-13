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

package com.mastersmith.config.store;

import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.validation.ConfigValidator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * アプリ起動時にConfigCacheへ全設定を読み込み、BR1.1〜BR1.4のfail-fast検証を実行する (functional-spec.md W1, NFR4.1)。
 *
 * <p>検証に失敗した場合は{@link com.mastersmith.config.exception.ConfigValidationException}を 伝播させ、Spring
 * Bootのコンテキスト起動そのものを失敗させる(project.md Mandated: 設定定義自体の誤りは起動時に検知しfail fastする)。
 */
@Component
@Order(0)
public class ConfigEngineStartupRunner implements ApplicationRunner {

  private final ConfigCache cache;
  private final ConfigValidator configValidator;

  public ConfigEngineStartupRunner(ConfigCache cache, ConfigValidator configValidator) {
    this.cache = cache;
    this.configValidator = configValidator;
  }

  @Override
  public void run(ApplicationArguments args) {
    cache.reload();
    configValidator.validateAll(cache.allTableConfigs(), cache.allColumnConfigs());
  }
}
