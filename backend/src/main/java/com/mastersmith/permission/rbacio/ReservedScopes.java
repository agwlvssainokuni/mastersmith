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

package com.mastersmith.permission.rbacio;

import java.util.Map;

/**
 * 管理系画面の権限のための、予約スキーマ名(rules.md
 * BR3.15)。予約のscreenKeyと、対応する予約スキーマ名(主権限のscopeType=SCHEMAのscopeRef)を、1か所に持つ。
 * permission-engineだけが、この名前の意味を知る(config-import-exportは、解釈せず、{@code
 * PermissionEngineApi#isReservedSchemaName}で、問い合わせる。BR9.20)。
 */
public final class ReservedScopes {

  /** 予約スキーマ名の接頭辞。 */
  public static final String PREFIX = "__system__:";

  /** 予約のscreenKey → 予約スキーマ名。 */
  public static final Map<String, String> SCREEN_SCHEMAS =
      Map.of(
          "user-management", PREFIX + "user-management",
          "audit-log", PREFIX + "audit-log",
          "config-import-export", PREFIX + "config-import-export");

  private ReservedScopes() {}

  /** 指定の名前が、予約スキーマ名(接頭辞を持ち、かつ、既知の予約名)か。 */
  public static boolean isReservedSchemaName(String name) {
    return name != null && SCREEN_SCHEMAS.containsValue(name);
  }

  /** 予約スキーマ名の接頭辞を持つか(既知の名前かは問わない)。 */
  public static boolean hasReservedPrefix(String name) {
    return name != null && name.startsWith(PREFIX);
  }
}
