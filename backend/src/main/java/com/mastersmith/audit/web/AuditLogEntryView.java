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

package com.mastersmith.audit.web;

import com.mastersmith.audit.entity.AuditLogEntry;
import java.time.Instant;
import java.util.Map;

/**
 * {@code GET /api/audit-log}のレスポンス要素(C6契約 AuditLogEntryスキーマ)。
 *
 * <p>{@code actorRaw}はC6契約に未追補のため、現時点ではレスポンスに含めない(functional-spec.md「Assumptions &amp; Open
 * Questions」)。
 */
public record AuditLogEntryView(
    String auditLogEntryId,
    String actorUserId,
    String targetType,
    String targetId,
    String operationType,
    Instant occurredAt,
    Map<String, Object> beforeValue,
    Map<String, Object> afterValue) {

  public static AuditLogEntryView from(AuditLogEntry entry) {
    return new AuditLogEntryView(
        entry.getAuditLogEntryId(),
        entry.getActorUserId(),
        entry.getTargetType(),
        entry.getTargetId(),
        entry.getOperationType(),
        entry.getOccurredAt(),
        entry.getBeforeValue(),
        entry.getAfterValue());
  }
}
