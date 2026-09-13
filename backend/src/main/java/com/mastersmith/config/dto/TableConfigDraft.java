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

package com.mastersmith.config.dto;

import com.mastersmith.config.rdbms.RdbmsDialect;
import java.util.List;

/**
 * schema-introspectorが対象RDBMSから読み取ったスキーマ全体のドラフト(C9契約 writeTableConfigDraftの引数型、functional-spec.md
 * W2)。
 *
 * <p>{@link #tables()}は対象RDBMS上の複数テーブルを含みうる。テーブルごとに、 既存TableConfigが存在する場合は取り込みをスキップする(rules.md
 * BR1.8)。
 *
 * @param dialect 対象RDBMSの方言
 * @param tables 読み取られた各テーブルのメタデータ
 */
public record TableConfigDraft(RdbmsDialect dialect, List<TableDraftEntry> tables) {

  public TableConfigDraft {
    tables = tables == null ? List.of() : List.copyOf(tables);
  }
}
