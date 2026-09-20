package com.mastersmith.auth.exception;

/**
 * Sessionが存在しないこと(C14)。内部呼び出しのJava例外で、frontend-ui向けには、Bearer認証の失敗として401に変換される ({@code
 * AuthCrossCuttingExceptionAdvice})。
 */
public class SessionNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SessionNotFoundException() {
    super("Session not found");
  }
}
