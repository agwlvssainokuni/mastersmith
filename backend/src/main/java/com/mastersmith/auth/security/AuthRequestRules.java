package com.mastersmith.auth.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 認証の要否の規則のうち、パスの照合(security-design.md NFR2.1)。{@link AuthSecurityConfig}の{@code SecurityFilterChain}の規則と、{@link
 * BearerAuthenticationFilter}の適用範囲({@code shouldNotFilter})が、同じ{@link RequestMatcher}を使う。
 *
 * <p>認証を要しないAPIは、ログイン・リフレッシュ・招待受諾の3つだけである(BR5.11)。{@code /api/**}の残りは、既定で認証を要する(deny by default)。
 */
public final class AuthRequestRules {

  private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

  /** 認証を要しない3つのAPI(ログイン・リフレッシュ・招待受諾)。 */
  public static final RequestMatcher PUBLIC_API =
      new OrRequestMatcher(
          PATHS.matcher(HttpMethod.POST, "/api/auth/login"),
          PATHS.matcher(HttpMethod.POST, "/api/auth/refresh"),
          PATHS.matcher(HttpMethod.POST, "/api/users/invitations/*/accept"));

  /** {@code /api/**}のすべて。 */
  public static final RequestMatcher API = PATHS.matcher("/api/**");

  /** 認証(Bearerトークン)を要するパス: {@code /api/**}から、認証を要しない3つを除いたもの。 */
  public static final RequestMatcher REQUIRES_BEARER =
      new AndRequestMatcher(API, new NegatedRequestMatcher(PUBLIC_API));

  /** ヘルスチェック(完全一致のみ。ヘルスのグループ({@code /actuator/health/**})は含まない)。 */
  public static final RequestMatcher HEALTH = PATHS.matcher(HttpMethod.GET, "/actuator/health");

  /** actuatorのすべて。 */
  public static final RequestMatcher ACTUATOR = PATHS.matcher("/actuator/**");

  /** フロントエンドの静的ファイル・SPAのルート({@code GET}・{@code HEAD})。 */
  public static final RequestMatcher STATIC_RESOURCES =
      new OrRequestMatcher(PATHS.matcher(HttpMethod.GET, "/**"), PATHS.matcher(HttpMethod.HEAD, "/**"));

  private AuthRequestRules() {}
}
