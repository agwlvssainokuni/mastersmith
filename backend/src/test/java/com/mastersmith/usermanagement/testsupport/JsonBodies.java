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

package com.mastersmith.usermanagement.testsupport;

import java.nio.charset.StandardCharsets;

/** リクエストボディの大きさを、バイト数で指定して組み立てる(境界値のテスト用)。 */
public final class JsonBodies {

  private JsonBodies() {}

  /** 招待のJSONに、未知の項目{@code pad}を足して、ちょうど指定のバイト数(ASCII)にする。 */
  public static byte[] inviteBodyOfExactly(int bytes) {
    String prefix = "{\"email\":\"a@example.test\",\"name\":\"n\",\"roleIds\":[],\"pad\":\"";
    String suffix = "\"}";
    int padding = bytes - prefix.length() - suffix.length();
    if (padding < 0) {
      throw new IllegalArgumentException("bytes is too small");
    }
    return (prefix + "a".repeat(padding) + suffix).getBytes(StandardCharsets.US_ASCII);
  }

  /** 指定のバイト数の、中身は問わない本文(上限を超える拒否のテスト用)。 */
  public static byte[] filler(int bytes) {
    return "x".repeat(bytes).getBytes(StandardCharsets.US_ASCII);
  }
}
