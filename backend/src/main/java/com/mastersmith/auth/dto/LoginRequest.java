package com.mastersmith.auth.dto;

/**
 * ログインのリクエスト(C4)。{@code toString}は、パスワードを含めない(NFR2.7。Javaの{@code record}の既定の{@code toString}は、全項目を出すため、上書きする)。
 */
public record LoginRequest(String email, String password) {

  @Override
  public String toString() {
    return "LoginRequest[]";
  }
}
