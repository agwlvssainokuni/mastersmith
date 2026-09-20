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

package com.mastersmith.usermanagement.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * リクエストから操作者(userIdとactiveRoleId)を解決する拡張点(nfr-design/security-design.md
 * NFR2.1)。AuthenticationServiceコンポーネントは 呼び出さない(U5→U4の依存方向を保ち、循環を避けるため)。
 *
 * <p><b>暫定実装であることの注記(code-generation-plan.md 前提事項2)</b>: authentication-service(U5)は未実装のため、{@link
 * HeaderCurrentOperatorProvider}が暫定でリクエストヘッダーから読む。U5の実装時に、検証済みトークンのクレームから読む実装へ差し替える。認可判定 ({@code
 * canAccessScreen})は、サービスの入口で必ずサーバー側で行うため、暫定実装であっても、実効権限の再検証(project.md Mandated)は弱まらない。
 */
public interface CurrentOperatorProvider {

  /** 操作者を解決する。解決できない項目はnull(全く解決できない場合は{@link Operator#unresolved()})。 */
  Operator resolve(HttpServletRequest request);
}
