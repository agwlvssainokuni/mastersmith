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

import com.mastersmith.auth.observation.UnauthorizedReason;

/**
 * アクセストークンの検証の失敗。原因の分類({@link UnauthorizedReason})だけを持ち、トークンの内容・解析の例外のメッセージは持たない
 * (NFR2.7)。応答には原因を出さない(メトリクスのタグとDEBUGログにだけ用いる)。
 */
public class InvalidAccessTokenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final UnauthorizedReason reason;

  public InvalidAccessTokenException(UnauthorizedReason reason) {
    super("Invalid access token: " + reason.tag());
    this.reason = reason;
  }

  public UnauthorizedReason reason() {
    return reason;
  }
}
