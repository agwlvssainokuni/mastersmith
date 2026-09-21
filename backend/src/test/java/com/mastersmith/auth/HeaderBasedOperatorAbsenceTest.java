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

package com.mastersmith.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ヘッダー方式の暫定の操作者取得が、どこにも残っていないことの確認(アーキテクチャテスト、authentication-serviceの機能設計
 * W7・Q9=A、code-generation-plan.md Step 13): {@code X-User-Id}・{@code
 * X-Active-Role-Id}を読むコードがなく、{@code HeaderCurrentOperatorProvider}・{@code
 * HeaderActiveRoleResolver}・{@code CurrentOperatorProvider}・ {@code ActiveRoleResolver}・{@code
 * usermanagement.security.Operator}のクラスが、アプリケーションのソースにも、クラスパスにもないこと。ヘッダーを信頼する経路は、どのプロファイルにも残さない。
 *
 * <p>ソースの検査は、コメント(「読まない」という説明のJavadocなど)を除いた、コードの部分に対して行う。
 */
class HeaderBasedOperatorAbsenceTest {

  private static final Path MAIN_SOURCES = Path.of("src/main/java");
  private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
  private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

  private static String codeOf(Path file) throws IOException {
    String source = Files.readString(file);
    return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll(" ")).replaceAll(" ");
  }

  private static List<Path> javaSources() throws IOException {
    try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
      return files.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }

  @Test
  void theSourceTreeIsFoundSoTheScansBelowAreMeaningful() throws IOException {
    assertThat(javaSources()).hasSizeGreaterThan(100);
  }

  @ParameterizedTest(name = "コードに{0}が現れない")
  @ValueSource(
      strings = {
        "X-User-Id",
        "X-Active-Role-Id",
        "HeaderCurrentOperatorProvider",
        "HeaderActiveRoleResolver",
        "CurrentOperatorProvider",
        "ActiveRoleResolver",
        "usermanagement.security.Operator"
      })
  void theHeaderBasedTemporaryOperatorLookupLeavesNoTraceInTheApplicationCode(String forbidden)
      throws IOException {
    List<String> offenders = new ArrayList<>();
    for (Path source : javaSources()) {
      if (codeOf(source).toLowerCase().contains(forbidden.toLowerCase())) {
        offenders.add(source.toString());
      }
    }

    assertThat(offenders).isEmpty();
  }

  @Test
  void theHeadersAreNotReferencedInTheResourcesEither() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN_RESOURCES)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        String name = file.toString();
        if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")) {
          String content = Files.readString(file).toLowerCase();
          if (content.contains("x-user-id") || content.contains("x-active-role-id")) {
            offenders.add(name);
          }
        }
      }
    }

    assertThat(offenders).isEmpty();
  }

  @ParameterizedTest(name = "クラス{0}がクラスパスにない")
  @ValueSource(
      strings = {
        "com.mastersmith.usermanagement.security.HeaderCurrentOperatorProvider",
        "com.mastersmith.usermanagement.security.CurrentOperatorProvider",
        "com.mastersmith.usermanagement.security.Operator",
        "com.mastersmith.schema.security.HeaderActiveRoleResolver",
        "com.mastersmith.schema.security.ActiveRoleResolver"
      })
  void theTemporaryClassesAreGone(String className) {
    assertThatThrownBy(() -> Class.forName(className)).isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void theOnlySourceOfTheOperatorIsTheNeutralSharedContract() throws Exception {
    // 共通基盤の契約(C15)が、存在する。
    assertThat(Class.forName("com.mastersmith.common.security.Operator")).isNotNull();
    assertThat(Class.forName("com.mastersmith.common.security.OperatorContext")).isInterface();
  }

  @Test
  void otherUnitsDependOnlyOnTheSharedContractAndNeverOnTheAuthenticationServiceComponents()
      throws IOException {
    // U2・U4・U6・U7・U3からの依存は、common.securityへの読み取りだけ(com.mastersmith.authへは依存しない)。
    // 例外: C14(SessionContextApi)は、list-engine・record-edit-engine(まだ未実装)が使う、認証サービスの契約であり、
    // 現在の他ユニットのソースは、com.mastersmith.authを一切importしない。
    List<String> offenders = new ArrayList<>();
    for (Path source : javaSources()) {
      String path = source.toString().replace('\\', '/');
      boolean otherUnit =
          path.contains("/com/mastersmith/schema/")
              || path.contains("/com/mastersmith/usermanagement/")
              || path.contains("/com/mastersmith/menu/")
              || path.contains("/com/mastersmith/audit/")
              || path.contains("/com/mastersmith/permission/");
      if (otherUnit
          && Pattern.compile("import\\s+com\\.mastersmith\\.auth\\.")
              .matcher(codeOf(source))
              .find()) {
        offenders.add(path);
      }
    }

    assertThat(offenders).isEmpty();
  }
}
