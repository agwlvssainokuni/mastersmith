package com.mastersmith.auth.exception;

/**
 * 内部設定DBの障害(接続の取得の失敗・タイムアウト・ロックの待機の超過など、一時的・接続の障害)により、認証の処理を完了できないこと(NFR4.2)。
 * 503({@code auth.service.unavailable})に変換される。例外のメッセージには、SQL・接続情報・利用者の値を含めない。
 */
public class AuthStorageUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public AuthStorageUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public AuthStorageUnavailableException(String message) {
    super(message);
  }
}
