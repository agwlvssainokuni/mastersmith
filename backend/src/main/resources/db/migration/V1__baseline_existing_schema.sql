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

-- audit-logging(U7)前提修正: プロジェクト全体へのFlyway導入に伴うベースラインマイグレーション。
-- 実装済み4ユニット(config-engine・schema-introspector・permission-engine・data-import-export)が
-- 現在Hibernate `ddl-auto: update` で自動生成しているスキーマを、そのまま過不足なく再現する
-- (code-generation-plan.md「前提修正: Flywayの導入」)。スキーマの実質的な変更は行わない。
--
-- 各テーブルの列定義・制約は、実際に起動して生成されたスキーマを `SCRIPT` コマンドでダンプし、
-- 各エンティティクラス(@Entity/@Table/@Column/@Id/@Embeddable/@UniqueConstraint/@Index)の
-- アノテーションと突き合わせて確認した結果をそのまま反映している。

-- ==========================================================================
-- role (com.mastersmith.permission.entity.Role)
-- ==========================================================================
CREATE TABLE role (
    role_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (role_id),
    CONSTRAINT uk_role_name UNIQUE (name)
);

-- ==========================================================================
-- permission_group (com.mastersmith.permission.entity.Group)
-- ==========================================================================
CREATE TABLE permission_group (
    group_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_permission_group PRIMARY KEY (group_id),
    CONSTRAINT uk_permission_group_name UNIQUE (name)
);

-- ==========================================================================
-- group_role (com.mastersmith.permission.entity.GroupRole / GroupRoleId)
-- ==========================================================================
CREATE TABLE group_role (
    group_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_group_role PRIMARY KEY (group_id, role_id)
);

-- ==========================================================================
-- group_membership (com.mastersmith.permission.entity.GroupMembership / GroupMembershipId)
-- ==========================================================================
CREATE TABLE group_membership (
    group_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_group_membership PRIMARY KEY (group_id, user_id)
);

-- ==========================================================================
-- primary_permission (com.mastersmith.permission.entity.PrimaryPermission)
-- ==========================================================================
CREATE TABLE primary_permission (
    primary_permission_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    scope_ref VARCHAR(255) NOT NULL,
    level ENUM('FULL', 'NONE', 'READ') NOT NULL,
    scope_type ENUM('COLUMN', 'SCHEMA', 'TABLE') NOT NULL,
    CONSTRAINT pk_primary_permission PRIMARY KEY (primary_permission_id),
    CONSTRAINT uk_primary_permission_scope UNIQUE (role_id, scope_type, scope_ref)
);

CREATE INDEX ix_primary_permission_scope ON primary_permission (role_id, scope_type, scope_ref);

-- ==========================================================================
-- auxiliary_permission (com.mastersmith.permission.entity.AuxiliaryPermission)
-- ==========================================================================
CREATE TABLE auxiliary_permission (
    create_allowed BOOLEAN,
    delete_allowed BOOLEAN,
    auxiliary_permission_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    scope_ref VARCHAR(255) NOT NULL,
    scope_type ENUM('COLUMN', 'SCHEMA', 'TABLE') NOT NULL,
    CONSTRAINT pk_auxiliary_permission PRIMARY KEY (auxiliary_permission_id),
    CONSTRAINT uk_auxiliary_permission_scope UNIQUE (role_id, scope_type, scope_ref)
);

CREATE INDEX ix_auxiliary_permission_scope ON auxiliary_permission (role_id, scope_type, scope_ref);

-- ==========================================================================
-- table_config (com.mastersmith.config.entity.TableConfig)
-- ==========================================================================
CREATE TABLE table_config (
    display_order INTEGER NOT NULL,
    table_config_id VARCHAR(36) NOT NULL,
    optimistic_lock_column VARCHAR(255),
    schema_name VARCHAR(255),
    table_name VARCHAR(255),
    CONSTRAINT pk_table_config PRIMARY KEY (table_config_id),
    CONSTRAINT uk_table_config_schema_table UNIQUE (schema_name, table_name)
);

-- ==========================================================================
-- column_config (com.mastersmith.config.entity.ColumnConfig)
-- ==========================================================================
CREATE TABLE column_config (
    display_order INTEGER NOT NULL,
    is_primary_key BOOLEAN NOT NULL,
    column_config_id VARCHAR(36) NOT NULL,
    column_name VARCHAR(255),
    format VARCHAR(255),
    table_config_id VARCHAR(255),
    choice_options JSON,
    editor_type ENUM(
        'CHECKBOX', 'DATE', 'DATETIME', 'DECIMAL', 'INTEGER',
        'RADIO', 'SELECT', 'SWITCH', 'TEXT', 'TEXTAREA'
    ),
    fk_reference JSON,
    validation_rule JSON,
    visibility ENUM('HIDDEN', 'VISIBLE') NOT NULL,
    CONSTRAINT pk_column_config PRIMARY KEY (column_config_id),
    CONSTRAINT uk_column_config_table_column UNIQUE (table_config_id, column_name)
);

-- ==========================================================================
-- translation_entry (com.mastersmith.config.entity.TranslationEntry / TranslationEntryId)
-- ==========================================================================
CREATE TABLE translation_entry (
    i18n_key VARCHAR(255) NOT NULL,
    locale VARCHAR(255) NOT NULL,
    text VARCHAR(255),
    CONSTRAINT pk_translation_entry PRIMARY KEY (i18n_key, locale)
);
