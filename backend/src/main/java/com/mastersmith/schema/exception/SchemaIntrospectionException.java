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

import java.io.Serial;

/**
 * 対象RDBMS(業務データ用)への接続失敗・メタデータ読み取り失敗(未対応方言の検出を含む)を表すfail-fast例外(rules.md BR2.9、project.md
 * Mandated「設定定義自体の誤り...はfail fastする」)。
 *
 * <p>このコントローラ境界での{@code 422 Unprocessable Content}(C8のValidationErrorレスポンス)へのマッピングは{@code
 * SchemaIntrospectionController}が担う。呼び出し元へ公開するメッセージ({@link #getMessage()})には、接続文字列・パスワード等の機微情報を含めない
 * (security-design.md NFR2.2、詳細な例外原因はサーバー側ログにのみ記録する)。
 */
public class SchemaIntrospectionException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public SchemaIntrospectionException(String message) {
    super(message);
  }

  public SchemaIntrospectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
