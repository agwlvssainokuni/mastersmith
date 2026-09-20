package com.mastersmith.auth.service;

import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.UserAccountLookupApi;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * user-managementのC11({@link UserAccountLookupApi})の呼び出しを包む、authentication-service側のアダプタ(reliability-design.md NFR4.2)。
 *
 * <p>U4の内部設定DBの障害(接続の取得の失敗・タイムアウトなど)を、{@link
 * com.mastersmith.auth.exception.AuthStorageUnavailableException}(503)に変換する。{@link
 * com.mastersmith.usermanagement.HashCapacityExceededException}は、変換せず、そのまま伝える(呼び出し元が、補償と503への変換を行う)。
 *
 * <p>C11の各メソッドは、トランザクションの外で呼ぶ(ハッシュ計算の許可を保持している間はDB接続を取らない、という資源の取得順序の不変条件。BR5.15)。呼び出し元
 * ({@link AuthenticationApplicationService})が、外側のトランザクションを作らないことで、これを保つ。
 */
@Component
public class UserAccountClient {

  private final UserAccountLookupApi api;
  private final AuthExceptionTranslator translator;

  public UserAccountClient(UserAccountLookupApi api, AuthExceptionTranslator translator) {
    this.api = api;
    this.translator = translator;
  }

  public Optional<UserAccount> findByEmail(String email) {
    return translator.translate(() -> api.findByEmail(email));
  }

  public Optional<UserAccount> findByUserId(String userId) {
    return translator.translate(() -> api.findByUserId(userId));
  }

  public boolean verifyPasswordHash(String userId, String rawPassword) {
    return translator.translate(() -> api.verifyPasswordHash(userId, rawPassword));
  }

  public void dummyVerify(String rawPassword) {
    translator.translate(() -> api.dummyVerify(rawPassword));
  }

  public boolean isDisabled(String userId) {
    return translator.translate(() -> api.isDisabled(userId));
  }
}
