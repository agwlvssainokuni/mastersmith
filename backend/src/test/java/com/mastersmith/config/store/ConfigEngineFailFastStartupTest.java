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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.repository.TableConfigRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * アプリ起動時のfail-fast検証(functional-spec.md W1, NFR4.1)を、実際のSpring Bootコンテキスト 起動を通じて確認する統合テスト。
 *
 * <p>不正な設定データ(必須プロパティ欠落)を含むテスト用DBで起動し、アプリケーションコンテキスト
 * 起動そのものが失敗することを検証する。{@code @SpringBootTest}は単一の(正常に起動する) コンテキストを前提とするため用いず、2フェーズで{@link
 * SpringApplicationBuilder}を用いて コンテキストを明示的に構築・破棄する(1: 正常起動して不正な行を直接永続化、2: 同一DBへ 再起動して起動失敗を確認)。
 */
class ConfigEngineFailFastStartupTest {

  @Test
  void applicationContextFailsToStartWhenAPersistedTableConfigViolatesRequiredProperties() {
    String jdbcUrl = "jdbc:h2:mem:failfast-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    // フェーズ1: 正常に起動してスキーマを作成し、schemaName欠落のTableConfigを直接永続化する
    // (ConfigValidatorを経由しない、DB内の既存不正データを模したシード)。
    try (ConfigurableApplicationContext bootstrapContext =
        new SpringApplicationBuilder(MastersmithApplication.class)
            .run("--spring.datasource.url=" + jdbcUrl, "--spring.jpa.hibernate.ddl-auto=create")) {
      TableConfigRepository repository = bootstrapContext.getBean(TableConfigRepository.class);
      repository.save(new TableConfig(null, "orphan_table"));
    }

    // フェーズ2: 同一DBへ再起動する。ConfigEngineStartupRunnerが不正な既存データを検知し、
    // ConfigValidationExceptionがコンテキスト起動を失敗させる。
    // (コマンドライン引数形式で渡すことで、application.yml/application-test.ymlより
    // 優先度の高いプロパティソースとしてdatasource.urlを確実に上書きする。
    // SpringApplicationBuilder#propertiesはdefaultProperties=最低優先度として
    // 登録されるため、application.ymlの値を上書きできない。)
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(MastersmithApplication.class)
                    .run(
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.jpa.hibernate.ddl-auto=none"))
        .satisfies(ex -> assertThat(findConfigValidationException(ex)).isNotNull());
  }

  private static ConfigValidationException findConfigValidationException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof ConfigValidationException cve) {
        return cve;
      }
      current = current.getCause();
    }
    return null;
  }
}
