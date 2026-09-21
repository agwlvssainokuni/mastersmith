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

package com.mastersmith.auth.token;

/**
 * JWTの署名の鍵(バイト列)を保持する専用の型(NFR2.2・NFR2.7)。{@link #toString()}は固定の伏せ字を返し、{@link
 * #equals(Object)}・{@link #hashCode()}にも 値を使わない(同一性による)ため、ログ・例外・診断に値が現れる経路を作らない。
 */
public final class SecretKeyMaterial {

  private static final String MASKED = "SecretKeyMaterial[****]";

  private final byte[] bytes;

  public SecretKeyMaterial(byte[] bytes) {
    this.bytes = bytes.clone();
  }

  /** 鍵のバイト列の写し(呼び出し側が変更しても、保持している値は変わらない)。 */
  public byte[] copyOfBytes() {
    return bytes.clone();
  }

  /** 鍵の長さ(バイト)。 */
  public int length() {
    return bytes.length;
  }

  @Override
  public String toString() {
    return MASKED;
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
