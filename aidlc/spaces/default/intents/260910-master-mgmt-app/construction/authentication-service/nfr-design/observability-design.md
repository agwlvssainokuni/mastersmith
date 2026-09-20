# Observability Design — authentication-service (U5)

`nfr-requirements/observability-requirements.md`の各要件を、設計に落とし込む。要件のNFR5(HTTPリクエストのレイテンシ・エラー率、ヘルスチェック、構造化ログ(JSON、リクエストID付与))は、共通基盤の計装をそのまま利用し、以下は本ユニット固有の設計である。

## NFR5.1: メトリクス

Micrometer(Spring Boot標準)の`MeterRegistry`(アプリ全体で共有)に、`AuthMetrics`(専用の部品)が、次のメーターを登録する。メーターの名前は、Micrometerの規約(ドット区切り、カウンタに`_total`を付けない)で登録し、エクスポートの形式(Prometheus形式では`_total`が付く、OTLPではドット区切りのまま)は、共通基盤のエクスポートの設定に従う([assumption]。エクスポートの形式の確定は、共通基盤の作業で、user-managementのNFR Designも同じ保留としている)。下の表の、要件の名前(`auth_login_failed_total`など)は、エクスポート後の名前の目安である。

**ラベル(タグ)に、個人を特定しうる値(メールアドレス・氏名・userId・sessionId)、トークン、鍵を含めない**(security-design.md NFR2.7)。

| Micrometerの名前 | 種類 | 要件の名前 | タグ | 増やす契機 |
|---|---|---|---|---|
| `auth.login.failed` | カウンタ | `auth_login_failed_total` | なし | ログインが401で終了したとき(原因を区別せず数える、BR5.14) |
| `auth.account.locked` | カウンタ | `auth_account_locked_total` | なし | 予約の更新が、しきい値に達して`locked_until`を設定したとき(`LoginAttemptGate.reserve`) |
| `auth.refresh.token.reuse.detected` | カウンタ | `auth_refresh_token_reuse_detected_total` | なし | 猶予を超えた再使用を検知して、Sessionを失効させたとき |
| `auth.login.hash.capacity.exceeded` | カウンタ | `auth_login_hash_capacity_exceeded_total` | なし | ログインが、`HashCapacityExceededException`で503になったとき(実際の検証・ダミーの検証のどちらも) |
| `auth.filter.unauthorized` | カウンタ | `auth_filter_unauthorized_total` | `reason`(`missing`・`invalid`・`expired`・`session_inactive`) | 認証フィルタが401を返したとき |
| `auth.refresh.reuse.within.grace` | カウンタ | `auth_refresh_reuse_within_grace_total`(派生指標) | なし | 猶予内の再使用を、401だけで、Sessionを失効させずに返したとき |
| `auth.db.unavailable` | カウンタ | `auth_db_unavailable_total`(派生指標) | なし | 内部設定DBの障害で、503を返したとき(`AuthStorageUnavailableException`) |
| `auth.login.duration` | タイマー(ヒストグラム) | `auth_login_duration_seconds`(派生指標) | なし | ログインの所要時間(NFR1.1の確認用) |
| `auth.filter.duration` | タイマー(ヒストグラム) | `auth_filter_duration_seconds`(派生指標) | `cache`(`hit`・`miss`) | 認証フィルタの処理時間(NFR1.2の確認用) |
| `cache.gets`(Micrometerの標準のキャッシュのメトリクス) | カウンタ | `auth_session_cache_requests_total`(派生指標) | `cache=auth-session`、`result`(`hit`・`miss`) | Caffeineの統計を、`CaffeineCacheMetrics`で登録する |

- **要件との差**(いずれも[assumption]): (1)`reason`に、`missing`(`Authorization`ヘッダーがない、またはBearerでない)を加えた。走査・認証なしの呼び出しと、トークン自体の不備を、区別するためである(security-design.md NFR2.2)。(2)キャッシュのヒット・ミスは、独自のカウンタではなく、Micrometerの標準のキャッシュのメトリクスを使う。内容は、要件の`auth_session_cache_requests_total`と同じである。
- `reason`は、値が4つに固定された列挙型(`UnauthorizedReason`)で、任意の文字列を、タグに使えない。
- 計装の場所: `BearerAuthenticationFilter`(`auth.filter.*`)、`LoginAttemptGate`(`auth.account.locked`)、`AuthenticationApplicationService`(`auth.login.*`・`auth.refresh.*`)、`AuthExceptionTranslator`(`auth.db.unavailable`)。

### アラートの暫定のしきい値

NFR Requirementsの暫定値を、そのまま置く([assumption]。運用フェーズが本MVPスコープ外で、確定する担当ステージがないため。実際に運用する時点で、運用の担当者が実測に基づいて見直す)。しきい値は、5分間の増加数で評価する。

| 対象 | しきい値 | 意味 |
|---|---|---|
| `auth.login.failed` | 5分間に30回以上 | 総当たり・パスワードスプレーの疑い |
| `auth.account.locked` | 5分間に3回以上 | 複数のアカウントが続けてロックされている(ロックの悪用・パスワードスプレーの疑い) |
| `auth.refresh.token.reuse.detected` | 5分間に1回以上 | トークンの盗用の疑い |
| `auth.login.hash.capacity.exceeded` | 5分間に3回以上 | ログインの混雑(実行環境のCPUコア数・同時実行の上限の見直し) |
| `auth.filter.unauthorized`(`reason=invalid`) | 5分間に10回以上 | 偽造・改ざんされたトークンの送信の疑い。鍵の入れ替えの直後の一時的な増加は許容する |

`reason=expired`・`reason=session_inactive`・`reason=missing`は、通常の利用でも起こるため、アラートの対象にしない。

## NFR5.2: ログ

- **形式**: 構造化ログ(JSON)。リクエストID(共通基盤が付与し、MDCに入れる)は、すべてのログに付く。`BearerAuthenticationFilter`は、認証に成功したリクエストの間、MDCに`userId`と`sessionId`を追加し、リクエストの終了時に、必ず取り除く(スレッドの再利用による、別のリクエストへの混入を防ぐ)。
- **窓口**: 認証の出来事のログは、`AuthEventLogger`だけが出力する(security-design.md NFR2.7)。引数は、userId・sessionId・roleId・分類(列挙型)・件数・所要時間に限る。
- **出来事とレベル**:

| 出来事 | レベル | 記録する内容 |
|---|---|---|
| ログイン成功 | INFO | userId・sessionId |
| ログイン失敗 | INFO | 失敗したという事実だけ(リクエストID)。原因の区別・試行されたメールアドレスは記録しない(BR5.14) |
| アカウントのロックが有効になった | WARN | userId・`lockedUntil` |
| 無効になったトークンの再使用の検知(Sessionの失効) | WARN | userId・sessionId |
| 猶予内の再使用(401のみ) | INFO | sessionId |
| リフレッシュ時のユーザーの無効化の検知(Sessionの失効) | INFO | userId・sessionId |
| ログアウト(Sessionの失効) | INFO | userId・sessionId |
| アクティブロールの選択・リフレッシュによる変更 | INFO | userId・sessionId・roleId |
| ハッシュ計算の待機超過による503 | WARN | リクエストIDだけ |
| 内部設定DBの障害による503 | ERROR | 例外の種類(メッセージ全文・SQLは出さない) |
| 認証フィルタの401 | DEBUG | 分類(`reason`)だけ。トークンの内容は出さない |
| Sessionの定期削除 | INFO(完了)・ERROR(失敗) | 削除した行数・所要時間、失敗時は例外の種類 |
| 起動時の設定の検証の失敗 | ERROR | 誤っている設定のキー(値は出さない) |

- 認証の出来事は、監査ログ(FR8)には発行しない(機能設計のAssumptions)。追跡は、構造化ログとメトリクスで行う。

## NFR5.3: 分散トレーシング

- Micrometerの`Observation`で、`auth.login`・`auth.refresh`・`auth.logout`・`auth.active-role`・`auth.filter`・`auth.session.cleanup`の各処理を、スパンとして記録する。呼び出し元からのトレースコンテキストを継承する(トレースの実装(Micrometer Tracingか、OTELのJavaエージェントか)は、共通基盤の作業で確定する。user-managementのNFR Designと同じ保留)。
- **スパンの属性**: 低カーディナリティの分類(`reason`・`cache`)だけを付ける。パスワード・トークン・鍵・メールアドレス・氏名・`Authorization`ヘッダーの値・userId・sessionIdは、属性に含めない(security-design.md NFR2.7)。HTTPの自動計装が、リクエストヘッダーを属性として収集しない設定にする。
- 認証系のAPIは、URLのパスに認証情報を含まない(C4は、認証情報をリクエストのボディで受け渡す)ため、パスの実際の値を、そのまま用いてよい。

## NFR5.4: ヘルスチェック

- authentication-serviceは、内部設定DBへの読み取り専用ヘルスチェックに参加する(アプリケーション全体のヘルスチェックの一部。ユニット単体の独立したヘルスチェックのエンドポイントは設けない)。
- JWTの鍵の妥当性は、起動時に検証する(reliability-design.md NFR4.4)。ヘルスチェックには含めない。
- Sessionの定期削除の失敗は、アプリケーション全体をunhealthyにしない。ERRORログで検知する(NFR5.2)。
- `/actuator/health`は、認証なしで、状態(UP・DOWN)だけを返す(security-design.md NFR2.1)。

## NFR5.5: ダッシュボード

NFR5.1のメトリクスを、運用フェーズ(本MVPスコープ外)で構築されるダッシュボードの候補指標として記録する。パネルの候補は、次のとおり。

- ログイン失敗の数・ロックの発生の数・ハッシュ待機超過の503の数(時系列)。
- 盗用の疑い(再使用の検知)の数と、猶予内の再使用の数(猶予の長さの見直し)。
- 認証フィルタの401の数(`reason`別)。
- ログインの所要時間・認証フィルタの処理時間(`hit`・`miss`別)のp95。Sessionのキャッシュのヒット率。
- 内部設定DBの障害による503の数。

本ステージでは、具体的なダッシュボードの設計は行わない。

## 根拠

- 要件: `nfr-requirements/observability-requirements.md`(NFR5.1〜NFR5.5)、`nfr-requirements/performance-requirements.md`(NFR1.1・NFR1.2)、`nfr-requirements/security-requirements.md`(NFR2.2・NFR2.7)、`nfr-requirements/reliability-requirements.md`(NFR4.2)、`nfr-requirements/scalability-requirements.md`(NFR3.2)、`nfr-requirements/tech-stack-decisions.md`、`inception/requirements-analysis/requirements.md`のNFR5・FR14
- 機能設計: `functional-design/functional-spec.md`、`rules.md`(BR5.2・BR5.3・BR5.6・BR5.11・BR5.14・BR5.15)
- 契約: `inception/contract-design/contract-summary.md` のC4・C11・C14
- 質問回答: `nfr-design-questions.md` Q3
