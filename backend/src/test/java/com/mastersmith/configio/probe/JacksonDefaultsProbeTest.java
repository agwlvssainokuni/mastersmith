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

package com.mastersmith.configio.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 事前の確認(code-generation-plan.md Step 1(a))。Spring Boot 4.1.1が既定で構成するJackson 3系({@code
 * tools.jackson})の、 入れ子の深さ・文字列の長さ・重複プロパティの既定の挙動を、実際に確認して固定する。
 *
 * <p>要件は、本ユニット独自の制限を設けないこと(Q2=C)であり、制限の値そのものは要件ではない。ここで固定した値は、Jackson 3の既定の挙動の記録である
 * (将来のバージョンで変わった場合に、このテストの失敗で気づくため)。
 */
class JacksonDefaultsProbeTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void jackson3IsResolvedFromTheWebStarter() {
    assertThat(JsonMapper.class.getName()).startsWith("tools.jackson.databind.");
  }

  @Test
  void defaultConstraintsAreRecorded() {
    StreamReadConstraints defaults = StreamReadConstraints.defaults();
    // 入れ子の深さの既定は500、文字列の長さの既定は1億文字(3.1.5で確認)、ドキュメント全体の長さは無制限(-1)。
    assertThat(defaults.getMaxNestingDepth()).isEqualTo(500);
    assertThat(defaults.getMaxStringLength()).isEqualTo(100_000_000);
    assertThat(defaults.getMaxNameLength()).isEqualTo(50_000);
    assertThat(defaults.getMaxDocumentLength()).isEqualTo(-1L);
  }

  @Test
  void nestingBeyondTheDefaultDepthFailsAsParseError() {
    String withinLimit = "[".repeat(500) + "]".repeat(500);
    String beyondLimit = "[".repeat(501) + "]".repeat(501);

    assertThat(mapper.readTree(withinLimit)).isNotNull();
    assertThatThrownBy(() -> mapper.readTree(beyondLimit))
        .isInstanceOf(StreamConstraintsException.class);
  }

  @Test
  void duplicatePropertyBehaviourIsRecorded() {
    boolean failsOnDuplicate =
        mapper.isEnabled(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    String json = "{\"a\":1,\"a\":2}";
    if (failsOnDuplicate) {
      assertThatThrownBy(() -> mapper.readTree(json))
          .isInstanceOf(tools.jackson.core.JacksonException.class);
    } else {
      JsonNode node = mapper.readTree(json);
      assertThat(node.get("a").asInt()).isEqualTo(2);
    }
    // 観測値を、テスト出力に残す(code-summary.mdの記録用)。
    System.out.println("JACKSON3_PROBE FAIL_ON_READING_DUP_TREE_KEY=" + failsOnDuplicate);
  }

  @Test
  void unknownPropertiesAreKeptInTheTree() {
    JsonNode node = mapper.readTree("{\"known\":1,\"unknown\":{\"x\":[1,2]}}");
    assertThat(node.has("unknown")).isTrue();
  }
}
