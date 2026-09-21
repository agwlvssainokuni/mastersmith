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

package com.mastersmith.common.configio;

/**
 * 設定の取り込みの検証の誤りに用いる、翻訳用のメッセージキー(i18nキー)の一覧(config-import-export
 * BR9.7、C7の追補)。各ユニットの検証専用メソッドと、config-import-exportが、同じキーを用いる。 入力値そのものは、キーにもパラメータにも含めない。
 */
public final class ImportMessageKeys {

  /** リクエスト本体が、JSONとして読めない・オブジェクトでない。 */
  public static final String JSON_MALFORMED = "config.import.json.malformed";

  /** {@code formatVersion}が欠落、または対応しない値。 */
  public static final String FORMAT_UNSUPPORTED = "config.import.format.unsupported";

  /** 必須の項目の欠落(null・空を含む)。 */
  public static final String FIELD_REQUIRED = "config.import.field.required";

  /** 項目の型が異なる。パラメータ {@code expected}: 期待する型。 */
  public static final String FIELD_TYPE = "config.import.field.type";

  /** 許容されない値。パラメータ {@code allowed}: 許容値の一覧。 */
  public static final String FIELD_VALUE_INVALID = "config.import.field.value.invalid";

  /** 一意でなければならない項目の重複。 */
  public static final String FIELD_DUPLICATE = "config.import.field.duplicate";

  /** 文字列の長さの上限を超えた。パラメータ {@code max}: 上限。 */
  public static final String FIELD_TOO_LONG = "config.import.field.too-long";

  /** セクションをまたぐ参照が、ファイルの中で解決できない(BR9.13)。 */
  public static final String REFERENCE_NOT_FOUND = "config.import.reference.notFound";

  /** カラムの{@code choiceOptions}と{@code fkReference}の同時指定(config-engine BR1.4)。 */
  public static final String COLUMN_CHOICE_OR_FK_EXCLUSIVE =
      "config.import.column.choiceOrFkExclusive";

  /** メニューの階層の規則の違反(遷移先を持つ項目が、子を持つ)。 */
  public static final String MENU_LEAF_HAS_CHILDREN = "config.import.menu.leafHasChildren";

  /** メニューの親の参照が、解決できない・自己参照・循環している。 */
  public static final String MENU_PARENT_INVALID = "config.import.menu.parentInvalid";

  /** 補助権限の対象に、COLUMNは指定できない。 */
  public static final String RBAC_AUXILIARY_COLUMN = "config.import.rbac.auxiliaryColumn";

  /** 権限の昇格(操作者の実効権限を上回る割当、BR9.11)。 */
  public static final String RBAC_ESCALATION = "config.import.rbac.escalation";

  /** 主権限が0件になる(BR9.12)。 */
  public static final String RBAC_EMPTY = "config.import.rbac.empty";

  /** 予約スキーマ名(管理系画面用)として認められない名前。 */
  public static final String RBAC_RESERVED_UNKNOWN = "config.import.rbac.reservedUnknown";

  private ImportMessageKeys() {}
}
