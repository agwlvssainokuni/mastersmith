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

package com.mastersmith.permission.dto;

import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.List;

/**
 * 設定の取り込み(config-import-export)が、permission-engineに渡す、RBAC設定一式(検証専用メソッド・反映するメソッドの入力、C10の追補)。ロール・グループは名前(自然キー)で表し、権限の対象は
 * {@link Scope}で表す。ユーザー・ユーザーのグループ所属は含めない。
 *
 * <p>各リストの添え字は、呼び出し元(config-import-export)のファイルの中の順序に対応し、検証の誤りの位置({@code
 * primaryPermissions[3]}など)に用いる。
 *
 * @param roles ロール
 * @param groups グループと、対応するロールの名前
 * @param primaryPermissions 主権限(FULL・READ・NONE。「指定なし」は、エントリを持たないことで表す)
 * @param auxiliaryPermissions 補助権限(作成・削除)
 */
public record RbacImportSet(
    List<Role> roles,
    List<Group> groups,
    List<Primary> primaryPermissions,
    List<Auxiliary> auxiliaryPermissions) {

  public RbacImportSet {
    roles = roles == null ? List.of() : List.copyOf(roles);
    groups = groups == null ? List.of() : List.copyOf(groups);
    primaryPermissions = primaryPermissions == null ? List.of() : List.copyOf(primaryPermissions);
    auxiliaryPermissions =
        auxiliaryPermissions == null ? List.of() : List.copyOf(auxiliaryPermissions);
  }

  /** ロール1件(名前が自然キー)。 */
  public record Role(String name) {}

  /** グループ1件と、対応するロールの名前。 */
  public record Group(String name, List<String> roleNames) {
    public Group {
      roleNames = roleNames == null ? List.of() : List.copyOf(roleNames);
    }
  }

  /**
   * 権限の対象。検証の段階では、取り込みで新規に作られるテーブル・カラムは、内部IDが未定であるため、{@code tableConfigId}・{@code
   * columnConfigId}がnull(仮の識別)になりうる
   * (現在の環境に、同じ自然キーのテーブル・カラムがある場合は、その内部ID)。反映の段階では、呼び出し元が、schemaの反映の結果から、すべて解決して渡す。
   *
   * <p>permission-engineの内部では、対象の識別子({@code
   * scopeRef})は、SCHEMAはスキーマ名、TABLEはtableConfigId、COLUMNはcolumnConfigIdの、不透明な文字列である(BR3.14)。
   *
   * @param scopeType 対象の階層
   * @param schemaName スキーマ名(SCHEMAの識別子。TABLE・COLUMNでも、階層の解決に用いる)
   * @param tableConfigId TABLE・COLUMNの、テーブルの内部ID(新規のテーブルは、検証の段階でnull)
   * @param columnConfigId COLUMNの、カラムの内部ID(新規のカラムは、検証の段階でnull)
   */
  public record Scope(
      ScopeType scopeType, String schemaName, String tableConfigId, String columnConfigId) {

    /** 権限の行に保存する、対象の識別子(未定ならnull)。 */
    public String scopeRef() {
      return switch (scopeType) {
        case SCHEMA -> schemaName;
        case TABLE -> tableConfigId;
        case COLUMN -> columnConfigId;
      };
    }
  }

  /** 主権限の割当1件。 */
  public record Primary(String roleName, Scope scope, PermissionLevel level) {}

  /** 補助権限の割当1件({@code null}は「指定なし」。上位の設定を継承する)。 */
  public record Auxiliary(
      String roleName, Scope scope, Boolean createAllowed, Boolean deleteAllowed) {}
}
