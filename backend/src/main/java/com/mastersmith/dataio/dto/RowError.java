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

/**
 * CSVインポートの行単位バリデーションエラー(rules.md BR8.6、entities.md CsvImportRowResult.errors)。
 *
 * <p>BR8.6は「行番号・フィールド単位のエラーメッセージ」を要求する。entities.mdの{@code CsvImportRowResult.errors}は{@code {field,
 * message}}の形状を定める一方、C13契約 (contract-summary.md)の{@code DataImportExportApi.importCsv}が返す{@code
 * ImportResult.errors: List<RowError>}は{@code RowError { row: int, message: string }}と定める。本レコードは両者を
 * 満たす上位互換の形状({@code row}・{@code field}・{@code message}の3項目)として1つに統合する
 * (contract-summary.mdの契約所有ルール「加法的変更(オプションフィールド追加等)は、コンシューマーが
 * 未知のフィールドを無視することを前提に、プロバイダー単独の判断で行ってよい」に基づく、フィールド単位の 詳細情報を保持するための追加)。
 *
 * @param row CSVファイル上の行番号(ヘッダー行を除く、1始まり)
 * @param field 違反したフィールド(列)名
 * @param message エラー内容を表す機械可読な識別子(例: "required", "typeMismatch", "maxLength")
 */
public record RowError(int row, String field, String message) {

  public RowError {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must not be blank");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }
}
