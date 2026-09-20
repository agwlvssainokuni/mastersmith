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

package com.mastersmith.usermanagement.web;

import com.mastersmith.usermanagement.dto.AcceptInvitationRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.service.InvitationAcceptService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C5の、招待メールのリンクからの初回パスワード・氏名の設定({@code POST /api/users/invitations/{token}/accept}、FR2.1、rules.md
 * BR4.2)。 認証は不要(C5: {@code security: []})で、招待トークンの保持が根拠となる(NFR2.4)。操作者は解決しない。
 *
 * <p>トークンは、パスにだけ含まれる。コントローラは、トークンをログ・例外・応答に出さない(NFR2.10)。未知・使用済み・取消済みのトークンは、区別せず、同一の404になる。
 */
@RestController
@RequestMapping("/api/users/invitations")
public class InvitationAcceptController {

  private final InvitationAcceptService acceptService;

  public InvitationAcceptController(InvitationAcceptService acceptService) {
    this.acceptService = acceptService;
  }

  @PostMapping("/{token}/accept")
  public UserResponse accept(
      @PathVariable String token, @RequestBody AcceptInvitationRequest body) {
    return acceptService.accept(token, body);
  }
}
