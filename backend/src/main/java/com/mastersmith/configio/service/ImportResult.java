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

package com.mastersmith.configio.service;

import com.mastersmith.common.configio.SectionCounts;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 取り込みが成功したときの結果(config-import-export entities.md
 * ImportResult、BR9.17)。200の応答本体と、監査イベントの件数に用いる。応答は、成功のときだけ返る(失敗は、422などの誤りの応答になる)。
 *
 * @param sections
 *     セクション(schema・translations・menu・roles・groups・primaryPermissions・auxiliaryPermissions)ごとの、追加・更新・削除の件数
 */
public record ImportResult(Map<String, SectionCounts> sections) {

  public ImportResult {
    sections = Collections.unmodifiableMap(new LinkedHashMap<>(sections));
  }

  /** 常に{@code SUCCESS}(失敗は、応答の種類が異なる)。 */
  public String outcome() {
    return "SUCCESS";
  }
}
