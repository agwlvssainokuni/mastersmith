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

import java.util.List;

/**
 * 取り込みの結果({@code ImportResult})・監査イベントの件数のセクション名(config-import-export BR9.17)。表示・記録の順序は、{@link
 * #ALL}のとおり。
 */
public final class ImportSections {

  public static final String SCHEMA = "schema";
  public static final String TRANSLATIONS = "translations";
  public static final String MENU = "menu";
  public static final String ROLES = "roles";
  public static final String GROUPS = "groups";
  public static final String PRIMARY_PERMISSIONS = "primaryPermissions";
  public static final String AUXILIARY_PERMISSIONS = "auxiliaryPermissions";

  public static final List<String> ALL =
      List.of(
          SCHEMA, TRANSLATIONS, MENU, ROLES, GROUPS, PRIMARY_PERMISSIONS, AUXILIARY_PERMISSIONS);

  private ImportSections() {}
}
