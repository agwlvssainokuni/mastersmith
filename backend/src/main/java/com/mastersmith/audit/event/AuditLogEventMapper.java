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
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.configio.event.ConfigImportExecutedEvent;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import com.mastersmith.permission.event.PermissionChangedEvent;
import com.mastersmith.permission.event.PermissionImportedEvent;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
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

  /** 設定の取り込みの監査ログの対象の種別(config-import-export BR9.16)。 */
  static final String CONFIG_IMPORT_TARGET_TYPE = "ConfigImportExport";

  /** 設定の取り込みの監査ログの対象のID(予約のscreenKey)。 */
  static final String CONFIG_IMPORT_TARGET_ID = "config-import-export";

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

  /**
   * config-import-exportのConfigImportExecutedEvent → AuditLogEntry(config-import-export
   * BR9.16)。{@code targetType}は {@code ConfigImportExport}、{@code targetId}は予約のscreenKey({@code
   * config-import-export})、{@code operationType}は {@code CONFIG_IMPORT_SUCCEEDED}・{@code
   * CONFIG_IMPORT_FAILED}。操作者は{@code actorUserId}へ(真のユーザーID)。{@code
   * afterValue}には、結果・操作者のアクティブロール・(成功)
   * セクションごとの件数・(失敗)失敗の分類と誤りの総数だけを持つ。<b>ファイルの内容(設定の値・名前)は、記録しない</b>(監査ログは、無期限に保持されるため)。
   */
  public AuditLogEntry fromConfigImportExecutedEvent(ConfigImportExecutedEvent event) {
    Map<String, Object> afterValue = new LinkedHashMap<>();
    afterValue.put("outcome", event.outcome().name());
    if (event.actorRoleId() != null) {
      afterValue.put("activeRoleId", event.actorRoleId());
    }
    if (event.failureCategory() != null) {
      afterValue.put("failureCategory", event.failureCategory().name());
    }
    if (event.errorCount() != null) {
      afterValue.put("errorCount", event.errorCount());
    }
    if (event.sections() != null) {
      Map<String, Object> sections = new LinkedHashMap<>();
      for (Map.Entry<String, SectionCounts> entry : event.sections().entrySet()) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("added", entry.getValue().added());
        counts.put("updated", entry.getValue().updated());
        counts.put("deleted", entry.getValue().deleted());
        sections.put(entry.getKey(), counts);
      }
      afterValue.put("sections", sections);
    }
    boolean success = event.outcome() == ConfigImportExecutedEvent.Outcome.SUCCESS;
    return new AuditLogEntry(
        event.actorUserId(),
        null,
        CONFIG_IMPORT_TARGET_TYPE,
        CONFIG_IMPORT_TARGET_ID,
        success ? "CONFIG_IMPORT_SUCCEEDED" : "CONFIG_IMPORT_FAILED",
        event.occurredAt(),
        null,
        afterValue);
  }

  /**
   * permission-engineのPermissionImportedEvent(取り込み単位のサマリ、rules.md BR3.11) → AuditLogEntry。{@code
   * actor}は、操作者のactiveRoleIdであり、ユーザーIDではないため、 {@code
   * actorRaw}へ入れる(PermissionChangedEventと同じ)。{@code afterValue}には、変更件数だけを持つ(個々の変更前後の値は含めない)。
   */
  public AuditLogEntry fromPermissionImportedEvent(PermissionImportedEvent event) {
    Map<String, Object> afterValue = new LinkedHashMap<>();
    afterValue.put("changeCount", event.changeCount());
    return new AuditLogEntry(
        null,
        event.actor(),
        "PermissionEngine",
        CONFIG_IMPORT_TARGET_ID,
        "PERMISSION_IMPORTED",
        event.occurredAt(),
        null,
        afterValue);
  }

  /**
   * user-managementのUserChangedEvent → AuditLogEntry(user-management rules.md
   * BR4.9、code-generation-plan.md 前提事項3)。
   *
   * <p>{@code targetType}はUserChangedEventの固定値({@code User})、{@code targetId}はuserId、{@code
   * operationType}は{@code operation}名(INVITED・ACTIVATED・UPDATED・DISABLED・BOOTSTRAPPED)。{@code
   * beforeValue}/{@code afterValue}は、スナップショット({@code name}・ {@code email}・{@code status}・{@code
   * roleIds}のみ。{@code passwordHash}・{@code invitationToken}は、イベントの型が持たない)のMap表現(初回の
   * INVITED・BOOTSTRAPPEDの{@code beforeValue}はnull)。
   *
   * <p>{@code actor}は、INVITED・ACTIVATED・UPDATED・DISABLEDでは操作した利用者のuserIdとして{@code
   * actorUserId}へ、BOOTSTRAPPED(初期管理者の 自動作成)ではシステム識別子{@code system}を、userIdを偽装せず{@code
   * actorRaw}へ入れる。
   */
  public AuditLogEntry fromUserChangedEvent(UserChangedEvent event) {
    boolean systemActor = event.operation() == UserChangeOperation.BOOTSTRAPPED;
    return new AuditLogEntry(
        systemActor ? null : event.actor(),
        systemActor ? event.actor() : null,
        event.targetType(),
        event.targetId(),
        event.operation().name(),
        event.occurredAt(),
        toMap(event.beforeValue()),
        toMap(event.afterValue()));
  }

  private static Map<String, Object> toMap(UserSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", snapshot.name());
    map.put("email", snapshot.email());
    map.put("status", snapshot.status());
    map.put("roleIds", snapshot.roleIds());
    return map;
  }
}
