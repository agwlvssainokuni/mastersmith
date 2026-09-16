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
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import com.mastersmith.permission.event.PermissionChangedEvent;
import org.springframework.stereotype.Component;

/**
 * 3種類のドメインイベント(ConfigChangedEvent・PermissionChangedEvent・ImportExecutedEvent)を{@link
 * AuditLogEntry}へ変換する(functional-design/rules.md BR7.2〜BR7.4)。
 *
 * <p>本コンポーネントは特定業務固有のテーブル名・カラム名・業務ルールをハードコードせず、購読したイベントクラスの型・フィールド値からのみ
 * 汎用的にマッピングする(BR7.11、共通エンジン層の制約)。beforeValue/afterValueは現行3イベントがいずれも構造化された変更前後の値を 運ばないため常に{@code
 * null}とする(entities.md「Assumptions &amp; Open Questions」)。
 */
@Component
public class AuditLogEventMapper {

  /** BR7.2: ConfigChangedEvent → AuditLogEntry。 */
  public AuditLogEntry fromConfigChangedEvent(ConfigChangedEvent event) {
    return new AuditLogEntry(
        null,
        event.actor(),
        "ConfigEngine",
        event.target(),
        event.operation().name(),
        event.occurredAt(),
        null,
        null);
  }

  /**
   * BR7.3: PermissionChangedEvent →
   * AuditLogEntry。scopeType/scopeRefはマッピングしない(targetIdはtargetRoleIdのみ)。
   */
  public AuditLogEntry fromPermissionChangedEvent(PermissionChangedEvent event) {
    return new AuditLogEntry(
        null,
        event.actor(),
        "PermissionEngine",
        event.targetRoleId(),
        "PERMISSION_CHANGED",
        event.occurredAt(),
        null,
        null);
  }

  /** BR7.4: ImportExecutedEvent → AuditLogEntry。このイベントのみactorUserIdへ実際のユーザーIDが入る。 */
  public AuditLogEntry fromImportExecutedEvent(ImportExecutedEvent event) {
    return new AuditLogEntry(
        event.actor(),
        null,
        "DataImportExport",
        event.tableConfigId(),
        event.committed() ? "IMPORT_COMMITTED" : "IMPORT_ROLLED_BACK",
        event.occurredAt(),
        null,
        null);
  }
}
