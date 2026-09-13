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

package com.mastersmith.config.entity;

/**
 * ColumnConfigの表示可否(entities.md ColumnConfig.visibility, FR4.5)。
 *
 * <p>権限とは独立した表示可否軸。READ権限未満の場合の非表示制御はPermissionEngine側の 判定と組み合わされる(本ユニットの責務外)。
 */
public enum Visibility {
  VISIBLE,
  HIDDEN
}
