package com.mastersmith.auth.security;

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * C15の{@link OperatorContext}の実装(BR5.12)。認証フィルタ({@link BearerAuthenticationFilter})が、セキュリティコンテキストに設定した{@link
 * OperatorAuthentication}から、{@link Operator}を読む(読み取り専用)。ヘッダー({@code X-User-Id}・{@code X-Active-Role-Id})は、読まない。
 *
 * <p>認証されていない(認証フィルタを通っていない)リクエストでは、空を返す。アクティブロールがnullでも、{@link Operator}は存在する。
 */
@Component
public class SecurityContextOperatorContext implements OperatorContext {

  @Override
  public Optional<Operator> current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof OperatorAuthentication operatorAuthentication
        && operatorAuthentication.isAuthenticated()) {
      return Optional.of(operatorAuthentication.getPrincipal());
    }
    return Optional.empty();
  }
}
