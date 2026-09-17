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

import java.util.List;

/**
 * {@code GET /api/menu}のレスポンスボディ(C3契約、BR6.1・BR6.5)。
 *
 * <p>権限のあるメニューが1件もない場合も、businessMenu・adminMenuの両方を空配列としてそのまま返す(BR6.5)。空メニュー時の案内表示はフロントエンド側の責務。
 */
public record MenuResponse(List<MenuItemView> businessMenu, List<MenuItemView> adminMenu) {

  public MenuResponse {
    businessMenu = businessMenu == null ? List.of() : List.copyOf(businessMenu);
    adminMenu = adminMenu == null ? List.of() : List.copyOf(adminMenu);
  }
}
