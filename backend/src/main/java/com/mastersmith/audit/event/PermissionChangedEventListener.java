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
import com.mastersmith.permission.event.PermissionChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * permission-engineが発行する{@link PermissionChangedEvent}を購読し、{@link
 * AuditLogEntry}として記録する(BR7.1・BR7.3)。
 *
 * <p>{@link ConfigChangedEventListener}と同一の例外遮断パターン(同期{@code @EventListener} +
 * 全体try-catch)を適用する(reliability-design.md、NFR4.2・NFR4.5、rules.md BR7.7)。
 */
@Component
public class PermissionChangedEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionChangedEventListener.class);

  private final AuditLogEventMapper mapper;
  private final AuditLogEntryRepository repository;

  public PermissionChangedEventListener(
      AuditLogEventMapper mapper, AuditLogEntryRepository repository) {
    this.mapper = mapper;
    this.repository = repository;
  }

  @EventListener
  public void onPermissionChangedEvent(PermissionChangedEvent event) {
    try {
      AuditLogEntry entry = mapper.fromPermissionChangedEvent(event);
      repository.save(entry);
    } catch (Exception e) {
      // BR7.7: 記録失敗はERRORログのみとし、発行元(permission-engine)へは一切影響を与えない。
      LOG.error(
          "Failed to record audit log entry for PermissionChangedEvent: targetRoleId={},"
              + " scopeType={}, scopeRef={}, actor={}, occurredAt={}",
          event.targetRoleId(),
          event.scopeType(),
          event.scopeRef(),
          event.actor(),
          event.occurredAt(),
          e);
    }
  }
}
