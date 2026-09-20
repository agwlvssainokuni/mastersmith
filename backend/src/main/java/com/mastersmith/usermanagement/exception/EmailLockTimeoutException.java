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

/** 同一emailの排他を、待機の上限(既定12秒)内に取れなかったこと(reliability-design.md NFR4.2)。U4の例外変換が503にする。 */
public class EmailLockTimeoutException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EmailLockTimeoutException() {
    super("Timed out waiting for the invitation lock of the email address");
  }
}
