package com.mastersmith.auth.token;

import java.util.Base64;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * JWTの署名の鍵を、{@link Environment}から直接読んで検証し、専用の型({@link SecretKeyMaterial})で保持する(NFR2.2・NFR4.4、BR5.5)。
 *
 * <p>鍵は、環境変数{@code MASTERSMITH_AUTH_JWT_SECRET}(キー{@code mastersmith.auth.jwt.secret})から与える。<b>標準のBase64</b>で表した文字列で、
 * デコード後が<b>32バイト(256ビット)以上</b>であること。未設定・Base64として不正・32バイト未満の場合は、起動時に失敗させる。{@code AuthProperties}の
 * 束縛の対象にしないのは、Spring Bootの束縛の失敗の診断が、拒否された値を表示するためである。
 *
 * <p>失敗の例外({@link IllegalStateException})のメッセージには、設定のキーの名前と理由(未設定・形式不正・長さ不足)だけを含める。値・値の断片・
 * デコードの例外のメッセージ(値を含みうる)は含めず、原因としても連鎖させない。リポジトリには、鍵の既定値も実値も置かない。
 */
@Component
public class JwtKeyProvider {

  /** 鍵の設定のキー。環境変数{@code MASTERSMITH_AUTH_JWT_SECRET}が、Spring Bootの緩やかな束縛でこのキーに対応する。 */
  public static final String KEY_PROPERTY = "mastersmith.auth.jwt.secret";

  /** 鍵の最小の長さ(バイト、256ビット)。HS256の鍵の強度の下限。 */
  public static final int MIN_KEY_BYTES = 32;

  private final SecretKeyMaterial key;

  public JwtKeyProvider(Environment environment) {
    this.key = load(environment.getProperty(KEY_PROPERTY));
  }

  /** 検証済みの鍵。 */
  public SecretKeyMaterial key() {
    return key;
  }

  private static SecretKeyMaterial load(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      throw new IllegalStateException(KEY_PROPERTY + " is not set (reason: missing)");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encoded.strip());
    } catch (IllegalArgumentException e) {
      // デコードの例外のメッセージは、値を含みうるため、連鎖させない。
      throw new IllegalStateException(KEY_PROPERTY + " must be standard Base64 (reason: format)");
    }
    if (decoded.length < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          KEY_PROPERTY
              + " must decode to at least "
              + MIN_KEY_BYTES
              + " bytes (reason: too short)");
    }
    return new SecretKeyMaterial(decoded);
  }
}
