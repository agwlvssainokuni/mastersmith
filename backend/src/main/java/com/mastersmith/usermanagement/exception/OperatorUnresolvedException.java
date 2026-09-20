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

/** 操作者(userId・activeRoleId)を解決できなかったこと(401、rules.md BR4.8・BR4.10)。 */
public class OperatorUnresolvedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public OperatorUnresolvedException() {
    super("Unable to resolve the operator");
  }
}
