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

/** 操作者のactiveRoleIdが、ユーザ管理の画面に対する権限を持たないこと(403、rules.md BR4.10)。 */
public class UserAccessDeniedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UserAccessDeniedException() {
    super("Access to user management is denied");
  }
}
