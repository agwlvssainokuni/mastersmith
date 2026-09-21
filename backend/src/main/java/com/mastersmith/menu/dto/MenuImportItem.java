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
 * config-import-exportが、menu-navigationに渡す、業務メニュー1項目の取り込みの入力(C12の{@code
 * validateMenuStructure}・{@code applyMenuStructure}の引数の要素、 functional-spec.md
 * 追補一覧3番)。メニュー項目は、自然キーを持たず(BR9.3)、入れ子の構造で親子を表すため、呼び出し元が、入れ子を、親を指す位置の文字列で結んだ、フラットな一覧にして渡す。
 * メニュー項目のIDは、取り込みのたびに再採番される(BR9.14)ため、入力には含めない。
 *
 * @param position この項目の位置(検証の誤りの位置に、そのまま用いる。一覧の中で一意。例 {@code menu.items[0].children[2]})
 * @param parentPosition 親の項目の位置。ルート直下はnull
 * @param label 表示名
 * @param order 同一階層内での表示順
 * @param leaf 遷移先のテーブルを持つ項目(リーフ)か。フォルダはfalse
 * @param targetTableConfigId
 *     遷移先のテーブルのID。リーフでも、検証の段階では、取り込みで新規に作られるテーブルのため、nullでありうる。反映の段階では、リーフには必須
 */
public record MenuImportItem(
    String position,
    String parentPosition,
    String label,
    Integer order,
    boolean leaf,
    String targetTableConfigId) {}
