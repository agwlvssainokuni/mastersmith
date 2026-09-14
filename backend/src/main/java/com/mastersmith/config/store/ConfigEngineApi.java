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

package com.mastersmith.config.store;

import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.ConfigImportSet;
import com.mastersmith.config.dto.TableConfigDraft;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * config-engine(U1)が提供する内部Javaインタフェース契約 (inception/contract-design/contract-summary.md C9:
 * ConfigEngineApi)。
 *
 * <p>consumers: schema-introspector, permission-engine, data-import-export, list-engine,
 * record-edit-engine, config-import-export(いずれも同一プロセス内、shared-schema)。
 */
public interface ConfigEngineApi {

  /** 指定された(schemaName, tableName)のTableConfigを返す。 */
  TableConfig getTableConfig(String schemaName, String tableName)
      throws TableConfigNotFoundException;

  /** 指定されたtableConfigIdに属する全ColumnConfigを返す(表示順は保証しない。呼び出し元がdisplayOrderでソートする)。 */
  List<ColumnConfig> getColumnConfigs(String tableConfigId);

  /**
   * 指定されたtableConfigIdに対応するTableConfigを返す(物理テーブル名(schemaName/tableName)の解決が必要な
   * 内部コンシューマー向け。data-import-export(U8)がCSVエクスポート・インポート対象の物理テーブルを
   * 解決するために必要とする、C9契約への追加メソッド。functional-spec.md W1/W2はtableConfigIdのみを
   * 受け取るため、getTableConfig(schemaName, tableName)とは逆方向の解決が別途必要となる)。
   */
  TableConfig getTableConfigById(String tableConfigId) throws TableConfigNotFoundException;

  /** 対象テーブルの楽観ロック対象列(更新日時/バージョン列)の明示設定を返す(BR1.7)。 */
  Optional<String> getOptimisticLockColumn(String tableConfigId)
      throws TableConfigNotFoundException;

  /**
   * schema-introspector専用。既存設定がある場合は上書きしない(fail fastではなくスキップ、BR1.8)。
   *
   * @return 新規生成されたTableConfigのtableConfigIdの一覧(スキップされたテーブルは含まない)
   */
  List<String> writeTableConfigDraft(TableConfigDraft draft);

  /** config-import-export専用。スキーマ・カラム設定一式を返す(W5)。 */
  ConfigExportSet getExportableConfigSet();

  /** config-import-export専用。設定定義に誤りがある場合はfail fastで送出し、内部設定DBへ反映しない(BR1.1, BR1.11)。 */
  void importConfigSet(ConfigImportSet configSet) throws ConfigValidationException;
}
