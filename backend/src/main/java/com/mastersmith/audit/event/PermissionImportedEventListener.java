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
import com.mastersmith.permission.event.PermissionImportedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * permission-engineが、取り込み1回につき1件発行する{@link PermissionImportedEvent}(サマリ、rules.md BR3.11)を購読し、{@link
 * AuditLogEntry}として記録する。{@link ConfigChangedEventListener}と同一の例外遮断パターン(同期{@code @EventListener} +
 * 全体try-catch。BR7.7)。
 */
@Component
public class PermissionImportedEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionImportedEventListener.class);

  private final AuditLogEventMapper mapper;
  private final AuditLogEntryRepository repository;

  public PermissionImportedEventListener(
      AuditLogEventMapper mapper, AuditLogEntryRepository repository) {
    this.mapper = mapper;
    this.repository = repository;
  }

  @EventListener
  public void onPermissionImportedEvent(PermissionImportedEvent event) {
    try {
      repository.save(mapper.fromPermissionImportedEvent(event));
    } catch (Exception e) {
      // BR7.7: 記録失敗はERRORログのみとし、発行元(permission-engine)へは一切影響を与えない。
      LOG.error(
          "Failed to record audit log entry for PermissionImportedEvent: changeCount={}, actor={},"
              + " occurredAt={}, cause={}",
          event.changeCount(),
          event.actor(),
          event.occurredAt(),
          e.getClass().getName());
    }
  }
}
