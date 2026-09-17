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

package com.mastersmith.menu;

import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.menu.dto.MenuStructureEntry;
import java.util.List;

/**
 * menu-navigation(U6)が提供する内部Javaインタフェース契約 (inception/contract-design/contract-summary.md C12:
 * MenuStructureApi)。
 *
 * <p>consumers: config-import-export(U9、本Bolt時点では未実装のコンシューマー)。C3(menu-navigation REST
 * API)とは異なる境界であり、config-import-exportが設定一式のexport/import対象にメニュー構成を含めるために用いる。
 *
 * <p>C12契約は往復する型を{@code MenuItem}(menuItemId, parentMenuItemId, label, order,
 * targetTableConfigId)という名前で定義しているが、本ユニットには同名の永続化エンティティ・REST用DTOが既に存在するため、{@link
 * MenuStructureEntry}という別名のJava型を用いる(命名上の軽微な逸脱、フィールド構成はC12契約と1:1)。管理メニュー4項目({@link
 * com.mastersmith.menu.tree.AdminMenuDefinition})はexport/importの対象外(BR6.2、DBへ永続化しないため)。
 */
public interface MenuStructureApi {

  /** config-import-export専用。現在永続化されている業務メニュー(MenuItem)一式をフラットな一覧として返す。 */
  List<MenuStructureEntry> getExportableMenuStructure();

  /**
   * config-import-export専用。指定された業務メニュー一式で全件洗い替えする。設定定義に誤りがある場合はfail
   * fastで送出し、内部設定DBへ反映しない(project.md Mandated、C9の{@code importConfigSet}と同種の方針)。
   */
  void importMenuStructure(List<MenuStructureEntry> items) throws ConfigValidationException;
}
