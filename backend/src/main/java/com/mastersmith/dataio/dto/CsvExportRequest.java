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

package com.mastersmith.dataio.dto;

import java.util.List;
import java.util.Map;

/**
 * CSVエクスポート実行時の内部パラメータ(entities.md CsvExportRequest)。list-engineから
 * DataImportExportへの内部呼び出し専用であり、WEB API(C1)には公開しない。
 *
 * <p>永続化しない。呼び出しごとの一時的なリクエストパラメータ。
 *
 * @param tableConfigId エクスポート対象テーブル(config-engineのTableConfig)を指すID
 * @param filter 一覧画面の検索条件(nullable、動的スキーマ)
 * @param sort 一覧画面のソート順(nullable)
 * @param permittedColumnNames list-engineが事前にPermissionEngineへ問い合わせ済みの実効READ権限列名一覧(BR8.2, BR8.8)
 */
public record CsvExportRequest(
    String tableConfigId, Map<String, Object> filter, String sort, List<String> permittedColumnNames) {

  public CsvExportRequest {
    if (tableConfigId == null || tableConfigId.isBlank()) {
      throw new IllegalArgumentException("tableConfigId must not be blank");
    }
    permittedColumnNames = permittedColumnNames == null ? List.of() : List.copyOf(permittedColumnNames);
  }
}
