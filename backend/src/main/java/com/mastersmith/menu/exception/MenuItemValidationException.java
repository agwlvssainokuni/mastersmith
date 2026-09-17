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
 * {@code POST/PUT /api/menu-items}のリクエストボディが、参照整合性の観点で不正な場合の例外(400、entities.md制約)。
 *
 * <p>{@code parentMenuItemId}が指す既存MenuItemが存在しない場合、{@code targetTableConfigId}が指すTableConfigが
 * ConfigEngine側に存在しない場合、および{@code parentMenuItemId}が自分自身を指す場合(木構造の循環を防ぐための防御的な追加検証)に送出する。
 * ラベル必須・orderの型といった構造的な検証はBean Validation({@code @Valid})がコントローラ境界で担う。
 */
public class MenuItemValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MenuItemValidationException(String message) {
    super(message);
  }
}
