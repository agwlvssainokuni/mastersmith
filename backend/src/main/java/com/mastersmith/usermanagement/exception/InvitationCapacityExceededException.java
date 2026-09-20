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

package com.mastersmith.usermanagement.exception;

/** 招待の同時実行数の許可が満杯で、待たずに拒否したこと(reliability-design.md NFR4.2)。U4の例外変換が503にする。 */
public class InvitationCapacityExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvitationCapacityExceededException() {
    super("Invitation capacity exceeded");
  }
}
