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
import java.util.List;
import org.springframework.data.domain.Page;

/** {@code GET /api/audit-log}のレスポンス全体(C6契約: {@code { items, totalCount }})。 */
public record AuditLogPageResponse(List<AuditLogEntryView> items, long totalCount) {

  public static AuditLogPageResponse from(Page<AuditLogEntry> page) {
    return new AuditLogPageResponse(
        page.getContent().stream().map(AuditLogEntryView::from).toList(), page.getTotalElements());
  }
}
