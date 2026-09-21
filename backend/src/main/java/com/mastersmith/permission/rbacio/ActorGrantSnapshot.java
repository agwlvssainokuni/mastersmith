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

import com.mastersmith.permission.dto.RbacImportSet.Scope;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 取り込みの権限昇格の判定(BR9.11)の基準となる、操作者(activeRoleId)の、取り込み開始時点の割当の、メモリ上のスナップショット。操作者の主権限・補助権限を、1回の問い合わせで、すべて読み、
 * 取り込みのすべてのエントリの実効権限を、メモリ上で解決する(エントリごとに問い合わせない。NFR1.2)。
 *
 * <p>解決の規則は、{@code PermissionResolver}と同じである(rules.md BR3.4:
 * 主権限はCOLUMN→TABLE→SCHEMAの順に、最初に明示設定が見つかった階層のレベル。なければNONE(BR3.6)。BR3.5: 補助権限は、
 * COLUMNを対象とせず、TABLE→SCHEMAの順に、最初にnullでない値。createAllowed・deleteAllowedは独立に解決。なければfalse)。取り込みで新規に作られるテーブル・カラム(内部IDが未定)は、その階層に割当がなく、
 * 上位の階層にフォールバックする。
 */
final class ActorGrantSnapshot {

  private record Key(ScopeType scopeType, String scopeRef) {}

  private record Auxiliary(Boolean createAllowed, Boolean deleteAllowed) {}

  private final Map<Key, PermissionLevel> primary = new HashMap<>();
  private final Map<Key, Auxiliary> auxiliary = new HashMap<>();

  private ActorGrantSnapshot() {}

  /** 操作者の割当がない(activeRoleIdが未選択など)スナップショット。すべてNONE・false(fail closed)。 */
  static ActorGrantSnapshot empty() {
    return new ActorGrantSnapshot();
  }

  static ActorGrantSnapshot load(
      String actorRoleId,
      PrimaryPermissionRepository primaryRepository,
      AuxiliaryPermissionRepository auxiliaryRepository) {
    ActorGrantSnapshot snapshot = new ActorGrantSnapshot();
    List<PrimaryPermission> primaryRows = primaryRepository.findByRoleId(actorRoleId);
    for (PrimaryPermission row : primaryRows) {
      snapshot.primary.put(new Key(row.getScopeType(), row.getScopeRef()), row.getLevel());
    }
    List<AuxiliaryPermission> auxiliaryRows = auxiliaryRepository.findByRoleId(actorRoleId);
    for (AuxiliaryPermission row : auxiliaryRows) {
      snapshot.auxiliary.put(
          new Key(row.getScopeType(), row.getScopeRef()),
          new Auxiliary(row.getCreateAllowed(), row.getDeleteAllowed()));
    }
    return snapshot;
  }

  /** 対象の、実効主権限(BR3.4・BR3.6)。 */
  PermissionLevel effectiveLevel(Scope scope) {
    for (Key key : chain(scope, true)) {
      PermissionLevel level = primary.get(key);
      if (level != null) {
        return level;
      }
    }
    return PermissionLevel.NONE;
  }

  /** 対象の、実効補助権限(作成。BR3.5・BR3.6)。 */
  boolean canCreate(Scope scope) {
    return effectiveAuxiliary(scope, true);
  }

  /** 対象の、実効補助権限(削除。BR3.5・BR3.6)。 */
  boolean canDelete(Scope scope) {
    return effectiveAuxiliary(scope, false);
  }

  private boolean effectiveAuxiliary(Scope scope, boolean create) {
    for (Key key : chain(scope, false)) {
      Auxiliary row = auxiliary.get(key);
      if (row != null) {
        Boolean value = create ? row.createAllowed() : row.deleteAllowed();
        if (value != null) {
          return value;
        }
      }
    }
    return false;
  }

  /** 対象から、上位へ向かう探索の列。内部IDが未定(仮の識別)の階層は、飛ばす。補助権限は、COLUMNを含まない。 */
  private static List<Key> chain(Scope scope, boolean includeColumn) {
    java.util.ArrayList<Key> chain = new java.util.ArrayList<>(3);
    if (includeColumn && scope.scopeType() == ScopeType.COLUMN && scope.columnConfigId() != null) {
      chain.add(new Key(ScopeType.COLUMN, scope.columnConfigId()));
    }
    if (scope.scopeType() != ScopeType.SCHEMA && scope.tableConfigId() != null) {
      chain.add(new Key(ScopeType.TABLE, scope.tableConfigId()));
    }
    chain.add(new Key(ScopeType.SCHEMA, scope.schemaName()));
    return chain;
  }
}
