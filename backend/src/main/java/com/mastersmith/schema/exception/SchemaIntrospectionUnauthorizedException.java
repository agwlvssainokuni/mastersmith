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
 * 操作者(認証済みのユーザー)を解決できなかったこと(401、C15の{@code OperatorContext}が操作者を返さない場合)。
 *
 * <p>{@code SchemaIntrospectionController}は、操作者そのものを解決できない場合は、権限判定({@code
 * canAccessScreen})を呼ばずに、自前で401を返す。
 * アクティブロールが未選択(操作者は解決済み)の場合とは区別する(その場合は、C10へ渡し、権限なし(403)とする。authentication-serviceの機能設計 BR5.12)。
 */
public class SchemaIntrospectionUnauthorizedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public SchemaIntrospectionUnauthorizedException() {
    super("Unable to resolve the operator");
  }
}
