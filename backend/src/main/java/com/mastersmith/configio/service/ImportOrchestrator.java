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

package com.mastersmith.configio.service;

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.mapper.ConfigDocumentMapper;
import com.mastersmith.configio.mapper.NaturalKeyIndex;
import com.mastersmith.configio.parser.ImportErrorCollector;
import com.mastersmith.configio.validation.ReferenceValidator;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.permission.PermissionEngineApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 3ユニット(config-engine・menu-navigation・permission-engine)の、検証専用メソッドと反映するメソッドを呼ぶ(config-import-export
 * BR9.10〜BR9.12、logical-components.md)。 呼び出し元の{@code REPEATABLE_READ}のトランザクションの中で呼ぶこと。
 *
 * <ul>
 *   <li>{@link #validate}:
 *       参照整合(BR9.13)と、3ユニットの検証専用メソッド(何も反映しない。権限昇格・主権限0件を含む)を呼び、誤りを、位置つきで、全件集める。自然キーは、現在の環境の内部IDへ解決する
 *       (既存の項目はその内部ID、新規の項目はnull=仮の識別。BR9.14)。
 *   <li>{@link #apply}:
 *       反映の順序は、schema(テーブル・カラム・翻訳)→メニュー→RBAC(ロール・グループ→権限)。メニューはテーブルを、権限はロールとテーブル・カラムを参照するため、schemaを最初に反映し、
 *       その結果(新しい内部ID)から、メニューの遷移先・権限の対象を解決し直す。各ユニットが、段階ごとに{@code flush()}する。
 * </ul>
 *
 * <p>業務固有の名前を、コードに持たない。名前・値の意味の検証は、所有するユニットに委ねる(BR9.20)。
 */
@Component
public class ImportOrchestrator {

  private final ConfigEngineApi configEngineApi;
  private final MenuStructureApi menuStructureApi;
  private final PermissionEngineApi permissionEngineApi;
  private final ConfigDocumentMapper mapper;
  private final ReferenceValidator referenceValidator;

  public ImportOrchestrator(
      ConfigEngineApi configEngineApi,
      MenuStructureApi menuStructureApi,
      PermissionEngineApi permissionEngineApi,
      ConfigDocumentMapper mapper,
      ReferenceValidator referenceValidator) {
    this.configEngineApi = configEngineApi;
    this.menuStructureApi = menuStructureApi;
    this.permissionEngineApi = permissionEngineApi;
    this.mapper = mapper;
    this.referenceValidator = referenceValidator;
  }

  /** 何も反映せず、参照整合と3ユニットの検証の誤りを、{@code errors}へ集める。 */
  public void validate(
      ConfigDocument document, ImportContext context, ImportErrorCollector errors) {
    referenceValidator.validate(document, permissionEngineApi::isReservedSchemaName, errors);
    NaturalKeyIndex index = NaturalKeyIndex.from(configEngineApi.getExportableConfigSet());
    errors.addAll("schema", configEngineApi.validateConfigSet(mapper.toNaturalKeySet(document)));
    // メニューの誤りの位置は、入力が持つ位置(ファイルの中のJSON上の位置)そのままである。
    errors.addAll("", menuStructureApi.validateMenuStructure(mapper.toMenuItems(document, index)));
    errors.addAll(
        "rbac",
        permissionEngineApi.validateRbacImport(
            mapper.toRbacImportSet(document, index),
            context.operatorActiveRoleId(),
            context.bootstrapAtStart()));
  }

  /** すべての検証に合格したあとに、1つのトランザクションの中で、schema→メニュー→RBACの順に反映する。 */
  public AppliedImport apply(ConfigDocument document, ImportContext context) {
    ApplyResult schema = configEngineApi.applyConfigSet(mapper.toNaturalKeySet(document));
    // schemaの反映の結果(新しい内部ID)から、メニューの遷移先・権限の対象を解決し直す。
    NaturalKeyIndex index = NaturalKeyIndex.from(configEngineApi.getExportableConfigSet());
    ApplyResult menu = menuStructureApi.applyMenuStructure(mapper.toMenuItems(document, index));
    ApplyResult rbac =
        permissionEngineApi.applyRbacImport(
            mapper.toRbacImportSet(document, index), context.operatorActiveRoleId());

    Map<String, SectionCounts> merged = new LinkedHashMap<>();
    for (String section : ImportSections.ALL) {
      for (ApplyResult result : List.of(schema, menu, rbac)) {
        if (result.sections().containsKey(section)) {
          merged.put(section, result.sections().get(section));
        }
      }
    }
    List<PostCommit> postCommits = new ArrayList<>(3);
    postCommits.add(schema.postCommit());
    postCommits.add(menu.postCommit());
    postCommits.add(rbac.postCommit());
    return new AppliedImport(merged, postCommits);
  }
}
