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

package com.mastersmith.config.event;

/** {@link ConfigChangedEvent}が表す変更操作の種別。 */
public enum ConfigChangeOperation {
  /** schema-introspectorからの初期ドラフト取り込み(functional-spec.md W2)。 */
  DRAFT_IMPORTED,
  /** config-import-exportからの設定一式インポート(functional-spec.md W5)。 */
  CONFIG_SET_IMPORTED,
  /** 管理画面からのi18nテキスト登録・更新(functional-spec.md W6)。 */
  TRANSLATION_UPSERTED
}
