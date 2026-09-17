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

package com.mastersmith.menu.exception;

import java.io.Serial;

/**
 * {@code PermissionEngineApi#canAccessScreen(activeRoleId, "config-import-export")}が{@code
 * false}を返した場合の例外(403、BR6.8)。{@code GET /api/menu}はこの例外を用いない(前提事項2、BR6.3の除外フィルタとエラーは区別する)。
 */
public class MenuItemForbiddenException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MenuItemForbiddenException(String message) {
    super(message);
  }
}
