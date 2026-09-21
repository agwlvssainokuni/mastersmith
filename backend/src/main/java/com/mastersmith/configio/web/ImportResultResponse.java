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

package com.mastersmith.configio.web;

import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.configio.service.ImportResult;
import java.util.Map;

/**
 * 取り込み成功(200)の応答本体(C7の追補、BR9.17)。セクション(schema・translations・menu・roles・groups・primaryPermissions・auxiliaryPermissions)ごとの、追加・更新・削除の件数を持つ。
 *
 * @param outcome 常に{@code SUCCESS}
 * @param sections セクションごとの件数
 */
public record ImportResultResponse(String outcome, Map<String, SectionCounts> sections) {

  public static ImportResultResponse from(ImportResult result) {
    return new ImportResultResponse(result.outcome(), result.sections());
  }
}
