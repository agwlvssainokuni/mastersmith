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

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.ConfigNaturalKeySet;
import com.mastersmith.config.dto.TableConfigDraft;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
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
   * 指定されたcolumnConfigIdに対応するColumnConfigを返す(存在しない場合はempty)。permission-engine(U3)が主権限のスコープ階層解決
   * (rules.md BR3.4: COLUMN→TABLE→SCHEMA)において、COLUMNスコープから親TABLEのtableConfigIdを解決するために必要とする、
   * C9契約への追加メソッド({@link #getTableConfigById(String)}と同種の軽微な拡張)。例外ではなく{@code
   * Optional.empty()}を返す設計とするのは、
   * 呼び出し元(permission-engine)がこの不在を「データ不整合」ではなく「それ以上の階層解決を打ち切りBR3.6のデフォルトへフォールバックする」という
   * 通常の制御フローとして扱うため。
   */
  Optional<ColumnConfig> findColumnConfigById(String columnConfigId);

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

  /**
   * config-import-export専用。スキーマ・カラム設定・翻訳一式を、キャッシュを介さず、内部設定DBから直接読み、他から変更できないスナップショットとして返す(NFR4.4)。呼び出し元の(読み取り専用の)トランザクションの中で
   * 呼ぶこと(同じ時点のスナップショットで、他のユニットの読み取りと整合させるため)。トランザクションがなければ、例外にする。
   */
  ConfigExportSet getExportableConfigSet();

  /**
   * config-import-export専用。取り込みの検証だけを行う(何も反映しない、BR9.10)。設定定義の誤り(BR1.1〜BR1.4)を、例外ではなく、全件を集めた一覧(位置=入力の中のリストの添え字を含む経路、i18nキー、パラメータ)で返す。
   * 誤りがなければ、空。
   */
  List<ImportValidationError> validateConfigSet(ConfigNaturalKeySet configSet);

  /**
   * config-import-export専用。取り込みを反映する(全置換: 自然キーで既存の項目と照合し、ファイルにない項目を削除し、既存の項目は内部IDを維持して更新する。{@code
   * isPrimaryKey}は維持する。BR9.9・BR9.14)。 伝播は{@code
   * MANDATORY}で、自身ではコミットせず、キャッシュに触れない。戻り値に、セクションごとの件数と、確定後の動作({@code PostCommit}:
   * 無効化・個別の変更イベントの発行)を含める。 事前に{@link #validateConfigSet}に合格していること(合格していない入力の結果は、保証しない)。
   */
  ApplyResult applyConfigSet(ConfigNaturalKeySet configSet);
}
