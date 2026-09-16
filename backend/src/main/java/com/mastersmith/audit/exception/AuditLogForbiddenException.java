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

package com.mastersmith.audit.exception;

import java.io.Serial;

/**
 * 呼び出し元のアクティブロールが監査ログ閲覧画面へのアクセス権限を持たない場合の拒否を表す例外(functional-design/rules.md
 * BR7.10、C6契約のForbiddenレスポンス)。
 *
 * <p>{@code AuditLogController}が{@code 403 Forbidden}へマッピングする(project.md
 * Mandated「画面表示の出し分けだけに依存せず、必ずサーバー側で実効権限を再検証する」)。
 */
public class AuditLogForbiddenException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public AuditLogForbiddenException(String message) {
    super(message);
  }
}
