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

package com.mastersmith.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 他ユニットが発行するドメインイベントを購読して生成する、追記専用(append-only)の監査記録1件(entities.md AuditLogEntry)。
 *
 * <p>UPDATE/DELETEに相当する操作用のsetterは一切公開しない(project.md Mandated「監査ログは改ざん・削除ができないようにする」、rules.md
 * BR7.5)。全属性はコンストラクタでのみ確定し、以降変更できない。
 *
 * <p>{@code beforeValue}/{@code afterValue}は{@link SqlTypes#JSON}型として永続化する(entities.md「object」型)。
 * 本Boltが購読する3イベント(ConfigChangedEvent・PermissionChangedEvent・ImportExecutedEvent)は
 * いずれも構造化された変更前後の値を運ばないため、現時点では常に{@code null}となる(entities.md「Assumptions &amp; Open
 * Questions」、rules.md BR7.7参照)。
 */
@Entity
@Table(
    name = "audit_log_entry",
    indexes = {
      @Index(name = "idx_audit_log_entry_occurred_at", columnList = "occurred_at"),
      @Index(
          name = "idx_audit_log_entry_target_type_occurred_at",
          columnList = "target_type, occurred_at")
    })
public class AuditLogEntry {

  @Id
  @Column(name = "audit_log_entry_id", nullable = false, updatable = false, length = 36)
  private String auditLogEntryId;

  /**
   * 操作を行った利用者のユーザーID。ImportExecutedEvent由来のエントリのみ非null(BR7.4)。
   * ConfigChangedEvent・PermissionChangedEvent由来のエントリはnull(Q4=B、BR7.2・BR7.3)。
   */
  @Column(name = "actor_user_id", updatable = false)
  private String actorUserId;

  /**
   * actorUserIdの意味(ユーザーID)に一致しない元の値(システム識別子・activeRoleId等)をそのまま保持する退避先(entities.md「Domain
   * Designからの逸脱」)。ImportExecutedEvent由来のエントリはnull(actorUserIdが真のユーザーIDであるため)。
   */
  @Column(name = "actor_raw", updatable = false)
  private String actorRaw;

  @NotBlank
  @Column(name = "target_type", nullable = false, updatable = false)
  private String targetType;

  @NotBlank
  @Column(name = "target_id", nullable = false, updatable = false)
  private String targetId;

  @NotBlank
  @Column(name = "operation_type", nullable = false, updatable = false)
  private String operationType;

  @NotNull
  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  /** 変更前スナップショット。現時点では常にnull(BR7.7、entities.md参照)。 */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_value", updatable = false)
  private Map<String, Object> beforeValue;

  /** 変更後スナップショット。beforeValueと同様の理由で常にnull(BR7.7、entities.md参照)。 */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_value", updatable = false)
  private Map<String, Object> afterValue;

  protected AuditLogEntry() {
    // JPA用
  }

  public AuditLogEntry(
      String actorUserId,
      String actorRaw,
      String targetType,
      String targetId,
      String operationType,
      Instant occurredAt,
      Map<String, Object> beforeValue,
      Map<String, Object> afterValue) {
    this(
        UUID.randomUUID().toString(),
        actorUserId,
        actorRaw,
        targetType,
        targetId,
        operationType,
        occurredAt,
        beforeValue,
        afterValue);
  }

  public AuditLogEntry(
      String auditLogEntryId,
      String actorUserId,
      String actorRaw,
      String targetType,
      String targetId,
      String operationType,
      Instant occurredAt,
      Map<String, Object> beforeValue,
      Map<String, Object> afterValue) {
    this.auditLogEntryId = auditLogEntryId;
    this.actorUserId = actorUserId;
    this.actorRaw = actorRaw;
    this.targetType = targetType;
    this.targetId = targetId;
    this.operationType = operationType;
    this.occurredAt = occurredAt;
    this.beforeValue = beforeValue;
    this.afterValue = afterValue;
  }

  public String getAuditLogEntryId() {
    return auditLogEntryId;
  }

  public String getActorUserId() {
    return actorUserId;
  }

  public String getActorRaw() {
    return actorRaw;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getOperationType() {
    return operationType;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Map<String, Object> getBeforeValue() {
    return beforeValue;
  }

  public Map<String, Object> getAfterValue() {
    return afterValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AuditLogEntry other)) {
      return false;
    }
    return Objects.equals(auditLogEntryId, other.auditLogEntryId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(auditLogEntryId);
  }

  @Override
  public String toString() {
    return "AuditLogEntry{auditLogEntryId='%s', targetType='%s', targetId='%s', operationType='%s', occurredAt=%s}"
        .formatted(auditLogEntryId, targetType, targetId, operationType, occurredAt);
  }
}
