package com.mastersmith.auth.exception;

/** 認証済みの操作者を解決できないこと(認証フィルタを通っていない呼び出し)。401({@code auth.token.invalid})。 */
public class AuthenticationRequiredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AuthenticationRequiredException() {
    super("Authentication required");
  }
}
