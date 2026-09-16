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
 * {@code GET
 * /api/audit-log}のクエリパラメータ(page/pageSize/targetType)が範囲外・不正な場合の拒否を表す例外(security-design.md「クエリパラメータの入力検証」、NFR2.5関連、Q4確定)。
 *
 * <p>{@code AuditLogController}が{@code 400 Bad Request}(RFC
 * 9457形式のProblemDetails)へマッピングする。サイレントクランプは行わない設計判断による。
 */
public class AuditLogQueryValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public AuditLogQueryValidationException(String message) {
    super(message);
  }
}
