package com.mastersmith.auth.exception;

import com.mastersmith.auth.observation.UnauthorizedReason;

/**
 * アクセストークンの検証の失敗。原因の分類({@link UnauthorizedReason})だけを持ち、トークンの内容・解析の例外のメッセージは持たない
 * (NFR2.7)。応答には原因を出さない(メトリクスのタグとDEBUGログにだけ用いる)。
 */
public class InvalidAccessTokenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final UnauthorizedReason reason;

  public InvalidAccessTokenException(UnauthorizedReason reason) {
    super("Invalid access token: " + reason.tag());
    this.reason = reason;
  }

  public UnauthorizedReason reason() {
    return reason;
  }
}
