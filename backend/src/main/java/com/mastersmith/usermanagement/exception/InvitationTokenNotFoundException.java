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

/**
 * 招待トークンに対応する、招待中のUserが存在しないこと(404)。未知・使用済み・取消済みのトークンを区別しない(rules.md BR4.2)。メッセージにトークンを含めない
 * (NFR2.10)。
 */
public class InvitationTokenNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvitationTokenNotFoundException() {
    super("Invitation not found");
  }
}
