package com.mastersmith.auth.exception;

import java.io.IOException;

/**
 * リクエストボディが、上限(64KiB)を超えたこと(413、NFR2.10)。{@code Content-Length}のないリクエスト(チャンク転送)で、読み込み量を数える ストリームが、上限を超えた時点で投げる。入力ストリームの読み取り中に投げるため
 * {@link IOException}で、JSONの読み取りの中で {@code HttpMessageNotReadableException}に包まれる場合と、包まれずに直接伝わる場合がある。 {@code
 * AuthApiExceptionAdvice}が、原因の連鎖をたどって413に変換する。
 */
public class RequestBodyTooLargeException extends IOException {

  private static final long serialVersionUID = 1L;

  public RequestBodyTooLargeException(long limitBytes) {
    super("Request body exceeds the limit of " + limitBytes + " bytes");
  }
}
