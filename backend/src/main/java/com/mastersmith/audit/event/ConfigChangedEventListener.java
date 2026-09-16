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

package com.mastersmith.audit.event;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.config.event.ConfigChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * config-engineが発行する{@link ConfigChangedEvent}を購読し、{@link AuditLogEntry}として記録する(BR7.1・BR7.2)。
 *
 * <p>同期の{@code @EventListener}とし、マッピングからDB書き込みまでの全体を{@code
 * try-catch}で囲むことで、いかなる例外も発行元(config-engine)へ伝播させない(reliability-design.md「イベントリスナーの例外遮断設計」、NFR4.2・NFR4.5、rules.md
 * BR7.7)。{@code @Async}は導入しない(同期実行のシンプルさを優先する設計判断)。
 */
@Component
public class ConfigChangedEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigChangedEventListener.class);

  private final AuditLogEventMapper mapper;
  private final AuditLogEntryRepository repository;

  public ConfigChangedEventListener(
      AuditLogEventMapper mapper, AuditLogEntryRepository repository) {
    this.mapper = mapper;
    this.repository = repository;
  }

  @EventListener
  public void onConfigChangedEvent(ConfigChangedEvent event) {
    try {
      AuditLogEntry entry = mapper.fromConfigChangedEvent(event);
      repository.save(entry);
    } catch (Exception e) {
      // BR7.7: 記録失敗はERRORログのみとし、発行元へは一切影響を与えない(例外を再送出しない)。
      // target/actor/occurredAtは機微情報を含まないためログへそのまま含めてよい(security-design.md NFR2.3確認済み)。
      LOG.error(
          "Failed to record audit log entry for ConfigChangedEvent: operation={}, target={},"
              + " actor={}, occurredAt={}",
          event.operation(),
          event.target(),
          event.actor(),
          event.occurredAt(),
          e);
    }
  }
}
