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
 * {@code DELETE /api/menu-items/{menuItemId}}が子孫MenuItemを持つ項目に対して呼び出された場合の例外(409、NFR4.4の既定方針)。
 *
 * <p>カスケード削除は行わず、管理者に先に子の削除・付け替えを求める(reliability-requirements.md「既定方針」、fail-safeな既定挙動)。
 */
public class MenuItemConflictException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MenuItemConflictException(String message) {
    super(message);
  }
}
