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
 * 招待メールの生成・送信の失敗(rules.md BR4.16)。招待全体を失敗させ、Userの作成・更新を巻き戻して503にする。
 *
 * <p>メッセージには、失敗の分類と例外の型名だけを含める。SMTPライブラリの例外のメッセージは宛先(メールアドレス)を含みうるため、原因の例外は 保持しない(NFR2.6)。
 */
public class InvitationMailException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 失敗の分類(ログ・メトリクスの分類に用いる)。 */
  public enum Failure {
    /** SMTPへの送信・接続の失敗。 */
    SEND_FAILED,
    /** 送信結果の待機の打ち切り(10秒)。 */
    TIMEOUT,
    /** 送信の専用プールが満杯(打ち切り後の送信スレッドが残っている場合など)。 */
    POOL_EXHAUSTED,
    /** 待機が中断された。 */
    INTERRUPTED,
    /** テンプレート起因の失敗(レンダリングの失敗、件名の拒否)。 */
    TEMPLATE_INVALID
  }

  private final Failure failure;

  public InvitationMailException(Failure failure, String message) {
    super(message);
    this.failure = failure;
  }

  public Failure getFailure() {
    return failure;
  }
}
