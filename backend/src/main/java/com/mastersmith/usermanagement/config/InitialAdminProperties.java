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

package com.mastersmith.usermanagement.config;

import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.security.PasswordPolicy;
import com.mastersmith.usermanagement.service.UserInputValidator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 初期管理者の設定({@code mastersmith.users.initial-admin.*}、rules.md BR4.7、security-design.md
 * NFR2.11)。リポジトリには、既定値も実値も置かず、
 * 環境変数等から注入する。未設定・パスワードの長さの違反(8〜128文字)・emailの形式不正は、プロパティのバインド時に検知して、起動を失敗させる (設定定義自体の誤りはfail
 * fastする、project.md Mandated)。エラーメッセージには、パスワードを含めない。
 *
 * @param email 初期管理者のemail。trim・小文字へ正規化される
 * @param password 初期管理者のパスワード(平文。{@link #toString()}に出力しない)
 * @param roleIds 直接付与するロールID(任意、未指定なら空)。roleIdの実在検証は行わない(ロールは、後続のRBAC設定インポートで定義されるため、作成時点では
 *     未定義でありうる、BR4.7)
 * @param name 初期管理者の氏名(任意、既定は{@code Administrator})
 */
@ConfigurationProperties("mastersmith.users.initial-admin")
public record InitialAdminProperties(
    String email,
    String password,
    @DefaultValue List<String> roleIds,
    @DefaultValue("Administrator") String name) {

  public InitialAdminProperties {
    List<UserFieldError> errors = new ArrayList<>();
    email = UserInputValidator.normalizeEmail(email, errors);
    name = UserInputValidator.normalizeName(name, errors);
    if (email == null) {
      throw new IllegalArgumentException(
          "mastersmith.users.initial-admin.email must be set to a valid email address");
    }
    if (!PasswordPolicy.isValidLength(password)) {
      throw new IllegalArgumentException(
          "mastersmith.users.initial-admin.password must be set and be %d to %d characters long"
              .formatted(PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH));
    }
    if (name == null) {
      throw new IllegalArgumentException(
          "mastersmith.users.initial-admin.name must be a non-empty single-line name");
    }
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String roleId : roleIds) {
      if (roleId != null && !roleId.isBlank()) {
        unique.add(roleId.strip());
      }
    }
    roleIds = List.copyOf(unique);
  }

  /** 認証情報(パスワード)を出力しない。 */
  @Override
  public String toString() {
    return "InitialAdminProperties{roleIds=%s}".formatted(roleIds);
  }
}
