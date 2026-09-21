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

package com.mastersmith.common.configio;

import java.util.Map;
import java.util.Objects;

/**
 * 設定の取り込みの検証で見つかった誤り1件(各ユニットの検証専用メソッドの戻り値、config-import-export entities.md
 * ImportError)。検証の誤りは、例外ではなく、この一覧として返す(全件を集めるため。logical-components.md 追補4)。
 *
 * <p>入力値そのもの(ファイルの内容)・スタックトレース・内部の型名は含めない。位置とi18nキーと、必要な場合だけの補助の値(項目の名前・許容値・上限など、内容そのものではない値)からなる。
 *
 * @param field 位置。各ユニットが受け取った入力の中での位置(リストの添え字を含む経路。例 {@code
 *     tables[3].columns[2].editorType})。メニューの検証は、呼び出し元が与えた位置の文字列を、そのまま用いる
 * @param message 翻訳用のメッセージキー(i18nキー)
 * @param params メッセージに埋め込む値(なければ空)
 */
public record ImportValidationError(String field, String message, Map<String, Object> params) {

  public ImportValidationError {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(message, "message");
    params = params == null ? Map.of() : Map.copyOf(params);
  }

  public static ImportValidationError of(String field, String message) {
    return new ImportValidationError(field, message, Map.of());
  }

  public static ImportValidationError of(String field, String message, Map<String, Object> params) {
    return new ImportValidationError(field, message, params);
  }

  /** 位置の先頭に、接頭辞(セクション名など)を付けた、新しい誤りを返す。 */
  public ImportValidationError withFieldPrefix(String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      return this;
    }
    return new ImportValidationError(
        field.isEmpty() ? prefix : (field.startsWith("[") ? prefix + field : prefix + "." + field),
        message,
        params);
  }
}
