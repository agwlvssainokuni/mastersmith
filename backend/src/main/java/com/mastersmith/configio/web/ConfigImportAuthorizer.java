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

package com.mastersmith.configio.web;

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import com.mastersmith.configio.exception.ConfigImportForbiddenException;
import com.mastersmith.configio.exception.ConfigImportUnauthorizedException;
import com.mastersmith.permission.PermissionEngineApi;
import org.springframework.stereotype.Component;

/**
 * 設定のエクスポート・インポートの認可(サーバー側で、必ず再検証する。project.md Mandated。C7、BR9.4、NFR2.1)。認証済みの操作者(共有契約C15の{@link
 * OperatorContext})を、要求ごとに1回取得し、解決できない場合は 401、解決できたら、permission-engine(C10)の{@code
 * canAccessScreen(activeRoleId, "config-import-export")}で判定し、falseなら403にする。
 *
 * <p>アクティブロールがnull(未選択)の場合は、ここでは拒否せず、そのままC10へ渡す(fail
 * closedで判定される。RBAC設定が1件もない初期状態の例外も、C10の判定に従い、本ユニットは解釈しない。 authentication-serviceのBR5.12)。{@code
 * ConfigImportExportController}と、束縛の例外を扱う{@code ConfigImportExceptionHandler}の、両方が、同じ処理を呼ぶ。
 */
@Component
public class ConfigImportAuthorizer {

  /** 設定管理画面の予約のscreenKey(permission-engineが予約・実装済み。rules.md BR3.15)。 */
  public static final String SCREEN_KEY = "config-import-export";

  private final OperatorContext operatorContext;
  private final PermissionEngineApi permissionEngineApi;

  public ConfigImportAuthorizer(
      OperatorContext operatorContext, PermissionEngineApi permissionEngineApi) {
    this.operatorContext = operatorContext;
    this.permissionEngineApi = permissionEngineApi;
  }

  /**
   * 認可に成功した操作者を返す。
   *
   * @throws ConfigImportUnauthorizedException 操作者を解決できない(401)
   * @throws ConfigImportForbiddenException {@code config-import-export}の権限がない(403)
   */
  public Operator authorize() {
    Operator operator =
        operatorContext.current().orElseThrow(ConfigImportUnauthorizedException::new);
    if (!permissionEngineApi.canAccessScreen(operator.activeRoleId(), SCREEN_KEY)) {
      throw new ConfigImportForbiddenException();
    }
    return operator;
  }
}
