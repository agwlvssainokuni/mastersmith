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

package com.mastersmith.permission.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link BootstrapStateChecker}の単体テスト(rules.md BR3.13)。 */
@ExtendWith(MockitoExtension.class)
class BootstrapStateCheckerTest {

  @Mock private PrimaryPermissionRepository primaryPermissionRepository;

  private BootstrapStateChecker checker;

  private BootstrapStateChecker checker() {
    if (checker == null) {
      checker = new BootstrapStateChecker(primaryPermissionRepository);
    }
    return checker;
  }

  @Test
  void isBootstrapStateWhenNoPrimaryPermissionRowExists() {
    when(primaryPermissionRepository.count()).thenReturn(0L);

    assertThat(checker().isBootstrapState()).isTrue();
  }

  @Test
  void isNotBootstrapStateOnceAtLeastOnePrimaryPermissionRowExists() {
    when(primaryPermissionRepository.count()).thenReturn(1L);

    assertThat(checker().isBootstrapState()).isFalse();
  }

  @Test
  void isNotBootstrapStateWhenManyPrimaryPermissionRowsExist() {
    when(primaryPermissionRepository.count()).thenReturn(42L);

    assertThat(checker().isBootstrapState()).isFalse();
  }
}
