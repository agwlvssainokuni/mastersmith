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

package com.mastersmith.usermanagement.mail;

import com.mastersmith.usermanagement.exception.SubjectRejectedException;
import com.mastersmith.usermanagement.exception.SubjectRejectedException.Rejection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * レンダリング後のHTMLの{@code <title>}要素の値から、招待メールの件名を取り出す(security-design.md NFR2.7)。HTMLパーサは使わず、
 * 自前の簡易処理で取り出す。
 *
 * <p>文字参照(数値参照・主要な名前つき参照)をデコードしたうえで、デコード後に制御文字(CR/LFなど)を含む場合、200文字を超える場合、 {@code
 * <title>}が無い・空の場合、および未対応の名前つき参照・不正な数値参照を含む場合は、除去せずに{@link
 * SubjectRejectedException}で拒否する(ヘッダーインジェクションの防止)。未対応の参照を拒否するのは、デコードの漏れを、起動時のテンプレートの検証で 検知するため。
 */
@Component
public class SubjectExtractor {

  /** 件名の最大文字数(Unicodeコードポイント数)。 */
  public static final int MAX_LENGTH = 200;

  private static final Pattern TITLE =
      Pattern.compile(
          "<title(?:\\s[^>]*)?>(.*?)</title\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern REFERENCE =
      Pattern.compile("&(?:#([0-9]+)|#[xX]([0-9a-fA-F]+)|([A-Za-z][A-Za-z0-9]*));");

  private static final Map<String, String> NAMED_REFERENCES =
      Map.ofEntries(
          Map.entry("amp", "&"),
          Map.entry("lt", "<"),
          Map.entry("gt", ">"),
          Map.entry("quot", "\""),
          Map.entry("apos", "'"),
          Map.entry("nbsp", String.valueOf((char) 0xA0)),
          Map.entry("copy", "©"),
          Map.entry("reg", "®"),
          Map.entry("trade", "™"),
          Map.entry("hellip", "…"),
          Map.entry("mdash", "—"),
          Map.entry("ndash", "–"),
          Map.entry("lsquo", "‘"),
          Map.entry("rsquo", "’"),
          Map.entry("ldquo", "“"),
          Map.entry("rdquo", "”"),
          Map.entry("laquo", "«"),
          Map.entry("raquo", "»"),
          Map.entry("middot", "·"),
          Map.entry("bull", "•"),
          Map.entry("times", "×"),
          Map.entry("yen", "¥"),
          Map.entry("euro", "€"));

  /**
   * 件名を取り出す。
   *
   * @throws SubjectRejectedException 件名を拒否する場合(理由は{@link Rejection})
   */
  public String extract(String html) {
    Matcher title = TITLE.matcher(html == null ? "" : html);
    if (!title.find()) {
      throw new SubjectRejectedException(Rejection.MISSING_TITLE);
    }
    String decoded = decodeReferences(title.group(1));
    if (containsControlCharacter(decoded)) {
      throw new SubjectRejectedException(Rejection.CONTROL_CHARACTER);
    }
    String subject = decoded.strip();
    if (subject.isEmpty()) {
      throw new SubjectRejectedException(Rejection.EMPTY_TITLE);
    }
    if (subject.codePointCount(0, subject.length()) > MAX_LENGTH) {
      throw new SubjectRejectedException(Rejection.TOO_LONG);
    }
    return subject;
  }

  private static String decodeReferences(String text) {
    Matcher matcher = REFERENCE.matcher(text);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String replacement;
      if (matcher.group(1) != null) {
        replacement = codePointToString(matcher.group(1), 10);
      } else if (matcher.group(2) != null) {
        replacement = codePointToString(matcher.group(2), 16);
      } else {
        replacement = NAMED_REFERENCES.get(matcher.group(3));
        if (replacement == null) {
          throw new SubjectRejectedException(Rejection.UNSUPPORTED_REFERENCE);
        }
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static String codePointToString(String digits, int radix) {
    int codePoint;
    try {
      codePoint = Integer.parseInt(digits, radix);
    } catch (NumberFormatException e) {
      throw new SubjectRejectedException(Rejection.UNSUPPORTED_REFERENCE);
    }
    boolean surrogate = codePoint >= 0xD800 && codePoint <= 0xDFFF;
    if (codePoint <= 0 || codePoint > Character.MAX_CODE_POINT || surrogate) {
      throw new SubjectRejectedException(Rejection.UNSUPPORTED_REFERENCE);
    }
    return new String(Character.toChars(codePoint));
  }

  private static boolean containsControlCharacter(String text) {
    return text.codePoints()
        .anyMatch(cp -> Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029);
  }
}
