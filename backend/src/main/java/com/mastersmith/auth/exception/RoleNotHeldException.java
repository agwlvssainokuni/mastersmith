package com.mastersmith.auth.exception;

/** 保持していないロール(空・nullを含む)の選択(BR5.9)。403({@code auth.role.not-held})。Sessionは変更しない。 */
public class RoleNotHeldException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RoleNotHeldException() {
    super("Role not held");
  }
}
