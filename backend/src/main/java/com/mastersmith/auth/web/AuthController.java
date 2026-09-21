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

package com.mastersmith.auth.web;

import com.mastersmith.auth.dto.ActiveRoleRequest;
import com.mastersmith.auth.dto.ActiveRoleResponse;
import com.mastersmith.auth.dto.LoginRequest;
import com.mastersmith.auth.dto.LoginResponse;
import com.mastersmith.auth.dto.RefreshRequest;
import com.mastersmith.auth.dto.RefreshResponse;
import com.mastersmith.auth.exception.AuthenticationRequiredException;
import com.mastersmith.auth.service.AuthenticationApplicationService;
import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C4(authentication-service REST API、FR2.7・FR3・FR4.2)。ログイン({@code POST
 * /api/auth/login})・リフレッシュ({@code POST /api/auth/refresh})・ログアウト({@code POST
 * /api/auth/logout})・アクティブロールの選択({@code PUT /api/auth/active-role})。
 *
 * <p>ログイン・リフレッシュは、認証を要しない(リフレッシュトークンが根拠)。ログアウト・ロール選択は、認証フィルタ({@code
 * BearerAuthenticationFilter})を通る。 操作者は、C15の{@link OperatorContext}から読む(認証フィルタが値を設定する)。例外は、{@link
 * AuthApiExceptionAdvice}が、RFC 9457のProblemDetailsに変換する。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationApplicationService service;
  private final OperatorContext operatorContext;

  public AuthController(AuthenticationApplicationService service, OperatorContext operatorContext) {
    this.service = service;
    this.operatorContext = operatorContext;
  }

  /** ログイン(W1)。 */
  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    return service.login(request.email(), request.password());
  }

  /** リフレッシュ(W2)。 */
  @PostMapping("/refresh")
  public RefreshResponse refresh(@RequestBody RefreshRequest request) {
    return service.refresh(request.refreshToken());
  }

  /** ログアウト(W3)。認証フィルタを通ったSessionだけを失効させる。 */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    service.logout(requireOperator());
    return ResponseEntity.noContent().build();
  }

  /** アクティブロールの選択(W4)。保持していないロールは403。 */
  @PutMapping("/active-role")
  public ActiveRoleResponse selectActiveRole(@RequestBody ActiveRoleRequest request) {
    return new ActiveRoleResponse(service.selectActiveRole(requireOperator(), request.roleId()));
  }

  private Operator requireOperator() {
    return operatorContext.current().orElseThrow(AuthenticationRequiredException::new);
  }
}
