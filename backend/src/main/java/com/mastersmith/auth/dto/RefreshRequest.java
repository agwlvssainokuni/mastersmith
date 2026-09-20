package com.mastersmith.auth.dto;

/** リフレッシュのリクエスト(C4)。{@code toString}は、リフレッシュトークンを含めない(NFR2.7)。 */
public record RefreshRequest(String refreshToken) {

  @Override
  public String toString() {
    return "RefreshRequest[]";
  }
}
