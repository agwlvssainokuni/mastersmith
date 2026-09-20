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
