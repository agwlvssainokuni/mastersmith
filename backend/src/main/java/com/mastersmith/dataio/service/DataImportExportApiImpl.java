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

package com.mastersmith.dataio.service;

import com.mastersmith.dataio.DataImportExportApi;
import com.mastersmith.dataio.config.BusinessDataSourceConfig;
import com.mastersmith.dataio.dto.ImportResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * {@link DataImportExportApi}(C13契約)の実装。エクスポートは{@link CsvExportService}へ、インポートは{@link
 * CsvImportService}へそれぞれ委譲する薄いファサード。
 *
 * <p>両サービスは、それぞれ独立した業務ロジック単位(BR8.1〜BR8.2/BR8.8/BR8.10のエクスポート系、BR8.3〜BR8.7/BR8.9/BR8.10の
 * インポート系)としてテスト容易性を優先し個別の{@code @Service}に分割しているため、外部公開契約である{@link DataImportExportApi}は
 * 本クラスが1つにまとめて実装する。
 *
 * <p>{@link CsvImportService}(ひいては{@link BusinessDataSourceConfig})と同一のプロパティで条件付き登録する。
 */
@Service
@ConditionalOnProperty(prefix = "mastersmith.business-datasource", name = "enabled", havingValue = "true")
public class DataImportExportApiImpl implements DataImportExportApi {

  private final CsvExportService csvExportService;
  private final CsvImportService csvImportService;

  public DataImportExportApiImpl(CsvExportService csvExportService, CsvImportService csvImportService) {
    this.csvExportService = csvExportService;
    this.csvImportService = csvImportService;
  }

  @Override
  public void exportCsv(
      String tableConfigId,
      Map<String, Object> filter,
      String sort,
      List<String> permittedColumnNames,
      OutputStream out) {
    csvExportService.exportCsv(tableConfigId, filter, sort, permittedColumnNames, out);
  }

  @Override
  public ImportResult importCsv(String tableConfigId, InputStream file, String actor) {
    return csvImportService.importCsv(tableConfigId, file, actor);
  }
}
