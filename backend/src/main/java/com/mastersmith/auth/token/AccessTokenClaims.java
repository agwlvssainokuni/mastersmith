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

package com.mastersmith.auth.token;

import java.time.Instant;

/**
 * アクセストークン(JWT)が運ぶ値(値オブジェクト。永続化しない、entities.md AccessTokenClaims)。アクティブロール・メールアドレス・氏名は含めない(Q4=A)。
 *
 * @param sub userId
 * @param sid sessionId
 * @param iat 発行日時
 * @param exp 有効期限
 */
public record AccessTokenClaims(String sub, String sid, Instant iat, Instant exp) {}
