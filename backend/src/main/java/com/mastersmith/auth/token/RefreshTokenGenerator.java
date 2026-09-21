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

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークン(不透明で、内容を持たない推測不能なランダムな値)を生成する(NFR2.3、BR5.5)。{@link SecureRandom}で256ビット(32バイト)を生成し、
 * Base64URL(パディングなし、43文字)にする。
 */
@Component
public class RefreshTokenGenerator {

  private static final int BYTES = 32;

  private final SecureRandom random;

  @Autowired
  public RefreshTokenGenerator() {
    this(new SecureRandom());
  }

  /** 乱数源を指定する(テストで、固定値を返す生成器を用いるため)。 */
  public RefreshTokenGenerator(SecureRandom random) {
    this.random = random;
  }

  public String generate() {
    byte[] bytes = new byte[BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
