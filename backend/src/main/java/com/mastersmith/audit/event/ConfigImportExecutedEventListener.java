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
import com.mastersmith.configio.event.ConfigImportExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * config-import-exportが発行する{@link ConfigImportExecutedEvent}を購読し、{@link
 * AuditLogEntry}として記録する(BR7.1、config-import-export BR9.16)。
 *
 * <p>{@link ConfigChangedEventListener}と同一の例外遮断パターン(同期{@code @EventListener} +
 * 全体try-catch)を適用する(BR7.7)。{@code repository.save}のみを行い、トランザクションは開かない: 発行側({@code
 * ConfigImportEventPublisher})が、独立した新しいトランザクション({@code
 * REQUIRES_NEW})の中で発行するため、この書き込みは、そのトランザクションに参加し、発行側が抜けるときにコミットされる。
 *
 * <p>失敗のログには、結果・失敗の分類・操作者・発生日時と、例外の型名だけを出す(ファイルの内容・例外のメッセージは出さない)。
 */
@Component
public class ConfigImportExecutedEventListener {

  private static final Logger LOG =
      LoggerFactory.getLogger(ConfigImportExecutedEventListener.class);

  private final AuditLogEventMapper mapper;
  private final AuditLogEntryRepository repository;

  public ConfigImportExecutedEventListener(
      AuditLogEventMapper mapper, AuditLogEntryRepository repository) {
    this.mapper = mapper;
    this.repository = repository;
  }

  @EventListener
  public void onConfigImportExecutedEvent(ConfigImportExecutedEvent event) {
    try {
      AuditLogEntry entry = mapper.fromConfigImportExecutedEvent(event);
      repository.save(entry);
    } catch (Exception e) {
      // BR7.7: 記録失敗はERRORログのみとし、発行元(config-import-export)へは一切影響を与えない(例外を再送出しない)。
      LOG.error(
          "Failed to record audit log entry for ConfigImportExecutedEvent: outcome={},"
              + " failureCategory={}, actor={}, occurredAt={}, cause={}",
          event.outcome(),
          event.failureCategory(),
          event.actorUserId(),
          event.occurredAt(),
          e.getClass().getName());
    }
  }
}
