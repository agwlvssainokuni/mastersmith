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

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import com.mastersmith.usermanagement.dto.InviteUserRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.service.InvitationFacade;
import com.mastersmith.usermanagement.service.UserApplicationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C5(user-management REST API)の、管理者向けのユーザー管理: 一覧・招待・更新・無効化({@code /api/users}系、FR2、rules.md
 * BR4.10)。
 *
 * <p>コントローラは、操作者を{@link OperatorContext}(C15、認証フィルタが値を設定する)から読み、サービスへ渡すだけで、認可({@code
 * canAccessScreen}による実効権限の再検証)は、 サービスの入口で必ずサーバー側で行う(NFR2.1)。応答は、{@code passwordHash}・{@code
 * invitationToken}を持たない{@link UserResponse}のみ (NFR2.2)。エラーは、{@link UserApiExceptionAdvice}がRFC
 * 9457のProblemDetailsに変換する。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserApplicationService userService;
  private final InvitationFacade invitationFacade;
  private final OperatorContext operatorContext;

  public UserController(
      UserApplicationService userService,
      InvitationFacade invitationFacade,
      OperatorContext operatorContext) {
    this.userService = userService;
    this.invitationFacade = invitationFacade;
    this.operatorContext = operatorContext;
  }

  /** ユーザー一覧(W7)。email昇順の全件。 */
  @GetMapping
  public List<UserResponse> list() {
    return userService.list(operator());
  }

  /** ユーザー招待・再招待(W1、招待メールを送信する)。 */
  @PostMapping
  public ResponseEntity<UserResponse> invite(@RequestBody InviteUserRequest body) {
    UserResponse invited = invitationFacade.invite(operator(), body);
    return ResponseEntity.status(HttpStatus.CREATED).body(invited);
  }

  /** ユーザー情報の更新(W3)。更新可能な項目は{@code name}と{@code roleIds}のみ(それ以外の項目の指定は422)。 */
  @PutMapping("/{userId}")
  public UserResponse update(@PathVariable String userId, @RequestBody Map<String, Object> body) {
    return userService.update(operator(), userId, UpdateUserRequestFactory.from(body));
  }

  /** ユーザーの無効化・招待の取消(W4)。すでにdisabledなら、冪等に204。 */
  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> disable(@PathVariable String userId) {
    userService.disable(operator(), userId);
    return ResponseEntity.noContent().build();
  }

  /** 認証フィルタが設定した操作者(C15)。解決できなければnull(サービスの入口の認可が、401にする)。 */
  private Operator operator() {
    return operatorContext.current().orElse(null);
  }
}
