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

package com.mastersmith.usermanagement.exception;

/**
 * 招待メールの件名(HTMLの{@code <title>})を、拒否したこと(security-design.md NFR2.7)。改行などの制御文字を含む場合と200文字を超える場合は、
 * 除去せずに拒否する。入力起因の不備(氏名の最大長・制御文字)は入力の検証で先に排除するため、この例外はテンプレート起因に限られる。
 *
 * <p>メッセージには、拒否の理由だけを含め、件名の値は含めない。
 */
public class SubjectRejectedException extends InvitationMailException {

  private static final long serialVersionUID = 1L;

  /** 拒否の理由。 */
  public enum Rejection {
    MISSING_TITLE,
    EMPTY_TITLE,
    CONTROL_CHARACTER,
    TOO_LONG,
    UNSUPPORTED_REFERENCE
  }

  private final Rejection rejection;

  public SubjectRejectedException(Rejection rejection) {
    super(Failure.TEMPLATE_INVALID, "Invitation mail subject rejected: " + rejection);
    this.rejection = rejection;
  }

  public Rejection getRejection() {
    return rejection;
  }
}
