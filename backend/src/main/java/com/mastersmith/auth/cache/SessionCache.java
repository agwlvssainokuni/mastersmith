package com.mastersmith.auth.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.mastersmith.auth.config.AuthProperties;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.service.AuthExceptionTranslator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 認証フィルタのSession参照のキャッシュ(NFR3.2、reliability-design.md NFR4.3)。Caffeine(プロセス内)で、キーは{@code sessionId}、値は{@link
 * SessionState}。
 *
 * <ul>
 *   <li>最大件数・書き込みからの有効期間(TTL、安全網)は設定({@code mastersmith.auth.cache.*})。統計を記録し、Micrometerの標準のキャッシュのメトリクス
 *       ({@code cache.gets}、タグ{@code cache=auth-session})で、ヒット・ミスを記録する。
 *   <li>読み込みは、{@code cache.get(sessionId, loader)}(キーごとの原子的な読み込み)。loaderは、主キーで1行を読む。Sessionが存在しない場合は、キャッシュに
 *       入れない(否定的な結果を保持しない)。読み込みが内部設定DBの障害で失敗した場合は、Caffeineは例外を保持せず、次のリクエストで、再び読み込みを試みる。
 *   <li>無効化は、Sessionを書き換える更新が<b>コミットされた後</b>に、{@link #invalidate}を呼ぶ。読み込みの途中に別のスレッドが無効化を呼んだ場合、無効化は、
 *       進行中の読み込みが終わるのを待ってから、その結果を取り除く(Caffeineの、キーごとの原子性による)。
 * </ul>
 */
@Component
public class SessionCache {

  /** キャッシュの名前(メトリクスのタグ{@code cache})。 */
  public static final String CACHE_NAME = "auth-session";

  /**
   * キャッシュの引き当ての結果。
   *
   * @param state Sessionの内容。存在しなければnull
   * @param hit キャッシュのヒットか(falseは、DBから読み込んだ)
   */
  public record Lookup(SessionState state, boolean hit) {}

  private final Cache<String, SessionState> cache;
  private final SessionRepository repository;
  private final AuthExceptionTranslator translator;

  @Autowired
  public SessionCache(
      SessionRepository repository,
      AuthProperties properties,
      MeterRegistry meterRegistry,
      AuthExceptionTranslator translator) {
    this(
        repository,
        properties.cache().maxSize(),
        properties.cache().ttl(),
        meterRegistry,
        translator,
        Ticker.systemTicker());
  }

  /** 時間の進みを指定する(テストで、TTLの経過を、実時間を待たずに確認するため)。 */
  public SessionCache(
      SessionRepository repository,
      long maxSize,
      Duration ttl,
      MeterRegistry meterRegistry,
      AuthExceptionTranslator translator,
      Ticker ticker) {
    this.repository = repository;
    this.translator = translator;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttl)
            .ticker(ticker)
            .recordStats()
            .build();
    CaffeineCacheMetrics.monitor(meterRegistry, cache, CACHE_NAME);
  }

  /**
   * Sessionを引く。キャッシュにあれば返し、なければ主キーで1行を読み込む(存在しなければ、キャッシュに入れない)。
   *
   * @throws com.mastersmith.auth.exception.AuthStorageUnavailableException 読み込みが、内部設定DBの障害で失敗した場合
   */
  public Lookup lookup(String sessionId) {
    boolean[] loaded = {false};
    SessionState state =
        cache.get(
            sessionId,
            key -> {
              loaded[0] = true;
              return load(key);
            });
    return new Lookup(state, !loaded[0]);
  }

  /** Sessionを引く(存在しなければ空)。 */
  public Optional<SessionState> find(String sessionId) {
    return Optional.ofNullable(lookup(sessionId).state());
  }

  /** キャッシュの該当のキーを無効化する。Sessionを書き換える更新が、コミットされた後に呼ぶ。 */
  public void invalidate(String sessionId) {
    cache.invalidate(sessionId);
  }

  /** キャッシュの現在の件数(テスト・診断用)。 */
  public long estimatedSize() {
    return cache.estimatedSize();
  }

  private SessionState load(String sessionId) {
    return translator.translate(
        () -> repository.findById(sessionId).map(SessionState::from).orElse(null));
  }
}
