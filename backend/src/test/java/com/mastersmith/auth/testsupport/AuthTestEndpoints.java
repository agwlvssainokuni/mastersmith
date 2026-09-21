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

package com.mastersmith.auth.testsupport;

import com.mastersmith.auth.SessionContextApi;
import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import com.mastersmith.permission.PermissionEngineApi;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証フィルタ越しの確認のための、検証用のコントローラ(テスト専用)。list-engine(U10)・record-edit-engine(U11)などの、他ユニットのコントローラの代役として、{@link
 * OperatorContext}(C15)・{@link SessionContextApi}(C14)・{@link
 * PermissionEngineApi}(C10、認可の再検証)を使う。{@code @TestComponent}のため、コンポーネントスキャンには
 * 現れず、{@code @Import}したテストだけで有効になる。
 */
@RestController
@TestComponent
public class AuthTestEndpoints {

  /** 検証用の画面のscreenKey(予約キーではない、任意の値)。 */
  public static final String SCREEN_KEY = "auth-test-screen";

  private final OperatorContext operatorContext;
  private final SessionContextApi sessionContextApi;
  private final PermissionEngineApi permissionEngineApi;

  public AuthTestEndpoints(
      OperatorContext operatorContext,
      SessionContextApi sessionContextApi,
      PermissionEngineApi permissionEngineApi) {
    this.operatorContext = operatorContext;
    this.sessionContextApi = sessionContextApi;
    this.permissionEngineApi = permissionEngineApi;
  }

  /** 認証済みの操作者(C15)を返す。 */
  @GetMapping("/api/auth-test/whoami")
  public Map<String, Object> whoami() {
    Operator operator = operatorContext.current().orElseThrow();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("userId", operator.userId());
    body.put("sessionId", operator.sessionId());
    body.put("activeRoleId", operator.activeRoleId());
    return body;
  }

  /** 受け取ったボディの長さを返す(CSRFトークンなしのPOSTが通ること・ボディの上限の確認)。 */
  @PostMapping("/api/auth-test/echo")
  public Map<String, Object> echo(@RequestBody(required = false) String body) {
    return Map.of("length", body == null ? 0 : body.length());
  }

  /**
   * 権限制御のある検証用の処理。C14でSessionのアクティブロールを取得し(他ユニットが、Session ID
   * から取得する形)、C10の判定に渡す。アクティブロールがnull(未選択)でも、自前で拒否せず、 そのままC10へ渡し、権限なし(403)は、C10の判定の結果とする(BR5.12)。
   */
  @GetMapping("/api/auth-test/protected")
  public ResponseEntity<Map<String, Object>> protectedResource() {
    Operator operator = operatorContext.current().orElseThrow();
    String activeRoleId = sessionContextApi.getActiveRoleId(operator.sessionId());
    if (!permissionEngineApi.canAccessScreen(activeRoleId, SCREEN_KEY)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("denied", true));
    }
    return ResponseEntity.ok(Map.of("activeRoleId", activeRoleId));
  }
}
