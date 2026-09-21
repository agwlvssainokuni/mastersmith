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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 取り込みの反映(伝播{@code MANDATORY}のメソッド)の結果(config-import-export logical-components.md
 * 追補3・追補4)。セクションごとの追加・更新・削除の件数と、トランザクションの確定後に行う動作 ({@link PostCommit})を持つ。
 *
 * @param sections セクション名({@link ImportSections})ごとの件数。反映したユニットが受け持つセクションだけを含む
 * @param postCommit 確定後の動作(無効化と、個別の変更イベントの発行)
 */
public record ApplyResult(Map<String, SectionCounts> sections, PostCommit postCommit) {

  public ApplyResult {
    Objects.requireNonNull(postCommit, "postCommit");
    sections =
        sections == null
            ? Map.of()
            : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sections));
  }
}
