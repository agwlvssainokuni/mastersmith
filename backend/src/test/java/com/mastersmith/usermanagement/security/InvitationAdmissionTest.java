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

package com.mastersmith.usermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.exception.InvitationCapacityExceededException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link InvitationAdmission}: 上限・待たずに拒否・許可の返却(reliability-design.md NFR4.2)。 */
class InvitationAdmissionTest {

  @Test
  void admitsUpToTheLimitAndRejectsTheNextWithoutWaiting() {
    InvitationAdmission admission = new InvitationAdmission(5);
    List<InvitationAdmission.Permit> permits = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      permits.add(admission.acquire());
    }

    long start = System.nanoTime();
    assertThatThrownBy(admission::acquire).isInstanceOf(InvitationCapacityExceededException.class);
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertThat(elapsedMillis).isLessThan(1_000);
    assertThat(admission.availablePermits()).isZero();
    permits.forEach(InvitationAdmission.Permit::close);
    assertThat(admission.availablePermits()).isEqualTo(5);
  }

  @Test
  void aReleasedPermitCanBeAcquiredAgain() {
    InvitationAdmission admission = new InvitationAdmission(1);
    admission.acquire().close();

    try (InvitationAdmission.Permit permit = admission.acquire()) {
      assertThat(admission.availablePermits()).isZero();
    }
    assertThat(admission.availablePermits()).isEqualTo(1);
  }

  @Test
  void closingAPermitTwiceReturnsItOnlyOnce() {
    InvitationAdmission admission = new InvitationAdmission(2);
    InvitationAdmission.Permit first = admission.acquire();
    InvitationAdmission.Permit second = admission.acquire();

    first.close();
    first.close();

    assertThat(admission.availablePermits()).isEqualTo(1);
    second.close();
    assertThat(admission.availablePermits()).isEqualTo(2);
  }
}
