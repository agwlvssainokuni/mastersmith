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

package com.mastersmith.schema.exception;

import com.mastersmith.config.exception.FieldError;
import java.io.Serial;
import java.util.List;

/**
 * 読み取ったメタデータを、config-engineが、初期ドラフトとして受け付けなかったこと(rules.md
 * BR2.9、レビュー指摘R-02)。たとえば、config-engineの正規化表にない型(PostgreSQLの{@code uuid}・{@code
 * jsonb}、MySQL/MariaDBの{@code json}など)を持つカラムがあった場合に、{@code writeTableConfigDraft}が送出する{@code
 * ConfigValidationException}を、この例外へ変換する。
 *
 * <p>{@link SchemaIntrospectionException}のサブクラスのため、コントローラで、422(C8のValidationError)へマッピングされる。
 * フィールド単位のエラー({@link #getFieldErrors()})は、RFC 9457の{@code errors}に載せる。値・SQL文などの内部情報は、含まない。 {@code
 * writeTableConfigDraft}は、全体が1つのトランザクションで巻き戻るため、この例外の時点で、config-engineには何も書き込まれていない。
 */
public class SchemaDraftValidationException extends SchemaIntrospectionException {

  @Serial private static final long serialVersionUID = 1L;

  private final List<FieldError> fieldErrors;

  public SchemaDraftValidationException(List<FieldError> fieldErrors, Throwable cause) {
    super(
        "Draft validation failed in config-engine: %d field error(s)".formatted(fieldErrors.size()),
        cause);
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }
}
