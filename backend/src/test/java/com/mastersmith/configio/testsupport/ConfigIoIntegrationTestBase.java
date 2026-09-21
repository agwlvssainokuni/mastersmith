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

package com.mastersmith.configio.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.common.security.TestOperatorContext;
import com.mastersmith.common.security.TestOperatorContextConfig;
import com.mastersmith.common.security.TestPermitAllSecurityConfig;
import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.parser.ConfigDocumentParser;
import com.mastersmith.configio.parser.ImportErrorCollector;
import com.mastersmith.permission.cache.PermissionCacheControl;
import com.mastersmith.permission.repository.RoleRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * config-import-exportの、実際の組込みH2・実際のトランザクション・実際の3ユニット・実際の監査ログでの、統合テストの基底(認証フィルタは通さず、{@link
 * TestOperatorContext}で操作者を供給する)。
 * 内部設定DBは、<b>各テストの前後に</b>初期化する(他のテストクラスが、空のRBAC設定(ブートストラップ状態)などを前提にするため、確定したデータを、残さない)。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@AutoConfigureMockMvc
@Import({TestOperatorContextConfig.class, TestPermitAllSecurityConfig.class})
public abstract class ConfigIoIntegrationTestBase {

  protected static final String EXPORT = "/api/config/export";
  protected static final String IMPORT = "/api/config/import";
  protected static final String ADMIN_ROLE = "role_admin";

  @Autowired protected MockMvc mockMvc;
  @Autowired protected TestOperatorContext operators;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ConfigCache configCache;
  @Autowired protected PermissionCacheControl permissionCacheControl;
  @Autowired protected RoleRepository roleRepository;
  @Autowired protected AuditLogEntryRepository auditRepository;
  @Autowired protected ConfigDocumentParser parser;

  @BeforeEach
  void resetBefore() {
    resetDatabase();
    operators.set("bootstrap-admin", null);
  }

  @AfterEach
  void resetAfter() {
    operators.clear();
    resetDatabase();
  }

  /** 設定に関わる表を空にし、キャッシュを無効にする(ユーザー・認証・監査ログは、対象外)。 */
  protected void resetDatabase() {
    for (String table :
        List.of(
            "menu_item",
            "primary_permission",
            "auxiliary_permission",
            "group_role",
            "group_membership",
            "permission_group",
            "role",
            "column_config",
            "table_config",
            "translation_entry")) {
      jdbc.update("delete from " + table);
    }
    configCache.invalidate();
    permissionCacheControl.invalidate();
  }

  protected MvcResult postImport(JsonNode body) throws Exception {
    return mockMvc
        .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andReturn();
  }

  protected MvcResult postImport(ConfigDocument document) throws Exception {
    return postImport(ConfigDocumentFactory.toJson(document));
  }

  protected MvcResult getExport() throws Exception {
    return mockMvc.perform(get(EXPORT)).andReturn();
  }

  /** エクスポートの応答を、設定ファイルの値オブジェクトに戻す(取り込みと同じパーサー。形式の契約の確認を兼ねる)。 */
  protected ConfigDocument exportDocument() throws Exception {
    MvcResult result = getExport();
    if (result.getResponse().getStatus() != 200) {
      throw new IllegalStateException("export failed: " + result.getResponse().getStatus());
    }
    JsonNode json = ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
    ImportErrorCollector errors = new ImportErrorCollector();
    ConfigDocument document = parser.parse(json, errors);
    if (errors.hasErrors()) {
      throw new IllegalStateException("exported document is not importable: " + errors.errors());
    }
    return document;
  }

  /** 指定の操作者(ユーザーID)の、設定の取り込みの監査ログ(新しい順)。 */
  protected List<AuditLogEntry> importAuditEntriesOf(String userId) {
    return auditRepository
        .findAll(PageRequest.of(0, 2000, Sort.by(Sort.Direction.DESC, "occurredAt")))
        .stream()
        .filter(
            e ->
                "ConfigImportExport".equals(e.getTargetType()) && userId.equals(e.getActorUserId()))
        .toList();
  }

  /** 名前のロールのIDで、操作者のアクティブロールに切り替える。 */
  protected void actAs(String userId, String roleName) {
    String roleId = roleRepository.findByName(roleName).orElseThrow().getRoleId();
    operators.set(userId, roleId);
  }
}
