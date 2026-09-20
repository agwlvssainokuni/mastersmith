package com.mastersmith.auth.exception;

/**
 * Sessionが有効でないこと(C14、BR5.11の定義: revoked、またはリフレッシュの有効期限の経過)。内部呼び出しのJava例外で、frontend-ui向けには、 Bearer認証の失敗として401に変換される
 * ({@code AuthCrossCuttingExceptionAdvice})。
 */
public class SessionExpiredException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SessionExpiredException() {
    super("Session is not valid");
  }
}
