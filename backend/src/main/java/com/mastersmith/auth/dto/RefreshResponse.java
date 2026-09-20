package com.mastersmith.auth.dto;

import java.util.List;

/**
 * リフレッシュの成功のレスポンス(C4の追補、BR5.6)。ローテーションした新しいリフレッシュトークンと、選択可能なロール・アクティブロール(未選択ならnull)を返す
 * (FR4.2: ページの再読み込みのあとも、ロール選択・ヘッダーの表示ができる)。{@code toString}は、トークンを含めない(NFR2.7)。
 */
public record RefreshResponse(
    String accessToken, String refreshToken, List<String> roles, String activeRoleId) {

  @Override
  public String toString() {
    return "RefreshResponse[activeRoleId=" + activeRoleId + "]";
  }
}
