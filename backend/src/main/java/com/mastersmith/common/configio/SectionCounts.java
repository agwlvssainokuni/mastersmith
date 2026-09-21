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
 * 設定の取り込みの1つのセクションの、追加・更新・削除の件数(config-import-export entities.md
 * SectionCounts)。値が変わらない項目は、「更新」に数えない(BR9.9)。
 *
 * @param added 追加した件数
 * @param updated 更新した件数(値が変わったもの)
 * @param deleted 削除した件数(全置換のため、ファイルにない項目)
 */
public record SectionCounts(int added, int updated, int deleted) {

  public static final SectionCounts ZERO = new SectionCounts(0, 0, 0);

  public SectionCounts {
    if (added < 0 || updated < 0 || deleted < 0) {
      throw new IllegalArgumentException("counts must not be negative");
    }
  }

  /** 追加・更新・削除の合計(変更の総数)。 */
  public int total() {
    return added + updated + deleted;
  }
}
