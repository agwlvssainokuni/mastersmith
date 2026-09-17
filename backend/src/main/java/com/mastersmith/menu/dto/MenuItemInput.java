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

package com.mastersmith.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * C3契約追補の{@code MenuItemInput}スキーマ(contract-summary.md)に対応する{@code POST/PUT
 * /api/menu-items}リクエストボディ(security-design.md「MenuItem CRUD APIの入力検証」)。
 *
 * <p>{@code label}の必須・空文字禁止、{@code order}の必須は本レコードのBean Validation注釈がコントローラ境界({@code
 * @Valid})で検証する。{@code parentMenuItemId}・{@code targetTableConfigId}の参照整合性検証(存在確認)はBean
 * Validationでは表現できないため、{@link com.mastersmith.menu.service.MenuItemCommandService}がアプリケーション層で行う。
 *
 * @param parentMenuItemId 親MenuItemのID。ルート直下の項目はnull
 * @param label 表示名(必須、空文字不可)
 * @param order 同一階層内での表示順(必須)
 * @param targetTableConfigId 遷移先TableConfigのID。フォルダ項目はnull
 */
public record MenuItemInput(
    String parentMenuItemId,
    @NotBlank String label,
    @NotNull Integer order,
    String targetTableConfigId) {}
