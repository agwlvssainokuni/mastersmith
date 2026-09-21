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

package com.mastersmith.common.cache;

import java.io.Serial;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * キャッシュの再読み込みができない(内部設定DBの障害・再読み込みの排他の待ちの上限・失敗の抑制の期間中)ことを表す例外。その読み取りだけの失敗であり、呼び出し元では503として扱う
 * ({@link org.springframework.dao.DataAccessException}の系統。既存のコントローラーの内部設定DBの障害の処理に、そのまま乗る)。
 *
 * <p>メッセージには、内部の詳細(SQL・接続情報)を含めない。
 */
public class CacheReloadUnavailableException extends DataAccessResourceFailureException {

  @Serial private static final long serialVersionUID = 1L;

  public CacheReloadUnavailableException(String message) {
    super(message);
  }

  public CacheReloadUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
