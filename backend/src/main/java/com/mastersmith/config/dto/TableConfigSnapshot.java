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

package com.mastersmith.config.dto;

import com.mastersmith.config.entity.TableConfig;

/**
 * テーブルの設定の、他から変更できないスナップショット(C9の{@code getExportableConfigSet}の戻り値の要素)。永続化の管理下のエンティティではなく、値のコピーである。
 */
public record TableConfigSnapshot(
    String tableConfigId,
    String schemaName,
    String tableName,
    int displayOrder,
    String optimisticLockColumn) {

  public static TableConfigSnapshot of(TableConfig entity) {
    return new TableConfigSnapshot(
        entity.getTableConfigId(),
        entity.getSchemaName(),
        entity.getTableName(),
        entity.getDisplayOrder(),
        entity.getOptimisticLockColumn());
  }
}
