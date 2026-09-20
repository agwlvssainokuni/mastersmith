package com.mastersmith.common.security;

import java.util.Objects;

/**
 * 認証済みのリクエストの操作者(C15、機能設計BR5.12)。値オブジェクトで、永続化しない。
 *
 * <p>authentication-serviceの認証フィルタが、アクセストークンの{@code sub}・{@code sid}と、Sessionのアクティブロールから決めて設定し、他ユニット
 * (schema-introspector・user-management・menu-navigation・audit-logging)は、{@link OperatorContext}を通して読むだけである。
 *
 * <p>操作者が解決できる(認証済み)なら、{@code activeRoleId}がnullでも{@code Operator}は存在する。{@code
 * activeRoleId}がnull(未選択)であることは、認証エラー(401)ではなく権限なし(403)を意味する。読み取り側は、nullを自前で拒否せず、そのまま permission-engine
 * (C10)へ渡して、権限なしとして判定させる。
 *
 * @param userId アクセストークンの{@code sub}
 * @param sessionId アクセストークンの{@code sid}
 * @param activeRoleId Sessionのアクティブロール。未選択ならnull
 */
public record Operator(String userId, String sessionId, String activeRoleId) {

  public Operator {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(sessionId, "sessionId");
  }
}
