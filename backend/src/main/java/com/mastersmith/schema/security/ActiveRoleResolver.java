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

package com.mastersmith.schema.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * C8(schema-introspector REST API)呼び出し元のアクティブロール(activeRoleId)を解決する拡張点(BR2.8)。
 *
 * <p><b>暫定実装であることの注記(code-generation-plan.md「前提事項: 認可基盤の拡張点定義」)</b>: authentication-service(U5、Bolt
 * 7相当)は本Bolt(schema-introspector)時点では未実装であり、Bearer
 * JWTの署名検証・有効期限確認は行わない。本インタフェースは、authentication-service実装時にJWT検証ベースの実装({@link
 * HeaderActiveRoleResolver}からの置き換え)へ差し替えるための拡張点として導入する。認可判定そのもの({@code
 * PermissionEngineApi.canAccessScreen})はコントローラ層でサーバー側再検証を必ず行うため(BR2.8、project.md
 * Mandated)、本拡張点の暫定実装であること自体が「実効権限の再検証」要件を弱めるものではない。
 */
public interface ActiveRoleResolver {

  /**
   * リクエストからactiveRoleIdを解決する。
   *
   * @return 解決できたactiveRoleId。解決できない場合(未指定等)は{@code null}
   */
  String resolveActiveRoleId(HttpServletRequest request);
}
