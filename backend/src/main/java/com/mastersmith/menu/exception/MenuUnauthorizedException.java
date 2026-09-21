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
 * {@link com.mastersmith.common.security.OperatorContext}が操作者を解決できなかった場合の例外(401、C3契約)。
 *
 * <p>{@code GET /api/menu}・{@code POST/PUT/DELETE /api/menu-items}のいずれもこの例外を用いる
 * (code-generation-plan.md「前提事項2」: C3契約は{@code GET /api/menu}に401のみを宣言し、403は宣言していない。BR6.3の権限フィルタは
 * 「除外」でありエラーではないため)。
 */
public class MenuUnauthorizedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MenuUnauthorizedException(String message) {
    super(message);
  }
}
