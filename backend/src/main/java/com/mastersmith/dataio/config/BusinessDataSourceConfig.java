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

package com.mastersmith.dataio.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 業務データ用RDBMS(PostgreSQL/MySQL/MariaDB)への接続設定(team.md「内部設定DB(新規決定)」: アプリ自身の設定を保持する内部設定DBとは別接続)。
 *
 * <p>data-import-export(U8)がCSVエクスポート・インポート時にJDBCで直接アクセスするための{@link DataSource}・{@link
 * PlatformTransactionManager}を、{@code mastersmith.business-datasource.*}
 * プロパティから構築する。実際の接続先RDBMSが未確定の開発環境では {@code
 * mastersmith.business-datasource.enabled=false}(既定)のままとし、本設定クラスの
 * Bean群を一切生成しないことで、業務データ用RDBMSに依存しない他ユニットの起動に影響を与えない (application.yml参照)。
 */
@Configuration
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class BusinessDataSourceConfig {

  /**
   * {@code
   * mastersmith.business-datasource.*}(url/driver-class-name/username/password)から構築するDataSource。
   */
  @Bean(name = "businessDataSource")
  @ConfigurationProperties(prefix = "mastersmith.business-datasource")
  public DataSource businessDataSource() {
    return DataSourceBuilder.create().build();
  }

  /**
   * 業務データ用RDBMSに対する更新系操作(CsvImportServiceのINSERT/UPDATE一括コミット、BR8.7)専用の
   * トランザクションマネージャ。内部設定DB用のJPA既定トランザクションマネージャとは別のDataSourceを 対象とするため、明示的に分離する。
   */
  @Bean(name = "businessTransactionManager")
  public PlatformTransactionManager businessTransactionManager(
      @Qualifier("businessDataSource") DataSource businessDataSource) {
    return new DataSourceTransactionManager(businessDataSource);
  }

  /**
   * 業務データ用RDBMSへのSQL実行に用いる{@link JdbcTemplate}。{@link
   * org.springframework.jdbc.datasource.DataSourceUtils}経由でコネクションを取得するため、 {@code
   * businessTransactionManager}が開始したトランザクションへ自動的に参加する。
   */
  @Bean(name = "businessJdbcTemplate")
  public JdbcTemplate businessJdbcTemplate(
      @Qualifier("businessDataSource") DataSource businessDataSource) {
    return new JdbcTemplate(businessDataSource);
  }
}
