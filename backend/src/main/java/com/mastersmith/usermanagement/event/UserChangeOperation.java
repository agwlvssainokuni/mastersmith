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

package com.mastersmith.usermanagement.event;

/** {@link UserChangedEvent}の操作種別(entities.md UserChangedEvent.operation、rules.md BR4.9)。 */
public enum UserChangeOperation {
  /** 招待・再招待。 */
  INVITED,
  /** 招待受諾によるパスワード設定・有効化。 */
  ACTIVATED,
  /** 管理者による更新(name・roleIds)。 */
  UPDATED,
  /** 無効化・招待取消。 */
  DISABLED,
  /** 初期管理者の自動作成。 */
  BOOTSTRAPPED
}
