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

import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.common.configio.SectionCounts;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ImportOrchestrator#apply}の結果: セクションごとの件数と、3ユニットの確定後の動作({@link
 * PostCommit})。動作の順序は、config-engine・menu-navigation・permission-engineの順 ({@code
 * PostCommitCoordinator}が、この順序で、無効化→個別イベントの発行を行う)。
 */
public record AppliedImport(Map<String, SectionCounts> sections, List<PostCommit> postCommits) {

  public AppliedImport {
    sections = Collections.unmodifiableMap(new LinkedHashMap<>(sections));
    postCommits = List.copyOf(postCommits);
  }
}
