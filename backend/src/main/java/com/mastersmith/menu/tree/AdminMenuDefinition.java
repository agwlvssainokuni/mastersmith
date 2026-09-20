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

package com.mastersmith.menu.tree;

import java.util.List;

/**
 * 管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)の固定定義(BR6.2、functional-design-questions.md
 * Q8=A)。DBへ永続化せず、アプリケーションコードに固定配列としてハードコードする。
 *
 * <p>各項目の{@code menuItemId}は固定値とする(code-generation-plan.md「前提事項1」)。フロントエンド側のルーティングは、この固定4項目を{@code
 * label}または位置で判別する前提(functional-design-questions.md Q9=A)。
 *
 * <p>{@code
 * screenKey}はBR6.3-(3)〜(5)のとおり、「ユーザ管理」="user-management"、「監査ログ管理"="audit-log"、「業務メニュー設定」「設定管理」はいずれも
 * schema-introspectorと共有する予約screenKey"config-import-export"を用いる(両画面は同一の権限スコープに属する)。
 */
public final class AdminMenuDefinition {

  private AdminMenuDefinition() {}

  /** 管理メニュー1項目の固定定義。 */
  public record Entry(String menuItemId, String label, String screenKey) {}

  /** BR6.1: 管理メニュー配下の管理者機能4項目(表示順もこの並び順に固定する)。 */
  public static final List<Entry> ENTRIES =
      List.of(
          new Entry("admin-business-menu-config", "業務メニュー設定", "config-import-export"),
          new Entry("admin-user-management", "ユーザ管理", "user-management"),
          new Entry("admin-audit-log", "監査ログ管理", "audit-log"),
          new Entry("admin-config-management", "設定管理", "config-import-export"));
}
