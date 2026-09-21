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
 * 内部設定DBの障害(接続の取得の失敗・タイムアウト・ロックの待機の超過など、一時的・接続の障害)により、認証の処理を完了できないこと(NFR4.2)。 503({@code
 * auth.service.unavailable})に変換される。例外のメッセージには、SQL・接続情報・利用者の値を含めない。
 */
public class AuthStorageUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AuthStorageUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public AuthStorageUnavailableException(String message) {
    super(message);
  }
}
