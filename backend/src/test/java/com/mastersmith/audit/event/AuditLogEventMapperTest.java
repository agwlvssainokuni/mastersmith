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

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.config.event.ConfigChangeOperation;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.event.PermissionChangedEvent;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link AuditLogEventMapper}のテーブル駆動テスト(3種類のイベント全てについてfunctional-design/rules.md
 * BR7.2〜BR7.4のマッピング規則を検証する。team.md確定の追加合格条件「イベント種別ごとのマッピング規則をテーブル駆動テストで網羅する」に対応、unit-test-instructions.md参照)。
 */
class AuditLogEventMapperTest {

  private final AuditLogEventMapper mapper = new AuditLogEventMapper();

  private record Case(
      String name,
      Function<AuditLogEventMapper, AuditLogEntry> map,
      String expectedTargetType,
      String expectedTargetId,
      String expectedOperationType,
      String expectedActorUserId,
      String expectedActorRaw,
      Instant expectedOccurredAt) {}

  private static List<Case> cases() {
    Instant configOccurredAt = Instant.parse("2026-09-16T00:00:00Z");
    Instant permissionOccurredAt = Instant.parse("2026-09-16T01:00:00Z");
    Instant importCommittedOccurredAt = Instant.parse("2026-09-16T02:00:00Z");
    Instant importRolledBackOccurredAt = Instant.parse("2026-09-16T03:00:00Z");

    ConfigChangedEvent configChangedEvent =
        new ConfigChangedEvent(
            ConfigChangeOperation.DRAFT_IMPORTED, "shop.products", "system", configOccurredAt);
    PermissionChangedEvent permissionChangedEvent =
        new PermissionChangedEvent(
            "role-1", ScopeType.SCHEMA, "shop", "role-admin", permissionOccurredAt);
    ImportExecutedEvent importCommittedEvent =
        new ImportExecutedEvent("table-config-1", "user-1", 10, 0, true, importCommittedOccurredAt);
    ImportExecutedEvent importRolledBackEvent =
        new ImportExecutedEvent(
            "table-config-2", "user-2", 3, 2, false, importRolledBackOccurredAt);

    return List.of(
        new Case(
            "ConfigChangedEvent (BR7.2)",
            m -> m.fromConfigChangedEvent(configChangedEvent),
            "ConfigEngine",
            "shop.products",
            "DRAFT_IMPORTED",
            null,
            "system",
            configOccurredAt),
        new Case(
            "PermissionChangedEvent (BR7.3)",
            m -> m.fromPermissionChangedEvent(permissionChangedEvent),
            "PermissionEngine",
            "role-1",
            "PERMISSION_CHANGED",
            null,
            "role-admin",
            permissionOccurredAt),
        new Case(
            "ImportExecutedEvent - committed (BR7.4)",
            m -> m.fromImportExecutedEvent(importCommittedEvent),
            "DataImportExport",
            "table-config-1",
            "IMPORT_COMMITTED",
            "user-1",
            null,
            importCommittedOccurredAt),
        new Case(
            "ImportExecutedEvent - rolled back (BR7.4)",
            m -> m.fromImportExecutedEvent(importRolledBackEvent),
            "DataImportExport",
            "table-config-2",
            "IMPORT_ROLLED_BACK",
            "user-2",
            null,
            importRolledBackOccurredAt));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void mapsEventToAuditLogEntryAccordingToItsMappingRule(Case testCase) {
    AuditLogEntry entry = testCase.map().apply(mapper);

    assertThat(entry.getTargetType()).isEqualTo(testCase.expectedTargetType());
    assertThat(entry.getTargetId()).isEqualTo(testCase.expectedTargetId());
    assertThat(entry.getOperationType()).isEqualTo(testCase.expectedOperationType());
    assertThat(entry.getActorUserId()).isEqualTo(testCase.expectedActorUserId());
    assertThat(entry.getActorRaw()).isEqualTo(testCase.expectedActorRaw());
    assertThat(entry.getOccurredAt()).isEqualTo(testCase.expectedOccurredAt());
    // Q5=A: 現行3イベントはいずれもbeforeValue/afterValueを常にnullとする。
    assertThat(entry.getBeforeValue()).isNull();
    assertThat(entry.getAfterValue()).isNull();
  }

  // ---- user-managementのUserChangedEvent(5種類のoperation、rules.md BR4.9、team.md Q6) ----

  private record UserCase(
      String name,
      UserChangedEvent event,
      String expectedOperationType,
      String expectedActorUserId,
      String expectedActorRaw,
      Map<String, Object> expectedBefore,
      Map<String, Object> expectedAfter) {}

  private static UserSnapshot snapshot(String name, String email, String status, String... roles) {
    return new UserSnapshot(name, email, status, List.of(roles));
  }

  private static Map<String, Object> asMap(UserSnapshot snapshot) {
    return Map.of(
        "name", snapshot.name(),
        "email", snapshot.email(),
        "status", snapshot.status(),
        "roleIds", snapshot.roleIds());
  }

  private static UserChangedEvent userEvent(
      UserChangeOperation operation,
      String userId,
      UserSnapshot before,
      UserSnapshot after,
      String actor,
      Instant occurredAt) {
    return new UserChangedEvent(
        operation, UserChangedEvent.TARGET_TYPE_USER, userId, before, after, actor, occurredAt);
  }

  private static List<UserCase> userCases() {
    Instant at = Instant.parse("2026-09-20T05:00:00Z");
    UserSnapshot invited = snapshot("招待 太郎", "taro@example.test", "invited", "r1");
    UserSnapshot reinvited = snapshot("再招待 太郎", "taro@example.test", "invited", "r1", "r2");
    UserSnapshot active = snapshot("有効 太郎", "taro@example.test", "active", "r1", "r2");
    UserSnapshot updated = snapshot("更新 太郎", "taro@example.test", "active", "r3");
    UserSnapshot disabled = snapshot("更新 太郎", "taro@example.test", "disabled", "r3");
    UserSnapshot admin = snapshot("Administrator", "admin@example.test", "active");

    return List.of(
        new UserCase(
            "INVITED(新規招待): beforeはnull、actorは管理者のuserId",
            userEvent(UserChangeOperation.INVITED, "user-1", null, invited, "admin-1", at),
            "INVITED",
            "admin-1",
            null,
            null,
            asMap(invited)),
        new UserCase(
            "INVITED(再招待): beforeは非null",
            userEvent(UserChangeOperation.INVITED, "user-1", invited, reinvited, "admin-1", at),
            "INVITED",
            "admin-1",
            null,
            asMap(invited),
            asMap(reinvited)),
        new UserCase(
            "ACTIVATED(招待受諾): actorは受諾したUser自身のuserId",
            userEvent(UserChangeOperation.ACTIVATED, "user-1", reinvited, active, "user-1", at),
            "ACTIVATED",
            "user-1",
            null,
            asMap(reinvited),
            asMap(active)),
        new UserCase(
            "UPDATED(管理者による更新)",
            userEvent(UserChangeOperation.UPDATED, "user-1", active, updated, "admin-2", at),
            "UPDATED",
            "admin-2",
            null,
            asMap(active),
            asMap(updated)),
        new UserCase(
            "DISABLED(無効化・招待取消)",
            userEvent(UserChangeOperation.DISABLED, "user-1", updated, disabled, "admin-2", at),
            "DISABLED",
            "admin-2",
            null,
            asMap(updated),
            asMap(disabled)),
        new UserCase(
            "BOOTSTRAPPED(初期管理者の自動作成): actorはシステム識別子でactorRawへ、actorUserIdはnull",
            userEvent(UserChangeOperation.BOOTSTRAPPED, "admin-user", null, admin, "system", at),
            "BOOTSTRAPPED",
            null,
            "system",
            null,
            asMap(admin)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("userCases")
  void mapsAUserChangedEventAccordingToBr49(UserCase testCase) {
    AuditLogEntry entry = mapper.fromUserChangedEvent(testCase.event());

    assertThat(entry.getTargetType()).isEqualTo("User");
    assertThat(entry.getTargetId()).isEqualTo(testCase.event().targetId());
    assertThat(entry.getOperationType()).isEqualTo(testCase.expectedOperationType());
    assertThat(entry.getActorUserId()).isEqualTo(testCase.expectedActorUserId());
    assertThat(entry.getActorRaw()).isEqualTo(testCase.expectedActorRaw());
    assertThat(entry.getOccurredAt()).isEqualTo(testCase.event().occurredAt());
    assertThat(entry.getBeforeValue()).isEqualTo(testCase.expectedBefore());
    assertThat(entry.getAfterValue()).isEqualTo(testCase.expectedAfter());
  }

  @Test
  void theSnapshotMapsHaveExactlyTheFourAuditedFieldsAndNoCredentials() {
    AuditLogEntry entry =
        mapper.fromUserChangedEvent(
            userEvent(
                UserChangeOperation.UPDATED,
                "user-1",
                snapshot("a", "a@example.test", "active", "r1"),
                snapshot("b", "a@example.test", "active", "r2"),
                "admin-1",
                Instant.parse("2026-09-20T05:00:00Z")));

    assertThat(entry.getBeforeValue()).containsOnlyKeys("name", "email", "status", "roleIds");
    assertThat(entry.getAfterValue()).containsOnlyKeys("name", "email", "status", "roleIds");
    assertThat(entry.getAfterValue().toString())
        .doesNotContain("passwordHash")
        .doesNotContain("invitationToken");
  }

  @Test
  void everyOperationOfTheEventIsCoveredByTheTable() {
    assertThat(userCases())
        .extracting(c -> c.event().operation())
        .containsAll(List.of(UserChangeOperation.values()));
  }
}
