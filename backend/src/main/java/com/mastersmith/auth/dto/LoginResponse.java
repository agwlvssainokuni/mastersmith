package com.mastersmith.auth.dto;

import java.util.List;

/**
 * ログインの成功のレスポンス(C4)。選択可能なロール({@code roles})は、直接付与分とGroup経由分の和集合(BR5.9)。{@code activeRoleId}は、未選択ならnull。
 * {@code toString}は、トークンを含めない(NFR2.7)。
 */
public record LoginResponse(
    String accessToken, String refreshToken, List<String> roles, String activeRoleId) {

  @Override
  public String toString() {
    return "LoginResponse[activeRoleId=" + activeRoleId + "]";
  }
}
