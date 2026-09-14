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

package com.mastersmith.permission.dto;

import com.mastersmith.permission.entity.PermissionLevel;

/**
 * ロール階層継承後の実効権限(inception/contract-design/contract-summary.md C10
 * EffectivePermission、functional-spec.md W1)。
 *
 * @param level 実効主権限(rules.md BR3.4, BR3.6)
 * @param canCreate 実効補助権限(作成、rules.md BR3.5, BR3.6)
 * @param canDelete 実効補助権限(削除、rules.md BR3.5, BR3.6)
 */
public record EffectivePermission(PermissionLevel level, boolean canCreate, boolean canDelete) {

  /** 明示設定がどの階層にも見つからない場合の安全側デフォルト(rules.md BR3.6)。 */
  public static final EffectivePermission NONE =
      new EffectivePermission(PermissionLevel.NONE, false, false);
}
