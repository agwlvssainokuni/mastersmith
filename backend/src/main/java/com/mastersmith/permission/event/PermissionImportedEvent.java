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

package com.mastersmith.permission.event;

import java.time.Instant;

/**
 * config-import-exportの1回の取り込みで、権限(主権限・補助権限)が変更されたことを、audit-loggingへ知らせる、取り込み単位のサマリイベント(rules.md
 * BR3.11)。実行者・変更件数・日時だけを持ち、個々の変更前後の値は含めない。
 * 取り込みのトランザクションの確定後に、1回の取り込みにつき、1件だけ発行する(主権限・補助権限の変更が1件以上ある場合に限る)。
 *
 * @param actor 取り込みを実行した操作者のactiveRoleId(未選択ならnull)
 * @param changeCount 主権限・補助権限の、追加・更新・削除の合計
 * @param occurredAt 発生日時
 */
public record PermissionImportedEvent(String actor, int changeCount, Instant occurredAt) {

  public static PermissionImportedEvent of(String actor, int changeCount) {
    return new PermissionImportedEvent(actor, changeCount, Instant.now());
  }
}
