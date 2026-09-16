-- Copyright 2026 agwlvssainokuni
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- audit-logging(U7): AuditLogEntry(entities.md)の永続化先。追記専用(append-only、
-- project.md Mandated・rules.md BR7.5)のため、アプリケーション層にUPDATE/DELETE経路を
-- 一切持たない(AuditLogEntryRepositoryにはsave/find系メソッドのみを定義する)。
--
-- インデックス設計(performance-design.md「インデックス設計」、NFR1.1・NFR1.2対応):
--   - idx_audit_log_entry_occurred_at: targetType未指定時(GET /api/audit-log、BR7.9の
--     既定並び順)を範囲スキャンのみで完結させる。
--   - idx_audit_log_entry_target_type_occurred_at: targetType指定時(等価一致フィルタ、
--     BR7.9)のフィルタ+ソートを複合インデックスのみで満たす。
CREATE TABLE audit_log_entry (
    audit_log_entry_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(255),
    actor_raw VARCHAR(255),
    target_type VARCHAR(255) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    operation_type VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    before_value JSON,
    after_value JSON,
    CONSTRAINT pk_audit_log_entry PRIMARY KEY (audit_log_entry_id)
);

CREATE INDEX idx_audit_log_entry_occurred_at ON audit_log_entry (occurred_at DESC);

CREATE INDEX idx_audit_log_entry_target_type_occurred_at
    ON audit_log_entry (target_type, occurred_at DESC);
