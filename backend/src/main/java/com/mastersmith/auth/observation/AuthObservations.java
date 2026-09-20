package com.mastersmith.auth.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * authentication-serviceの観測(スパン)の入口(nfr-design/observability-design.md NFR5.3)。ログイン・リフレッシュ・ログアウト・ロール選択・認証フィルタ・
 * Sessionの定期削除を、{@link ObservationRegistry}による観測に載せる。呼び出し元(HTTPリクエストの観測)のトレースコンテキストを継承する。
 *
 * <p><b>属性(キーと値)には、固定の低カーディナリティの分類だけを使う</b>({@code unit}・{@code reason}・{@code cache})。パスワード・トークン・鍵・
 * メールアドレス・氏名・{@code Authorization}ヘッダーの値・userId・sessionIdは、属性に含めない(NFR2.7)。
 *
 * <p>{@link ObservationRegistry}が構成されていない環境(スライスのテストなど)では、何も観測しない(NOOP)。
 */
@Component
public class AuthObservations {

  /** 何も観測しない既定の実装(Springの外で組み立てるテストなど)。 */
  public static final AuthObservations NOOP = new AuthObservations(ObservationRegistry.NOOP);

  private static final String UNIT_KEY = "unit";
  private static final String UNIT_VALUE = "authentication-service";

  private final ObservationRegistry registry;

  @Autowired
  public AuthObservations(ObjectProvider<ObservationRegistry> registry) {
    this(registry.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  public AuthObservations(ObservationRegistry registry) {
    this.registry = registry;
  }

  /** 処理を、指定の名前の観測の中で実行し、結果を返す。 */
  public <T> T observe(String name, Supplier<T> action) {
    return newObservation(name).observe(action);
  }

  /** 戻り値のない処理を、指定の名前の観測の中で実行する。 */
  public void run(String name, Runnable action) {
    newObservation(name).observe(action);
  }

  /** 処理を、指定の名前と、固定の分類(キーと値)の観測の中で実行し、結果を返す。 */
  public <T> T observe(String name, String key, String value, Supplier<T> action) {
    return newObservation(name).lowCardinalityKeyValue(key, value).observe(action);
  }

  /** 開始していない観測(属性の追加・手動の開始・停止を、呼び出し側が行う場合)。 */
  public Observation start(String name) {
    return newObservation(name).start();
  }

  private Observation newObservation(String name) {
    return Observation.createNotStarted(name, registry)
        .lowCardinalityKeyValue(UNIT_KEY, UNIT_VALUE);
  }
}
