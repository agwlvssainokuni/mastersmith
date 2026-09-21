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

import java.io.IOException;

/**
 * リクエストボディが、上限(64KiB)を超えたこと(413、NFR2.10)。{@code Content-Length}のないリクエスト(チャンク転送)で、読み込み量を数える
 * ストリームが、上限を超えた時点で投げる。入力ストリームの読み取り中に投げるため {@link IOException}で、JSONの読み取りの中で {@code
 * HttpMessageNotReadableException}に包まれる場合と、包まれずに直接伝わる場合がある。 {@code
 * AuthApiExceptionAdvice}が、原因の連鎖をたどって413に変換する。
 */
public class RequestBodyTooLargeException extends IOException {

  private static final long serialVersionUID = 1L;

  public RequestBodyTooLargeException(long limitBytes) {
    super("Request body exceeds the limit of " + limitBytes + " bytes");
  }
}
