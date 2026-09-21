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

package com.mastersmith.permission.rbacio;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.dto.RbacImportSet.Auxiliary;
import com.mastersmith.permission.dto.RbacImportSet.Primary;
import com.mastersmith.permission.dto.RbacImportSet.Scope;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * RBAC設定の取り込みの、検証専用の処理(何も反映しない。config-import-export BR9.10〜BR9.12)。誤りは、例外ではなく、全件を集めた一覧で返す。
 *
 * <ul>
 *   <li><b>構造</b>: 権限の対象の必須項目・長さの上限・予約スキーマ名・補助権限の対象にCOLUMNを指定できないこと。
 *   <li><b>主権限が0件</b>(BR9.12): ブートストラップ状態でも、拒否する(初期状態の例外の再有効化による権限昇格を防ぐため)。
 *   <li><b>権限昇格</b>(BR3.8・BR9.11):
 *       すべてのエントリについて、操作者(activeRoleId)の、<b>取り込み開始時点の</b>実効権限(呼び出し元の{@code
 *       REPEATABLE_READ}のトランザクションの
 *       スナップショット)を基準に、上回る割当をすべて集める。ブートストラップ状態(取り込み開始時点で主権限が0件)では、判定しない(初回のRBAC投入を許す)。操作者のactiveRoleIdが未選択・空なら、
 *       割当を持たない者として、fail closedで判定する(NONE・false)。
 * </ul>
 */
@Component
public class RbacImportValidator {

  /** scopeRefの文字列長の上限(PermissionEngineApiImplと同じ。security-design.md「入力検証・インジェクション対策」)。 */
  static final int MAX_SCOPE_REF_LENGTH = 255;

  private final PrimaryPermissionRepository primaryRepository;
  private final AuxiliaryPermissionRepository auxiliaryRepository;

  public RbacImportValidator(
      PrimaryPermissionRepository primaryRepository,
      AuxiliaryPermissionRepository auxiliaryRepository) {
    this.primaryRepository = primaryRepository;
    this.auxiliaryRepository = auxiliaryRepository;
  }

  public List<ImportValidationError> validate(
      RbacImportSet set, String actorRoleId, boolean bootstrapAtStart) {
    List<ImportValidationError> errors = new ArrayList<>();
    if (set.primaryPermissions().isEmpty()) {
      errors.add(ImportValidationError.of("primaryPermissions", ImportMessageKeys.RBAC_EMPTY));
    }
    boolean[] primaryValid = new boolean[set.primaryPermissions().size()];
    for (int i = 0; i < set.primaryPermissions().size(); i++) {
      Primary entry = set.primaryPermissions().get(i);
      String path = "primaryPermissions[" + i + "]";
      int before = errors.size();
      validateScope(entry.scope(), path + ".scope", errors);
      if (entry.level() == null) {
        errors.add(ImportValidationError.of(path + ".level", ImportMessageKeys.FIELD_REQUIRED));
      }
      primaryValid[i] = errors.size() == before;
    }
    boolean[] auxiliaryValid = new boolean[set.auxiliaryPermissions().size()];
    for (int i = 0; i < set.auxiliaryPermissions().size(); i++) {
      Auxiliary entry = set.auxiliaryPermissions().get(i);
      String path = "auxiliaryPermissions[" + i + "]";
      int before = errors.size();
      validateScope(entry.scope(), path + ".scope", errors);
      if (entry.scope() != null && entry.scope().scopeType() == ScopeType.COLUMN) {
        // 補助権限は、SCHEMA・TABLEだけを対象とする(entities.md AuxiliaryPermission)。
        errors.add(
            ImportValidationError.of(path + ".scope", ImportMessageKeys.RBAC_AUXILIARY_COLUMN));
      }
      auxiliaryValid[i] = errors.size() == before;
    }
    if (!bootstrapAtStart) {
      checkEscalation(set, actorRoleId, primaryValid, auxiliaryValid, errors);
    }
    return errors;
  }

  private static void validateScope(Scope scope, String path, List<ImportValidationError> errors) {
    if (scope == null || scope.scopeType() == null) {
      errors.add(ImportValidationError.of(path + ".scopeType", ImportMessageKeys.FIELD_REQUIRED));
      return;
    }
    String schemaName = scope.schemaName();
    if (schemaName == null || schemaName.isBlank()) {
      errors.add(ImportValidationError.of(path + ".schemaName", ImportMessageKeys.FIELD_REQUIRED));
      return;
    }
    if (schemaName.length() > MAX_SCOPE_REF_LENGTH) {
      errors.add(
          ImportValidationError.of(
              path + ".schemaName",
              ImportMessageKeys.FIELD_TOO_LONG,
              Map.of("max", MAX_SCOPE_REF_LENGTH)));
      return;
    }
    if (scope.scopeType() == ScopeType.SCHEMA
        && ReservedScopes.hasReservedPrefix(schemaName)
        && !ReservedScopes.isReservedSchemaName(schemaName)) {
      errors.add(
          ImportValidationError.of(path + ".schemaName", ImportMessageKeys.RBAC_RESERVED_UNKNOWN));
    }
  }

  private void checkEscalation(
      RbacImportSet set,
      String actorRoleId,
      boolean[] primaryValid,
      boolean[] auxiliaryValid,
      List<ImportValidationError> errors) {
    ActorGrantSnapshot actor =
        actorRoleId == null || actorRoleId.isBlank()
            ? ActorGrantSnapshot.empty()
            : ActorGrantSnapshot.load(actorRoleId, primaryRepository, auxiliaryRepository);
    for (int i = 0; i < set.primaryPermissions().size(); i++) {
      Primary entry = set.primaryPermissions().get(i);
      if (primaryValid[i] && entry.level().rank() > actor.effectiveLevel(entry.scope()).rank()) {
        errors.add(
            ImportValidationError.of(
                "primaryPermissions[" + i + "]", ImportMessageKeys.RBAC_ESCALATION));
      }
    }
    for (int i = 0; i < set.auxiliaryPermissions().size(); i++) {
      Auxiliary entry = set.auxiliaryPermissions().get(i);
      if (!auxiliaryValid[i]) {
        continue;
      }
      String path = "auxiliaryPermissions[" + i + "]";
      if (Boolean.TRUE.equals(entry.createAllowed()) && !actor.canCreate(entry.scope())) {
        errors.add(
            ImportValidationError.of(path + ".createAllowed", ImportMessageKeys.RBAC_ESCALATION));
      }
      if (Boolean.TRUE.equals(entry.deleteAllowed()) && !actor.canDelete(entry.scope())) {
        errors.add(
            ImportValidationError.of(path + ".deleteAllowed", ImportMessageKeys.RBAC_ESCALATION));
      }
    }
  }
}
