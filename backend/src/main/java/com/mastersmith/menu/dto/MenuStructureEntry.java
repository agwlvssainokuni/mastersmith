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

/**
 * C12契約(menu-navigation → config-import-export 内部インタフェース契約、contract-summary.md)の{@code
 * MenuItem}型に対応するフラット表現(木構造ではなく、{@code parentMenuItemId}を保持する1件単位)。
 *
 * <p>C12契約は本型を{@code MenuItem}という名前で定義しているが、本ユニットには既に{@link
 * com.mastersmith.menu.entity.MenuItem}(永続化エンティティ)と{@link
 * MenuItemView}(REST用の木構造表現、C3契約)が存在するため、名前の衝突を避けて{@code
 * MenuStructureEntry}と命名する(フィールド構成はC12契約と1:1、config-engineの{@code ConfigExportSet}/{@code
 * ConfigImportSet}が独自のJava型名を持つのと同種の軽微な命名上の逸脱)。
 *
 * @param menuItemId メニュー項目のID
 * @param parentMenuItemId 親MenuItemのID。ルート直下の項目はnull
 * @param label 表示名
 * @param order 同一階層内での表示順
 * @param targetTableConfigId 遷移先TableConfigのID。フォルダ項目はnull
 */
public record MenuStructureEntry(
    String menuItemId,
    String parentMenuItemId,
    String label,
    int order,
    String targetTableConfigId) {}
