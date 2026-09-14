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

import java.io.InputStream;

/**
 * CSVインポート実行時の内部パラメータ(entities.md CsvImportRequest)。record-edit-engineから
 * DataImportExportへの内部呼び出し専用であり、WEB API(C2)のリクエストボディには{@code actor}を公開しない。
 *
 * <p>永続化しない。呼び出しごとの一時的なリクエストパラメータ。
 *
 * @param tableConfigId インポート対象テーブル(config-engineのTableConfig)を指すID
 * @param file アップロードされたCSVファイル
 * @param actor インポートを実行した利用者のユーザーID(BR8.9のImportExecutedEvent.actorの出所)
 */
public record CsvImportRequest(String tableConfigId, InputStream file, String actor) {

  public CsvImportRequest {
    if (tableConfigId == null || tableConfigId.isBlank()) {
      throw new IllegalArgumentException("tableConfigId must not be blank");
    }
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("actor must not be blank");
    }
  }
}
