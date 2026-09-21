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

package com.mastersmith.auth.exception;

/**
 * このリフレッシュトークンでは更新できないこと(未知・期限切れ・失効済み・再送の競合・盗用の疑いのいずれも区別しない、BR5.6)。401({@code
 * auth.refresh.rejected})。
 */
public class RefreshRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RefreshRejectedException() {
    super("Refresh rejected");
  }
}
