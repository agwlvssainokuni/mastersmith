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

package com.mastersmith.permission.resolver;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 実効権限のスコープ階層解決アルゴリズム(rules.md BR3.4: 主権限COLUMN→TABLE→SCHEMA、BR3.5: 補助権限TABLE→SCHEMA、BR3.6: 全階層
 * 指定なし時のデフォルト)。
 *
 * <p>PermissionEngineApiの薄い委譲先ではなく、resolveEffectivePermission(W1)の中核アルゴリズムそのものを実装する。ロールを跨いだ解決は行わない
 * (ロール階層は存在しないため、渡されたroleId単一ロールの設定のみを参照する、rules.md BR3.4)。
 */
@Component
public class PermissionResolver {

  private final ConfigEngineApi configEngineApi;
  private final PrimaryPermissionRepository primaryPermissionRepository;
  private final AuxiliaryPermissionRepository auxiliaryPermissionRepository;

  public PermissionResolver(
      ConfigEngineApi configEngineApi,
      PrimaryPermissionRepository primaryPermissionRepository,
      AuxiliaryPermissionRepository auxiliaryPermissionRepository) {
    this.configEngineApi = configEngineApi;
    this.primaryPermissionRepository = primaryPermissionRepository;
    this.auxiliaryPermissionRepository = auxiliaryPermissionRepository;
  }

  /**
   * 指定されたroleIdの、指定されたスコープに対する実効権限を解決する(W1)。
   *
   * @param roleId 判定対象のロール(activeRoleId)
   * @param scopeType 問い合わせの起点となるスコープ種別
   * @param scopeRef scopeTypeに応じた対象識別子(不透明な識別子として扱う、rules.md BR3.14)
   */
  public EffectivePermission resolve(String roleId, ScopeType scopeType, String scopeRef) {
    List<ScopePosition> chain = buildScopeChain(scopeType, scopeRef);

    PermissionLevel level = resolvePrimaryLevel(roleId, chain);
    boolean canCreate = resolveAuxiliaryField(roleId, chain, AuxiliaryPermission::getCreateAllowed);
    boolean canDelete = resolveAuxiliaryField(roleId, chain, AuxiliaryPermission::getDeleteAllowed);
    return new EffectivePermission(level, canCreate, canDelete);
  }

  /** BR3.4: COLUMN→TABLE→SCHEMAの順に、最初に明示設定が見つかった階層のlevelを採用する。見つからなければNONE(BR3.6)。 */
  private PermissionLevel resolvePrimaryLevel(String roleId, List<ScopePosition> chain) {
    for (ScopePosition position : chain) {
      Optional<PrimaryPermission> row =
          primaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
              roleId, position.scopeType(), position.scopeRef());
      if (row.isPresent()) {
        return row.get().getLevel();
      }
    }
    return PermissionLevel.NONE;
  }

  /**
   * BR3.5: TABLE→SCHEMAの順に(AuxiliaryPermissionはCOLUMNを対象としないためchainのCOLUMN段は読み飛ばす)、fieldGetterが最初に
   * 非nullを返す階層の値を採用する。createAllowed/deleteAllowedは独立に解決するため、フィールドごとに個別に本メソッドを呼び出す。見つからなければ
   * false(BR3.6)。
   */
  private boolean resolveAuxiliaryField(
      String roleId,
      List<ScopePosition> chain,
      Function<AuxiliaryPermission, Boolean> fieldGetter) {
    for (ScopePosition position : chain) {
      if (position.scopeType() == ScopeType.COLUMN) {
        continue;
      }
      Optional<AuxiliaryPermission> row =
          auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
              roleId, position.scopeType(), position.scopeRef());
      if (row.isPresent()) {
        Boolean value = fieldGetter.apply(row.get());
        if (value != null) {
          return value;
        }
      }
    }
    return false;
  }

  /**
   * 起点となる(scopeType, scopeRef)から、より上位の階層へ向かうスコープ列を構築する。COLUMN起点はCOLUMN・TABLE・SCHEMAの3段(親TABLEの
   * tableConfigIdはconfig-engineから解決)、TABLE起点はTABLE・SCHEMAの2段、SCHEMA起点はSCHEMAの1段のみとなる。
   */
  private List<ScopePosition> buildScopeChain(ScopeType scopeType, String scopeRef) {
    List<ScopePosition> chain = new ArrayList<>();
    switch (scopeType) {
      case COLUMN -> {
        chain.add(new ScopePosition(ScopeType.COLUMN, scopeRef));
        Optional<ColumnConfig> columnConfig = configEngineApi.findColumnConfigById(scopeRef);
        if (columnConfig.isPresent()) {
          String tableConfigId = columnConfig.get().getTableConfigId();
          chain.add(new ScopePosition(ScopeType.TABLE, tableConfigId));
          appendSchemaPosition(chain, tableConfigId);
        }
        // columnConfigが見つからない場合(データ不整合)は、それ以上の階層解決を打ち切る。
        // COLUMN段のPrimaryPermission検索のみ行い、見つからなければBR3.6のデフォルトへ委ねる。
      }
      case TABLE -> {
        chain.add(new ScopePosition(ScopeType.TABLE, scopeRef));
        appendSchemaPosition(chain, scopeRef);
      }
      case SCHEMA -> chain.add(new ScopePosition(ScopeType.SCHEMA, scopeRef));
      default -> throw new IllegalArgumentException("Unsupported scopeType: " + scopeType);
    }
    return chain;
  }

  private void appendSchemaPosition(List<ScopePosition> chain, String tableConfigId) {
    try {
      TableConfig tableConfig = configEngineApi.getTableConfigById(tableConfigId);
      chain.add(new ScopePosition(ScopeType.SCHEMA, tableConfig.getSchemaName()));
    } catch (TableConfigNotFoundException e) {
      // 対応するテーブル設定がconfig-engine側に存在しない(データ不整合)。SCHEMA階層への解決を
      // 諦め、それ以降の階層探索はBR3.6のデフォルト(NONE/禁止)に委ねる。
    }
  }

  private record ScopePosition(ScopeType scopeType, String scopeRef) {}
}
