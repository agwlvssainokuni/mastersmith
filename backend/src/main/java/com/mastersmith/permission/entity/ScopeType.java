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

package com.mastersmith.permission.entity;

/**
 * 権限割当の対象階層(entities.md PrimaryPermission.scopeType/AuxiliaryPermission.scopeType)。
 *
 * <p>実効権限の階層解決(rules.md BR3.4: 主権限はCOLUMN→TABLE→SCHEMA、BR3.5: 補助権限はTABLE→SCHEMA)は、この3値の順序
 * (COLUMNが最も詳細、SCHEMAが最上位)に基づく。AuxiliaryPermissionはCOLUMNを対象としない(entities.md
 * AuxiliaryPermission.scopeType.allowed_values)。
 */
public enum ScopeType {
  SCHEMA,
  TABLE,
  COLUMN
}
