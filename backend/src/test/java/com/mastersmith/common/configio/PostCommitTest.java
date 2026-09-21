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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** {@link PostCommit}・{@link ApplyResult}・{@link SectionCounts}の単体テスト。 */
class PostCommitTest {

  @Test
  void noneDoesNothingAndNeverFails() {
    PostCommit.NONE.invalidateCaches().run();
    PostCommit.NONE.publishEvents().run();
  }

  @Test
  void keepsTheTwoActionsSeparate() {
    AtomicInteger invalidated = new AtomicInteger();
    AtomicInteger published = new AtomicInteger();
    PostCommit postCommit =
        new PostCommit(invalidated::incrementAndGet, published::incrementAndGet);

    postCommit.invalidateCaches().run();

    assertThat(invalidated).hasValue(1);
    assertThat(published).hasValue(0);
    postCommit.publishEvents().run();
    assertThat(published).hasValue(1);
  }

  @Test
  void rejectsNullActions() {
    assertThatThrownBy(() -> new PostCommit(null, () -> {}))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PostCommit(() -> {}, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void applyResultKeepsSectionOrderAndIsUnmodifiable() {
    Map<String, SectionCounts> sections = new LinkedHashMap<>();
    sections.put(ImportSections.SCHEMA, new SectionCounts(1, 2, 3));
    sections.put(ImportSections.MENU, new SectionCounts(4, 5, 6));

    ApplyResult result = new ApplyResult(sections, PostCommit.NONE);
    sections.put(ImportSections.ROLES, SectionCounts.ZERO);

    assertThat(result.sections().keySet())
        .containsExactly(ImportSections.SCHEMA, ImportSections.MENU);
    assertThatThrownBy(() -> result.sections().put("x", SectionCounts.ZERO))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(new ApplyResult(null, PostCommit.NONE).sections()).isEmpty();
  }

  @Test
  void sectionCountsTotalsAndRejectsNegatives() {
    assertThat(new SectionCounts(1, 2, 3).total()).isEqualTo(6);
    assertThatThrownBy(() -> new SectionCounts(-1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sectionNamesAreListedInDisplayOrder() {
    assertThat(ImportSections.ALL)
        .containsExactly(
            "schema",
            "translations",
            "menu",
            "roles",
            "groups",
            "primaryPermissions",
            "auxiliaryPermissions");
  }
}
