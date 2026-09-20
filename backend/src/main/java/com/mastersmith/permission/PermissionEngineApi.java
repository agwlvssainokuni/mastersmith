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

package com.mastersmith.permission;

import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.exception.PermissionEscalationException;
import java.util.List;

/**
 * permission-engine(U3)が提供する内部Javaインタフェース契約 (inception/contract-design/contract-summary.md C10:
 * PermissionEngineApi)。
 *
 * <p>consumers(contract-summary.md C10確定分): user-management, menu-navigation, audit-logging,
 * list-engine, record-edit-engine, config-import-export(いずれも同一プロセス内、shared-schema)。
 *
 * <p><b>C10契約からの追補・差異(functional-spec.md「Domain Design/Contract Designへの追補」参照)</b>:
 *
 * <ul>
 *   <li>{@code scopeType}はC10 YAMLでは{@code "schema|table|column"}という文字列で表現されているが、本インタフェースは {@link
 *       ScopeType}(entities.md準拠の型付き列挙)として受け渡す。同様に{@code level}は{@link PermissionLevel}を用いる。
 *   <li>{@code assignPermission}は、C10 YAMLの{@code params: {roleId, scopeType, scopeRef,
 *       level}}に加えて{@code
 *       actorRoleId}(割当操作を実行している操作者のactiveRoleId)を引数に追加する。BR3.8(権限昇格の防止、project.md
 *       Forbidden)は操作者自身の実効権限との比較を要求するが、C10契約はその操作者情報を運ぶパラメータを持たないため、本メソッドの核心的責務(昇格防止)を
 *       実装するために構造的に必須な拡張である(config-engineの{@code ConfigChangedEvent}が抱える同種の「actor伝搬」の未解決課題を、
 *       本契約では引数として明示的に解決する)。
 *   <li>{@code assignAuxiliaryPermission}はC10 YAMLに明示されていない追加メソッドである。functional-spec.md W4は
 *       主権限(level)だけでなく補助権限(createAllowed/deleteAllowed)の割当も{@code assignPermission}が担うと記述しているが、
 *       C10 YAMLの{@code params}は{@code level}のみを持つ。entities.mdがAuxiliaryPermissionを独立したエンティティとして
 *       定義していることと整合させるため、主権限用と補助権限用のメソッドを分離した(計画外・正当化された逸脱。config-engineの{@code
 *       getTableConfigById}追加と同種の、ユニット自身の契約への軽微な拡張)。
 *   <li>{@code getGroupDerivedRoleIds(userId):
 *       List<String>}は、W3(選択可能ロール一覧の算出)のためfunctional-spec.mdが
 *       識別した契約ギャップに対応する追加メソッド(functional-spec.md「Domain Design/Contract Designへの追補」3番)。
 * </ul>
 */
public interface PermissionEngineApi {

  /**
   * ロール階層継承後の実効権限を再検証する(W1、rules.md BR3.4〜BR3.6)。画面表示の出し分けだけに依存してはならず、全コンシューマーは必ず本メソッド
   * 経由でサーバー側検証を行う(project.md Mandated、rules.md BR3.7)。
   *
   * @param activeRoleId 判定対象のロール。実在しない(削除済みを含む)場合は安全側のデフォルト({@link
   *     EffectivePermission#NONE})を返す(security-design.md「多層防御」)
   * @param scopeType 問い合わせの起点となるスコープ種別
   * @param scopeRef scopeTypeに応じた対象識別子(不透明な識別子として扱う、rules.md BR3.14)
   */
  EffectivePermission resolveEffectivePermission(
      String activeRoleId, ScopeType scopeType, String scopeRef);

  /**
   * 画面(ユーザ管理・監査ログ閲覧・メニュー項目)へのアクセス可否を判定する(W2、rules.md BR3.10)。
   *
   * @param activeRoleId 判定対象のロール
   * @param screenKey 予約キー({@code user-management}/{@code audit-log}/{@code config-import-export})、
   *     またはconfig-engineのtableConfigId(業務メニュー項目)
   */
  boolean canAccessScreen(String activeRoleId, String screenKey);

  /**
   * 主権限を割り当てる(W4)。権限管理者による明示的操作でのみ呼び出し可能であり、権限昇格(自分自身への昇格を含む)を防止する(project.md Forbidden、rules.md
   * BR3.8)。呼び出し経路はconfig-import-export(C7 `/api/config/import`)のみとする(rules.md BR3.9)。
   *
   * @param actorRoleId 割当操作を実行している操作者のactiveRoleId(BR3.8の比較基準)
   * @param targetRoleId 割当先のロール
   * @param scopeType 割当対象のスコープ種別
   * @param scopeRef 割当対象のスコープ参照
   * @param level 割り当てる主権限
   * @throws PermissionEscalationException 操作者自身の実効権限を上回る割当が試みられた場合(ブートストラップ状態を除く、rules.md BR3.13)
   */
  void assignPermission(
      String actorRoleId,
      String targetRoleId,
      ScopeType scopeType,
      String scopeRef,
      PermissionLevel level)
      throws PermissionEscalationException;

  /**
   * 補助権限を割り当てる(W4)。主権限と同じ昇格防止規則(rules.md BR3.8)を、createAllowed/deleteAllowedそれぞれ独立に適用する。
   *
   * @param actorRoleId 割当操作を実行している操作者のactiveRoleId
   * @param targetRoleId 割当先のロール
   * @param scopeType 割当対象のスコープ種別({@link ScopeType#COLUMN}は対象外、entities.md
   *     AuxiliaryPermission。指定された場合は{@link IllegalArgumentException}でfail fast拒否する)
   * @param scopeRef 割当対象のスコープ参照
   * @param createAllowed 割り当てるcreateAllowed(nullは「指定なし」を表し、既存設定を変更しない)
   * @param deleteAllowed 割り当てるdeleteAllowed(nullは「指定なし」を表し、既存設定を変更しない)
   * @throws PermissionEscalationException 操作者自身の実効権限を上回る割当が試みられた場合
   * @throws IllegalArgumentException scopeTypeが{@link ScopeType#COLUMN}の場合、またはscopeRefが不正な場合
   */
  void assignAuxiliaryPermission(
      String actorRoleId,
      String targetRoleId,
      ScopeType scopeType,
      String scopeRef,
      Boolean createAllowed,
      Boolean deleteAllowed)
      throws PermissionEscalationException;

  /**
   * 指定されたUserがGroup所属を通じて間接的に得るロールID一覧を返す(W3、rules.md BR3.2)。user-managementが自身の{@code
   * User.roleIds}(直接付与分)と本メソッドの戻り値(Group経由分)を合成し、選択可能ロール一覧(rules.md BR3.3)を構成する。
   */
  List<String> getGroupDerivedRoleIds(String userId);

  /**
   * 指定されたroleIdが、permission-engine側に実在するかを判定する(Contract Design追補、user-management(U4)のrules.md
   * BR4.5・Q6)。 user-managementが、招待・更新でroleIdsを指定・変更する際の実在検証に用いる。実在しない(削除済みを含む)場合はfalse。
   *
   * @param roleId 判定対象のroleId
   */
  boolean roleExists(String roleId);
}
