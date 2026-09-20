package com.mastersmith.auth.exception;

/**
 * このリフレッシュトークンでは更新できないこと(未知・期限切れ・失効済み・再送の競合・盗用の疑いのいずれも区別しない、BR5.6)。401({@code
 * auth.refresh.rejected})。
 */
public class RefreshRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RefreshRejectedException() {
    super("Refresh rejected");
  }
}
