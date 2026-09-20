/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastersmith.usermanagement.dto;

/**
 * 招待受諾のリクエスト(C5、{@code POST /api/users/invitations/{token}/accept})。
 *
 * @param password 初回のパスワード(8文字以上128文字以下)。平文のため、{@link #toString()}に出力しない
 * @param theme 任意。省略時は{@code light}(BR4.3)
 * @param fontSize 任意。省略時は{@code medium}
 * @param locale 任意。省略時は{@code ja}
 */
public record AcceptInvitationRequest(
    String password, String name, String theme, String fontSize, String locale) {

  /** 認証情報(パスワード)と氏名を、ログ・例外メッセージへ出さない(BR4.4)。 */
  @Override
  public String toString() {
    return "AcceptInvitationRequest{theme=%s, fontSize=%s, locale=%s}"
        .formatted(theme, fontSize, locale);
  }
}
