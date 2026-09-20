package com.mastersmith.auth.token;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * sessionId({@code sid})を生成する(NFR2.3)。{@link SecureRandom}で128ビット(16バイト)を生成し、Base64URL(パディングなし、22文字)にする。
 * 推測不能性は、認証フィルタの{@code sub}とSessionの{@code userId}の照合の根拠である(鍵が漏えいしても、有効な{@code sid}を知らない攻撃者は、
 * なりすませない)。
 */
@Component
public class SessionIdGenerator {

  private static final int BYTES = 16;

  private final SecureRandom random;

  @Autowired
  public SessionIdGenerator() {
    this(new SecureRandom());
  }

  /** 乱数源を指定する(テストで、固定値を返す生成器を用いるため)。 */
  public SessionIdGenerator(SecureRandom random) {
    this.random = random;
  }

  public String generate() {
    byte[] bytes = new byte[BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
