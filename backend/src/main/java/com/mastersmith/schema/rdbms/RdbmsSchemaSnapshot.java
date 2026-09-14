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

package com.mastersmith.schema.rdbms;

import com.mastersmith.config.rdbms.RdbmsDialect;
import com.mastersmith.schema.dto.RdbmsTableMetadata;
import java.util.List;

/**
 * {@link RdbmsMetadataReader}が1回のintrospection呼び出しで読み取った結果一式(方言判定(BR2.3) +
 * 対象範囲(BR2.2)の全テーブルのメタデータ)。entities.mdが定義するcontract型ではなく、config-engineのC9契約型{@code
 * TableConfigDraft}へ変換する前段の、reader実装内部の合成読み取りモデル(functional-spec.mdが技術詳細をCode
 * Generationに委ねた部分の具体化)。
 *
 * @param dialect 対象RDBMSの方言(BR2.3)
 * @param tables 読み取り対象範囲(BR2.2)の全テーブルのメタデータ
 */
public record RdbmsSchemaSnapshot(RdbmsDialect dialect, List<RdbmsTableMetadata> tables) {

  public RdbmsSchemaSnapshot {
    tables = tables == null ? List.of() : List.copyOf(tables);
  }
}
