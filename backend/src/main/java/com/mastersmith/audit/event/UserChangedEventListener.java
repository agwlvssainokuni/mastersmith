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
import com.mastersmith.usermanagement.event.UserChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * user-managementが発行する{@link UserChangedEvent}を購読し、{@link AuditLogEntry}として記録する(user-management
 * rules.md BR4.9、nfr-design NFR4.3、 code-generation-plan.md 前提事項3・保留10番)。
 *
 * <p>他のリスナー({@link ConfigChangedEventListener}など)と同じ、同期の{@code @EventListener}と、全体の{@code
 * try-catch}による例外遮断 (rules.md BR7.7)。{@code repository.save}のみを行い、トランザクションは開かない: 発行側({@code
 * UserChangedEventPublisher})が、コミット後に、独立した 新しいトランザクション({@code
 * REQUIRES_NEW})の中で発行するため、この書き込みは、そのトランザクションに参加し、発行側が抜けるときにコミットされる。
 *
 * <p>失敗のログには、操作名・userId・操作者・発生日時と、例外の型名だけを出す。例外のメッセージは、氏名・メールアドレス(スナップショットの値)を含みうるため、
 * 出さない(user-management NFR2.6)。
 */
@Component
public class UserChangedEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(UserChangedEventListener.class);

  private final AuditLogEventMapper mapper;
  private final AuditLogEntryRepository repository;

  public UserChangedEventListener(AuditLogEventMapper mapper, AuditLogEntryRepository repository) {
    this.mapper = mapper;
    this.repository = repository;
  }

  @EventListener
  public void onUserChangedEvent(UserChangedEvent event) {
    try {
      AuditLogEntry entry = mapper.fromUserChangedEvent(event);
      repository.save(entry);
    } catch (Exception e) {
      // BR7.7: 記録失敗はERRORログのみとし、発行元(user-management)へは一切影響を与えない(例外を再送出しない)。
      LOG.error(
          "Failed to record audit log entry for UserChangedEvent: operation={}, userId={},"
              + " actor={}, occurredAt={}, cause={}",
          event.operation(),
          event.targetId(),
          event.actor(),
          event.occurredAt(),
          e.getClass().getName());
    }
  }
}
