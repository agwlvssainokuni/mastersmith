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

package com.mastersmith.permission.exception;

import com.mastersmith.permission.entity.ScopeType;
import java.io.Serial;

/**
 * 権限管理者の明示的な操作を経ない権限昇格の試行(project.md Forbidden、rules.md BR3.8、C10契約)。
 *
 * <p>操作者自身の実効権限を超えるレベル・補助権限の割当が試みられた場合に送出する。メッセージ本文には対象ロールID・スコープ種別・スコープ参照は含めるが、
 * 操作者の実効権限の具体的な内部値までは含めない(security-design.mdの「エラー情報の安全な返却」の精神に倣う)。
 */
public class PermissionEscalationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public PermissionEscalationException(String roleId, ScopeType scopeType, String scopeRef) {
    super(
        "Permission escalation denied: roleId=%s, scopeType=%s, scopeRef=%s"
            .formatted(roleId, scopeType, scopeRef));
  }
}
