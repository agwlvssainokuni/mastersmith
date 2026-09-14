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

package com.mastersmith.dataio;

import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.dataio.dto.ImportResult;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * data-import-export(U8)が提供する内部Javaインタフェース契約 (inception/contract-design/contract-summary.md
 * C13: DataImportExportApi)。
 *
 * <p>consumers: list-engine(exportCsv)、record-edit-engine(importCsv)。DataImportExportは
 * config-engine(U1)のみに依存し、権限は呼び出し元が事前検証済みであることを前提に再検証しない
 * (functional-design/rules.md BR8.8)。
 *
 * <p><b>C13契約からのexportCsvシグネチャ上の差異</b>: contract-summary.mdはexportCsvの戻り値を{@code
 * InputStream (CSV)}と記す。しかしnfr-design/performance-design.mdは、BR8.10(ストリーミング処理、全件を
 * メモリへ読み込まない)を実現するため、業務データ用RDBMSのカーソルから読み取った行を都度呼び出し元の
 * {@code OutputStream}(例: {@code HttpServletResponse}の出力ストリーム)へ直接書き込む方式を明示的に選定した
 * (「HttpServletResponseの出力ストリームへ直接ストリーミングする」)。本インタフェースはこの性能設計を
 * 具体化し、CSVデータを保持する新規{@code InputStream}を返す代わりに、呼び出し元が渡す{@code OutputStream}
 * へ書き込む方式を採用する(戻り値でCSVバイト列を返す設計は、全件をメモリ上に保持するか別スレッドで
 * パイプ処理するかのいずれかを要求し、BR8.10の制約と両立しないため)。この差異はC13契約の加法的な実装詳細
 * であり、業務ロジック(BR8.1〜BR8.10)そのものには影響しない。
 */
public interface DataImportExportApi {

  /**
   * 業務データCSVエクスポート(FR12.1、BR8.1, BR8.2, BR8.8, BR8.10)。
   *
   * @param tableConfigId エクスポート対象テーブル(config-engineのTableConfig)を指すID
   * @param filter 一覧画面の現在の検索条件(nullable、動的スキーマ)
   * @param sort 一覧画面の現在のソート順(nullable)
   * @param permittedColumnNames list-engineが事前にPermissionEngineへ問い合わせ済みの、実効READ権限を持つ列名一覧
   * @param out 生成したCSVの書き込み先(呼び出し元が用意する出力ストリーム。本メソッドはクローズしない)
   * @throws TableConfigNotFoundException 対象テーブルのTableConfigが存在しない場合
   */
  void exportCsv(
      String tableConfigId,
      Map<String, Object> filter,
      String sort,
      List<String> permittedColumnNames,
      OutputStream out);

  /**
   * 業務データCSVインポート(FR12.1、BR8.3〜BR8.7, BR8.9, BR8.10)。
   *
   * @param tableConfigId インポート対象テーブル(config-engineのTableConfig)を指すID
   * @param file アップロードされたCSVファイル
   * @param actor インポートを実行した利用者のユーザーID(ImportExecutedEvent.actorの出所)
   * @return 成功件数とエラー一覧
   * @throws TableConfigNotFoundException 対象テーブルのTableConfigが存在しない場合
   */
  ImportResult importCsv(String tableConfigId, InputStream file, String actor);
}
