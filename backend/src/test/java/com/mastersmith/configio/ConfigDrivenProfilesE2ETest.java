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

package com.mastersmith.configio;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentNormalizer;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 設定駆動に特有の必須テスト(team.md Q8-b、code-generation-plan.md Step 18):
 * 「アプリ本体を1つのまま、設定を差し替えるだけで、複数の業務に転用できる」ことを、<b>業務ドメインの異なる、2つの設定プロファイル</b>(商品マスタ用・蔵書マスタ用)で、
 * <b>同じ操作シナリオ</b>(取り込み → エクスポート → 往復 → 変更して再取り込み →
 * 各ユニットが設定を解釈できること)を流して確認する(実際の組込みH2・実際の3ユニット・取り込みのAPI経由)。加えて、アプリケーションのコードに、
 * 業務固有の名前がないこと(BR9.20・NFR8.1)を、ソースの走査で確認する。
 */
class ConfigDrivenProfilesE2ETest extends ConfigIoIntegrationTestBase {

  @Autowired private ConfigEngineApi configEngineApi;
  @Autowired private MenuStructureApi menuStructureApi;
  @Autowired private PermissionEngineApi permissionEngineApi;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("transactionManager")
  private org.springframework.transaction.PlatformTransactionManager transactionManager;

  record Profile(
      String name, ConfigDocument document, String schemaName, String mainTable, String adminRole) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Arguments> profiles() {
    return Stream.of(
        Arguments.of(
            new Profile(
                "商品マスタ",
                withAdmin(ConfigDocumentFactory.productProfile(), "shop_admin"),
                "shop",
                "products",
                "shop_admin")),
        Arguments.of(
            new Profile(
                "蔵書マスタ",
                withAdmin(ConfigDocumentFactory.libraryProfile(), "librarian"),
                "library",
                "books",
                "librarian")));
  }

  /** プロファイルの管理者のロール(プロファイルが持つロール名)に、設定管理画面の権限(予約スキーマのFULL)を加える。 */
  private static ConfigDocument withAdmin(ConfigDocument document, String roleName) {
    List<ConfigDocument.PrimaryPermissionEntry> primary =
        new java.util.ArrayList<>(document.rbac().primaryPermissions());
    primary.add(
        new ConfigDocument.PrimaryPermissionEntry(
            roleName,
            new ConfigDocument.PermissionScope(
                ScopeType.SCHEMA, ConfigDocumentFactory.ADMIN_SCREEN_SCHEMA, null, null),
            PermissionLevel.FULL));
    return new ConfigDocument(
        document.formatVersion(),
        document.exportedAt(),
        document.appVersion(),
        document.schema(),
        document.menu(),
        new ConfigDocument.RbacSection(
            document.rbac().roles(),
            document.rbac().groups(),
            primary,
            document.rbac().auxiliaryPermissions()));
  }

  private MvcResult importAsAdmin(Profile profile, ConfigDocument document) throws Exception {
    actAs("profile-admin", profile.adminRole());
    return postImport(document);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profiles")
  void theSameScenarioWorksForEveryProfileJustByReplacingTheConfiguration(Profile profile)
      throws Exception {
    // (1) 初回の取り込み(ブートストラップ状態。操作者は、ロールを持たない初期管理者)。
    MvcResult first = postImport(profile.document());
    assertThat(first.getResponse().getStatus())
        .as(first.getResponse().getContentAsString())
        .isEqualTo(200);

    // (2) エクスポートは、取り込んだ設定と同じ(順序・無視される項目を除く)。
    actAs("profile-admin", profile.adminRole());
    ConfigDocument exported = exportDocument();
    assertThat(ConfigDocumentNormalizer.normalize(exported))
        .isEqualTo(ConfigDocumentNormalizer.normalize(profile.document()));

    // (3) 往復: 取り込み直しても、何も変わらない。
    MvcResult again = importAsAdmin(profile, exported);
    assertThat(again.getResponse().getStatus()).isEqualTo(200);
    JsonNode counts =
        ConfigDocumentFactory.JSON
            .readTree(again.getResponse().getContentAsString())
            .get("sections");
    for (String section :
        List.of(
            "schema",
            "translations",
            "roles",
            "groups",
            "primaryPermissions",
            "auxiliaryPermissions")) {
      assertThat(counts.get(section).toString())
          .as(section)
          .isEqualTo("{\"added\":0,\"updated\":0,\"deleted\":0}");
    }

    // (4) 各ユニットが、取り込んだ設定を、そのまま解釈できる(業務の名前を知らないコードが、設定の内容で動く)。
    TableConfig table = configEngineApi.getTableConfig(profile.schemaName(), profile.mainTable());
    assertThat(configEngineApi.getColumnConfigs(table.getTableConfigId())).hasSize(4);
    assertThat(exportMenuInTransaction()).isNotEmpty();
    String roleId = roleRepository.findByName(profile.adminRole()).orElseThrow().getRoleId();
    EffectivePermission effective =
        permissionEngineApi.resolveEffectivePermission(
            roleId, ScopeType.TABLE, table.getTableConfigId());
    assertThat(effective.level()).isEqualTo(PermissionLevel.FULL); // スキーマのFULLを、テーブルが継承する。
    assertThat(permissionEngineApi.canAccessScreen(roleId, "config-import-export")).isTrue();

    // (5) 設定を変えて(カラムを1つ除く)取り込み直すと、内部設定DBは、その内容に置き換わる。
    ConfigDocument changed = withoutLastColumn(profile.document(), profile.mainTable());
    MvcResult replaced = importAsAdmin(profile, changed);
    // 除いたカラムを、FKで、参照している(蔵書・商品のFK列)ため、除くカラムは、参照される側でない末尾の列(FK列)である。
    assertThat(replaced.getResponse().getStatus())
        .as(replaced.getResponse().getContentAsString())
        .isEqualTo(200);
    assertThat(
            configEngineApi.getColumnConfigs(
                configEngineApi
                    .getTableConfig(profile.schemaName(), profile.mainTable())
                    .getTableConfigId()))
        .hasSize(3);
  }

  private List<?> exportMenuInTransaction() {
    return new TransactionTemplate(transactionManager)
        .execute(status -> menuStructureApi.getExportableMenuStructure());
  }

  private static ConfigDocument withoutLastColumn(ConfigDocument document, String tableName) {
    List<ConfigDocument.TableEntry> tables =
        document.schema().tables().stream()
            .map(
                t -> {
                  if (!t.tableName().equals(tableName)) {
                    return t;
                  }
                  return new ConfigDocument.TableEntry(
                      t.schemaName(),
                      t.tableName(),
                      t.displayOrder(),
                      t.optimisticLockColumn(),
                      t.columns().subList(0, t.columns().size() - 1));
                })
            .toList();
    return new ConfigDocument(
        document.formatVersion(),
        document.exportedAt(),
        document.appVersion(),
        new ConfigDocument.SchemaSection(tables, document.schema().translations()),
        document.menu(),
        document.rbac());
  }

  @Test
  void bothProfilesProduceDifferentDatabasesFromTheSameApplicationWithoutAnyCodeChange()
      throws Exception {
    Profile product = (Profile) profiles().findFirst().orElseThrow().get()[0];
    Profile library = (Profile) profiles().skip(1).findFirst().orElseThrow().get()[0];

    assertThat(postImport(product.document()).getResponse().getStatus()).isEqualTo(200);
    ConfigDocument exportedProduct = exportDocumentAs(product);
    resetDatabase();
    operators.set("bootstrap-admin", null);
    assertThat(postImport(library.document()).getResponse().getStatus()).isEqualTo(200);
    ConfigDocument exportedLibrary = exportDocumentAs(library);

    assertThat(exportedProduct.schema().tables())
        .extracting(ConfigDocument.TableEntry::schemaName)
        .containsOnly("shop");
    assertThat(exportedLibrary.schema().tables())
        .extracting(ConfigDocument.TableEntry::schemaName)
        .containsOnly("library");
    assertThat(ConfigDocumentNormalizer.normalize(exportedProduct))
        .isNotEqualTo(ConfigDocumentNormalizer.normalize(exportedLibrary));
  }

  private ConfigDocument exportDocumentAs(Profile profile) throws Exception {
    actAs("profile-admin", profile.adminRole());
    return exportDocument();
  }

  // ---- BR9.20・NFR8.1: アプリケーションのコードに、業務固有の名前がない ----

  private static final Pattern BUSINESS_NAMES =
      Pattern.compile(
          "\\b(shop|products?|categories|library|books?|shelves|librarian|shop_admin|category_id|product_id|book_id|shelf_id)\\b");

  @Test
  void theApplicationCodeOfTheEngineLayerContainsNoBusinessSpecificNames() throws IOException {
    Path main = Path.of("src/main/java/com/mastersmith");
    List<Path> engineSources;
    try (Stream<Path> files = Files.walk(main)) {
      engineSources =
          files
              .filter(p -> p.toString().endsWith(".java"))
              .filter(
                  p -> {
                    String s = p.toString();
                    return s.contains("/configio/")
                        || s.contains("/common/configio/")
                        || s.contains("/config/transfer/")
                        || s.contains("/permission/rbacio/")
                        || s.contains("MenuStructureApiImpl")
                        || s.contains("ConfigImportExecutedEvent");
                  })
              .toList();
    }

    assertThat(engineSources).isNotEmpty();
    for (Path source : engineSources) {
      assertThat(BUSINESS_NAMES.matcher(Files.readString(source)).find())
          .as(source.toString())
          .isFalse();
    }
  }
}
