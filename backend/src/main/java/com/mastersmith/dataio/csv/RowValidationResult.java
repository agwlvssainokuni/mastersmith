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

package com.mastersmith.dataio.csv;

import com.mastersmith.dataio.dto.CsvImportRowResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link CsvRowValidator}の1行分の検証結果。{@link CsvImportRowResult}(entities.mdが定める公開的な
 * 行結果の形状)に加え、outcome=VALIDの場合のみ、コミット時にINSERT/UPDATEへ用いる型変換後の値 ({@code values: Map<String,
 * Object>})を保持する。この{@code convertedValues}は
 * nfr-design/performance-design.mdが定める「検証済みの行データ(変換後の値)」の実装上の担い手であり (同ドキュメントの{@code
 * ValidatedRow}概念に相当)、CsvImportServiceの一時バッファ(BR8.10)が
 * outcome=VALIDの行についてのみ保持し、コミット時のINSERT/UPDATEに用いる。
 *
 * @param report 行単位のバリデーション結果(rowNumber/outcome/operation/errors)
 * @param convertedValues outcomeがVALIDの場合の、列名をキーとした型変換後の値(INVALIDの場合は空)
 */
public record RowValidationResult(CsvImportRowResult report, Map<String, Object> convertedValues) {

  public RowValidationResult {
    convertedValues =
        convertedValues == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(convertedValues));
  }

  public boolean isValid() {
    return report.isValid();
  }
}
