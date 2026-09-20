package com.mastersmith.auth.security;

import com.mastersmith.common.security.Operator;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * 認証フィルタ({@link BearerAuthenticationFilter})が、セキュリティコンテキストに設定する認証済みの操作者({@link Operator})。認証情報(credentials)は
 * 持たない。権限(authorities)は持たない(権限判定は、各ユニットが、permission-engine(C10)で行う。認証と認可の分担)。
 */
public final class OperatorAuthentication extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final Operator operator;

  public OperatorAuthentication(Operator operator) {
    super(List.of());
    this.operator = operator;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Operator getPrincipal() {
    return operator;
  }

  @Override
  public String getName() {
    return operator.userId();
  }
}
