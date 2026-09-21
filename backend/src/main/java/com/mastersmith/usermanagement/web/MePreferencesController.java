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

import com.mastersmith.common.security.OperatorContext;
import com.mastersmith.usermanagement.dto.UserPreferenceDto;
import com.mastersmith.usermanagement.service.UserPreferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C5の、ユーザー単位の表示設定の取得・更新({@code /api/me/preferences}、FR9.1・FR10.1、rules.md
 * BR4.8)。認証済み(操作者のuserIdを解決できる)であれば、
 * 誰でも自分自身の設定を操作できる。activeRoleIdには依存しない(ロール選択前でも使える)。対象のuserIdは、常に操作者のuserIdで、リクエストのボディ・
 * パラメータからは受け取らない。
 */
@RestController
@RequestMapping("/api/me/preferences")
public class MePreferencesController {

  private final UserPreferenceService preferenceService;
  private final OperatorContext operatorContext;

  public MePreferencesController(
      UserPreferenceService preferenceService, OperatorContext operatorContext) {
    this.preferenceService = preferenceService;
    this.operatorContext = operatorContext;
  }

  @GetMapping
  public UserPreferenceDto get() {
    return preferenceService.get(operatorContext.current().orElse(null));
  }

  @PutMapping
  public UserPreferenceDto update(@RequestBody UserPreferenceDto body) {
    return preferenceService.update(operatorContext.current().orElse(null), body);
  }
}
