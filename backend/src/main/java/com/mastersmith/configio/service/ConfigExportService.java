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

package com.mastersmith.configio.service;

import com.mastersmith.common.security.Operator;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.mapper.ConfigDocumentMapper;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.permission.PermissionEngineApi;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 設定一式のエクスポート(FR11.1、BR9.1〜BR9.5)。読み取り専用・{@code
 * REPEATABLE_READ}の、1つのトランザクションの中で、3つのユニット(config-engine・menu-navigation・permission-engine)の
 * エクスポート用メソッドを、schema→menu→rbacの順に呼ぶ(NFR4.4)。3ユニットは、キャッシュを介さず、内部設定DBから直接読むため、同じ時点のスナップショットになり、書き出しの途中で取り込み・メニューの変更が確定しても、
 * ファイルの中のセクション間の参照(メニューの遷移先・権限の対象・FK参照)が食い違わない。エクスポートは、内部設定DBを変更せず、監査イベントを発行しない(BR9.16)。
 *
 * <p>認可(401・403)は、呼び出し元({@code
 * ConfigImportExportController})が、このサービスを呼ぶ前に行う。設定ファイルの内容・ファイル名は、ログに出さない(NFR5.2)。
 */
@Service
public class ConfigExportService {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigExportService.class);

  /** 書き出したアプリケーションのバージョンが、得られない場合の値(ビルド情報がない開発環境など)。 */
  static final String UNKNOWN_APP_VERSION = "unknown";

  private final ConfigEngineApi configEngineApi;
  private final MenuStructureApi menuStructureApi;
  private final PermissionEngineApi permissionEngineApi;
  private final ConfigDocumentMapper mapper;
  private final TransactionOperations readTransaction;
  private final Clock clock;
  private final String appVersion;

  @Autowired
  public ConfigExportService(
      ConfigEngineApi configEngineApi,
      MenuStructureApi menuStructureApi,
      PermissionEngineApi permissionEngineApi,
      ConfigDocumentMapper mapper,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      ObjectProvider<Clock> clock,
      ObjectProvider<BuildProperties> buildProperties) {
    this(
        configEngineApi,
        menuStructureApi,
        permissionEngineApi,
        mapper,
        readOnlyRepeatableRead(transactionManager),
        clock.getIfAvailable(Clock::systemUTC),
        buildProperties.getIfAvailable() == null
            ? UNKNOWN_APP_VERSION
            : buildProperties.getObject().getVersion());
  }

  /** トランザクション・時計・バージョンを指定するコンストラクター(テスト用)。 */
  public ConfigExportService(
      ConfigEngineApi configEngineApi,
      MenuStructureApi menuStructureApi,
      PermissionEngineApi permissionEngineApi,
      ConfigDocumentMapper mapper,
      TransactionOperations readTransaction,
      Clock clock,
      String appVersion) {
    this.configEngineApi = configEngineApi;
    this.menuStructureApi = menuStructureApi;
    this.permissionEngineApi = permissionEngineApi;
    this.mapper = mapper;
    this.readTransaction = readTransaction;
    this.clock = clock;
    this.appVersion = appVersion == null ? UNKNOWN_APP_VERSION : appVersion;
  }

  private static TransactionOperations readOnlyRepeatableRead(
      PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setReadOnly(true);
    return template;
  }

  /** 設定一式を、1つの時点として整合するように読み取り、設定ファイルの値オブジェクトにして返す。 */
  public ConfigDocument export(Operator operator) {
    long started = System.nanoTime();
    LOG.info("event=config.export.start operatorUserId={}", operator.userId());
    try {
      ConfigDocument document =
          readTransaction.execute(
              status -> {
                var config = configEngineApi.getExportableConfigSet();
                var menu = menuStructureApi.getExportableMenuStructure();
                var rbac = permissionEngineApi.exportRbac();
                return mapper.toDocument(config, menu, rbac, clock.instant(), appVersion);
              });
      LOG.info(
          "event=config.export.end operatorUserId={} outcome=SUCCESS durationMs={}",
          operator.userId(),
          elapsedMillis(started));
      return document;
    } catch (RuntimeException e) {
      LOG.error(
          "event=config.export.error operatorUserId={} cause={} durationMs={}",
          operator.userId(),
          e.getClass().getName(),
          elapsedMillis(started));
      throw e;
    }
  }

  private static long elapsedMillis(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }
}
