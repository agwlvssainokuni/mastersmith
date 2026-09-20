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

package com.mastersmith.usermanagement;

/**
 * ハッシュ計算の同時実行数の許可を、待機の上限(既定2秒)内に取れなかったことを表す非チェック例外(C11の追補、NFR1.3)。
 *
 * <p>REST(招待受諾など)では、U4の例外変換が503に変換する。C11の{@code verifyPasswordHash}では、この例外をそのまま呼び出し元
 * (authentication-service)へ伝え、HTTPへの変換は呼び出し元が行う。
 */
public class HashCapacityExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public HashCapacityExceededException(String message) {
    super(message);
  }
}
