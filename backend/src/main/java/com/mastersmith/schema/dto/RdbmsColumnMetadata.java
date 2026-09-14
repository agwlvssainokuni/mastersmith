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

package com.mastersmith.schema.dto;

/**
 * 対象RDBMS(業務データ用)のメタデータカタログから読み取った1カラム分の情報(entities.md RdbmsColumnMetadata、functional-spec.md
 * W1手順6)。永続化しない、1回のintrospection呼び出し内でのみ保持する中間データ。
 *
 * @param columnName カラム名
 * @param rawTypeName 対象RDBMSのメタデータ型名(例:
 *     "varchar(255)")。正規化せず生のまま保持する。editorType/formatの決定はconfig-engine側の論理型正規化ロジックに委ねる(BR2.5)
 * @param isPrimaryKey 主キー制約から判定した結果(BR2.4)。複合主キーの場合、含まれる全カラムがtrue
 * @param nullable NOT
 *     NULL制約から判定したNULL可否(BR2.10)。FR1.4の読み取り対象として保持するが、config-engineのColumnDraftEntryには対応フィールドが無いため伝搬しない(BR2.10)
 */
public record RdbmsColumnMetadata(
    String columnName, String rawTypeName, boolean isPrimaryKey, boolean nullable) {}
