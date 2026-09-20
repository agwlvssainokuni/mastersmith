package com.mastersmith.auth.service;

import com.mastersmith.auth.config.AuthProperties;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.repository.SessionRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 起動時の全Sessionの失効の手段(security-design.md NFR2.5、reliability-design.md NFR4.7)。設定{@code
 * mastersmith.auth.session.revoke-all-on-startup}(既定false)をtrueにして起動すると、起動時に、{@code auth_session}のすべての行を削除する。
 * バックアップからの復元後や、JWTの鍵の漏えいの疑いのときの運用の手順の前提である(設定を戻さないまま起動を繰り返すと、そのたびに削除される)。
 *
 * <p>実行のタイミングは、Flywayの移行の後、<b>Webサーバーがリクエストを受け付ける前</b>である。{@link SmartInitializingSingleton}は、すべてのシングルトンの
 * 初期化(Flywayの移行を含む)が終わった後、Webサーバーの起動({@code SmartLifecycle})より前に実行される。{@code ApplicationRunner}は、Webサーバーの起動の後に
 * 実行され、その間に作られたSessionを削除しうるため、使わない。削除に失敗した場合は、起動を失敗させる(失効させるべきSessionを残したまま、受け付けない)。
 */
@Component
public class SessionStartupRevoker implements SmartInitializingSingleton {

  private final SessionRepository repository;
  private final AuthProperties properties;
  private final AuthEventLogger logger;
  private final TransactionTemplate tx;

  public SessionStartupRevoker(
      SessionRepository repository,
      AuthProperties properties,
      AuthEventLogger logger,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.properties = properties;
    this.logger = logger;
    this.tx = new TransactionTemplate(transactionManager);
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!properties.session().revokeAllOnStartup()) {
      return;
    }
    Integer deleted = tx.execute(status -> repository.deleteAllSessions());
    logger.allSessionsRevokedOnStartup(deleted == null ? 0 : deleted);
  }
}
