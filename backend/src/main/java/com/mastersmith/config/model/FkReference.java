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

package com.mastersmith.config.model;

/**
 * editorTypeがselect/radioで、FK参照による動的名称解決を行う場合の参照先定義 (entities.md ColumnConfig.fkReference, FR1.5)。
 *
 * <p>名称解決自体はConfigEngineではなく、業務データRDBMSへアクセス可能な
 * list-engine/record-edit-engineが実行時に行う(functional-spec.md W4)。ConfigEngineは この参照先メタデータのみを保持・提供する。
 */
public record FkReference(
    String referencedSchemaName,
    String referencedTableName,
    String referencedValueColumnName,
    String referencedLabelColumnName) {}
