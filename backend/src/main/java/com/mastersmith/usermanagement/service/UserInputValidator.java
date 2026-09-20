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

package com.mastersmith.usermanagement.service;

import com.mastersmith.usermanagement.entity.FontSize;
import com.mastersmith.usermanagement.entity.Theme;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.security.PasswordPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 入力の検証と正規化(rules.md BR4.15とその追補、BR4.2・BR4.3・BR4.5)。エラーは、フィールド単位のi18nキー({@code
 * user.validation.<field>.<rule>})で返し、入力値そのもの(パスワードなど)は含めない(NFR2.2・NFR7.2)。
 *
 * <p>各メソッドは、不備があれば、渡された{@code errors}に追加してnullを返す(呼び出し側が、まとめて{@link
 * com.mastersmith.usermanagement.exception.UserValidationException}で投げる)。
 */
public final class UserInputValidator {

  public static final int EMAIL_MAX_LENGTH = 254;
  public static final int NAME_MAX_LENGTH = 100;
  public static final int ROLE_ID_MAX_LENGTH = 255;

  private static final String KEY_PREFIX = "user.validation.";

  // local@domain(ASCIIのみ。ドメインは、ドットで区切られたラベルの並びで、ドットなしのホスト名も許す)。
  private static final Pattern EMAIL =
      Pattern.compile(
          "[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+"
              + "@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
              + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");

  private UserInputValidator() {}

  /** emailを、前後の空白を除去し、形式を検証したうえで、小文字へ正規化する(BR4.15)。 */
  public static String normalizeEmail(String email, List<UserFieldError> errors) {
    if (email == null || email.isBlank()) {
      errors.add(error("email", "email.required"));
      return null;
    }
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    if (normalized.length() > EMAIL_MAX_LENGTH) {
      errors.add(error("email", "email.tooLong", Map.of("max", EMAIL_MAX_LENGTH)));
      return null;
    }
    if (!EMAIL.matcher(normalized).matches()) {
      errors.add(error("email", "email.invalid"));
      return null;
    }
    return normalized;
  }

  /**
   * nameを検証し、前後の空白を除去する。必須(空白のみは不可)・最大100文字(Unicodeコードポイント数)・制御文字(CR/LFなど)を含まないこと
   * (BR4.15の追補)。制御文字は、空白の除去より前に、入力そのままの値で検査する。
   */
  public static String normalizeName(String name, List<UserFieldError> errors) {
    if (name == null || name.isBlank()) {
      errors.add(error("name", "name.required"));
      return null;
    }
    if (containsControlCharacter(name)) {
      errors.add(error("name", "name.controlCharacter"));
      return null;
    }
    String normalized = name.strip();
    if (normalized.codePointCount(0, normalized.length()) > NAME_MAX_LENGTH) {
      errors.add(error("name", "name.tooLong", Map.of("max", NAME_MAX_LENGTH)));
      return null;
    }
    return normalized;
  }

  /**
   * roleIdsの形式を検証し、重複を除く(順序は保つ、BR4.5の追補)。空白のみ・長すぎる要素はエラー。
   *
   * @param required trueの場合、nullをエラーとする(PUTは全置換)。falseの場合、nullは空
   */
  public static List<String> normalizeRoleIds(
      List<String> roleIds, boolean required, List<UserFieldError> errors) {
    if (roleIds == null) {
      if (required) {
        errors.add(error("roleIds", "roleIds.required"));
        return null;
      }
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    boolean valid = true;
    for (String roleId : roleIds) {
      if (roleId == null || roleId.isBlank()) {
        errors.add(error("roleIds", "roleIds.blank"));
        valid = false;
      } else if (roleId.strip().length() > ROLE_ID_MAX_LENGTH) {
        errors.add(error("roleIds", "roleIds.tooLong", Map.of("max", ROLE_ID_MAX_LENGTH)));
        valid = false;
      } else {
        unique.add(roleId.strip());
      }
    }
    return valid ? List.copyOf(new ArrayList<>(unique)) : null;
  }

  /** パスワードの長さ(8〜128、コードポイント数)を検証する。値はエラーに含めない(BR4.2・BR4.4)。 */
  public static boolean validatePassword(String password, List<UserFieldError> errors) {
    if (password == null || password.isEmpty()) {
      errors.add(error("password", "password.required"));
      return false;
    }
    if (!PasswordPolicy.isValidLength(password)) {
      errors.add(
          error(
              "password",
              "password.length",
              Map.of("min", PasswordPolicy.MIN_LENGTH, "max", PasswordPolicy.MAX_LENGTH)));
      return false;
    }
    return true;
  }

  /** テーマ。nullは、{@code required}がfalseなら既定値(light)、trueならエラー。許容値外はエラー。 */
  public static Theme parseTheme(String value, boolean required, List<UserFieldError> errors) {
    if (value == null) {
      return requiredOrDefault("theme", required, Theme.DEFAULT, errors);
    }
    return Theme.fromValue(value).orElseGet(() -> invalid("theme", errors));
  }

  /** フォントサイズ。nullは、{@code required}がfalseなら既定値(medium)、trueならエラー。 */
  public static FontSize parseFontSize(
      String value, boolean required, List<UserFieldError> errors) {
    if (value == null) {
      return requiredOrDefault("fontSize", required, FontSize.DEFAULT, errors);
    }
    return FontSize.fromValue(value).orElseGet(() -> invalid("fontSize", errors));
  }

  /** 表示言語。nullは、{@code required}がfalseなら既定値(ja)、trueならエラー。 */
  public static UiLocale parseLocale(String value, boolean required, List<UserFieldError> errors) {
    if (value == null) {
      return requiredOrDefault("locale", required, UiLocale.DEFAULT, errors);
    }
    return UiLocale.fromValue(value).orElseGet(() -> invalid("locale", errors));
  }

  private static <T> T requiredOrDefault(
      String field, boolean required, T defaultValue, List<UserFieldError> errors) {
    if (required) {
      errors.add(error(field, field + ".required"));
      return null;
    }
    return defaultValue;
  }

  private static <T> T invalid(String field, List<UserFieldError> errors) {
    errors.add(error(field, field + ".invalid"));
    return null;
  }

  /** 制御文字(CR/LFなど、{@link Character#isISOControl(int)})と、Unicodeの行区切り・段落区切りを含むか。 */
  static boolean containsControlCharacter(String text) {
    return text.codePoints()
        .anyMatch(cp -> Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029);
  }

  private static UserFieldError error(String field, String rule) {
    return UserFieldError.of(field, KEY_PREFIX + rule);
  }

  private static UserFieldError error(String field, String rule, Map<String, Object> params) {
    return UserFieldError.of(field, KEY_PREFIX + rule, params);
  }
}
