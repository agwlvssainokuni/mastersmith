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

package com.mastersmith.configio.web;

import com.mastersmith.common.security.Operator;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.service.ConfigExportService;
import com.mastersmith.configio.service.ConfigImportService;
import com.mastersmith.configio.service.ImportResult;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * C7(config-import-export REST API、FR11)のエンドポイント。
 *
 * <ul>
 *   <li>{@code GET /api/config/export}: 設定一式のJSONを、200で返す。{@code Content-Disposition}で、保存名({@code
 *       mastersmith-config-<書き出し日時>.json})を示す(BR9.2)。
 *   <li>{@code POST /api/config/import}: 設定一式のJSONを取り込む。リクエスト本体は、{@code @RequestBody
 *       JsonNode}(Jackson 3。位置つきの誤りを、全件集めるため。 Q3=B)。成功は200と、セクションごとの件数(BR9.17)。
 * </ul>
 *
 * <p>認可(401・403)は、サーバー側で、必ず、最初に行う({@link ConfigImportAuthorizer})。{@code
 * consumes}は宣言しない(マッピングの段階の415が、専用の例外ハンドラーに届かないため。束縛の失敗は、 {@link
 * ConfigImportExceptionHandler}が、認可の後に、422として返す)。例外は、{@link ConfigImportExceptionHandler}が、RFC
 * 9457のProblemDetailsに変換する。
 */
@RestController
public class ConfigImportExportController {

  private final ConfigImportAuthorizer authorizer;
  private final ConfigExportService exportService;
  private final ConfigImportService importService;

  public ConfigImportExportController(
      ConfigImportAuthorizer authorizer,
      ConfigExportService exportService,
      ConfigImportService importService) {
    this.authorizer = authorizer;
    this.exportService = exportService;
    this.importService = importService;
  }

  @GetMapping("/api/config/export")
  public ResponseEntity<ConfigDocument> export() {
    Operator operator = authorizer.authorize();
    ConfigDocument document = exportService.export(operator);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(fileName(document.exportedAt()))
                .build()
                .toString())
        .body(document);
  }

  @PostMapping("/api/config/import")
  public ResponseEntity<ImportResultResponse> importConfig(@RequestBody JsonNode body) {
    Operator operator = authorizer.authorize();
    ImportResult result = importService.importConfig(body, operator);
    return ResponseEntity.ok(ImportResultResponse.from(result));
  }

  /**
   * 保存名: {@code mastersmith-config-<書き出し日時>.json}(日時は、ISO 8601のUTCから、区切りの{@code -}・{@code
   * :}を除いた、{@code yyyyMMddTHHmmssZ})。
   */
  static String fileName(String exportedAt) {
    return "mastersmith-config-" + exportedAt.replace("-", "").replace(":", "") + ".json";
  }
}
