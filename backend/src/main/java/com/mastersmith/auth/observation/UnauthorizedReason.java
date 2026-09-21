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

package com.mastersmith.auth.observation;

/** 認証フィルタが401を返した原因の分類(NFR5.1)。値が4つに固定された列挙型で、任意の文字列を、メトリクスのタグに使えない。応答には出さない。 */
public enum UnauthorizedReason {
  /** {@code Authorization}ヘッダーがない、またはBearerでない。 */
  MISSING("missing"),
  /** トークンの解析・署名・必須の値・{@code sub}の照合の失敗。 */
  INVALID("invalid"),
  /** {@code exp}の経過。 */
  EXPIRED("expired"),
  /** Sessionが存在しない、または有効でない。 */
  SESSION_INACTIVE("session_inactive");

  private final String tag;

  UnauthorizedReason(String tag) {
    this.tag = tag;
  }

  /** メトリクスのタグの値・ログの分類。 */
  public String tag() {
    return tag;
  }
}
