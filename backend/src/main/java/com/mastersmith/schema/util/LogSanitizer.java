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

package com.mastersmith.schema.util;

import java.util.List;

/**
 * クライアントが指定した値(schemaName・tableNamesなど)をログ・例外メッセージへ出す前に、制御文字を無害化する(ログインジェクション対策、レビュー指摘R-07)。
 *
 * <p>改行などの制御文字・行区切り・書式制御文字(双方向制御など)は、ログの行を偽装できるため、{@code \}{@code
 * uXXXX}形式の可視の文字列に置き換える。それ以外の文字(日本語の識別子を含む)はそのまま残す。
 */
public final class LogSanitizer {

  private LogSanitizer() {}

  /** 制御文字などを可視のエスケープ({@code \}{@code uXXXX})に置き換えた文字列を返す。nullはnullのまま返す。 */
  public static String clean(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder sanitized = new StringBuilder(value.length());
    value
        .codePoints()
        .forEach(
            codePoint -> {
              if (isUnsafe(codePoint)) {
                sanitized.append(String.format("\\u%04x", codePoint));
              } else {
                sanitized.appendCodePoint(codePoint);
              }
            });
    return sanitized.toString();
  }

  /** 各要素を{@link #clean(String)}した一覧を返す。nullはnullのまま返す。 */
  public static List<String> clean(List<String> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(LogSanitizer::clean).toList();
  }

  private static boolean isUnsafe(int codePoint) {
    if (Character.isISOControl(codePoint)) {
      return true;
    }
    int type = Character.getType(codePoint);
    return type == Character.LINE_SEPARATOR
        || type == Character.PARAGRAPH_SEPARATOR
        || type == Character.FORMAT;
  }
}
