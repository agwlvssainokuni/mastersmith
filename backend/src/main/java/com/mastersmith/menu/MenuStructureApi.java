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

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.menu.dto.MenuImportItem;
import com.mastersmith.menu.dto.MenuStructureEntry;
import java.util.List;

/**
 * menu-navigation(U6)が提供する内部Javaインタフェース契約 (inception/contract-design/contract-summary.md C12:
 * MenuStructureApi)。
 *
 * <p>consumers: config-import-export(U9)。C3(menu-navigation REST
 * API)とは異なる境界であり、config-import-exportが設定一式のexport/import対象にメニュー構成を含めるために用いる。
 *
 * <p>C12契約は往復する型を{@code MenuItem}(menuItemId, parentMenuItemId, label, order,
 * targetTableConfigId)という名前で定義しているが、本ユニットには同名の永続化エンティティ・REST用DTOが既に存在するため、{@link
 * MenuStructureEntry}という別名のJava型を用いる(命名上の軽微な逸脱、フィールド構成はC12契約と1:1)。管理メニュー4項目({@link
 * com.mastersmith.menu.tree.AdminMenuDefinition})はexport/importの対象外(BR6.2、DBへ永続化しないため)。
 *
 * <p>取り込みは、検証と反映の2つに分かれる(config-import-export functional-spec.md 追補一覧3番、BR9.10・BR9.14)。
 */
public interface MenuStructureApi {

  /**
   * config-import-export専用。現在永続化されている業務メニュー(MenuItem)一式を、内部設定DBから直接読み、フラットな一覧として返す。呼び出し元の(読み取り専用の)トランザクションの中で呼ぶこと
   * (他のユニットの読み取りと、同じ時点のスナップショットにするため)。メニューは、キャッシュを持たない。
   */
  List<MenuStructureEntry> getExportableMenuStructure();

  /**
   * config-import-export専用。取り込みの検証だけを行う(何も反映しない、BR9.10)。構造の規則(階層・親の参照・表示名)を、例外ではなく、全件を集めた一覧(位置は、入力が持つ位置の文字列)で返す。遷移先のテーブルの実在は、
   * 反映の順序(schemaが先)により、反映の段階で満たされるため、ここでは検証しない。誤りがなければ、空。
   */
  List<ImportValidationError> validateMenuStructure(List<MenuImportItem> items);

  /**
   * config-import-export専用。業務メニューを全置換で反映する(既存の項目をすべて削除し、入力の項目を、新しいIDで採番して追加する。BR9.14)。伝播は{@code
   * MANDATORY}で、自身ではコミットしない。メニューは、キャッシュを持たず、イベントも発行しないため、確定後の動作は空である。
   */
  ApplyResult applyMenuStructure(List<MenuImportItem> items);
}
