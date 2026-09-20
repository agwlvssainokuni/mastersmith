package com.mastersmith.auth.exception;

/** ログインの失敗(全原因)。原因(未登録・パスワードの誤り・ロック中・無効化済み・招待中)を区別しない(BR5.2)。401({@code auth.login.failed})。 */
public class LoginFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LoginFailedException() {
    super("Login failed");
  }
}
