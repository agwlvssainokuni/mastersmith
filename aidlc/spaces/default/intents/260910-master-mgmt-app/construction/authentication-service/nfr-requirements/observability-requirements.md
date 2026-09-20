# Observability Requirements — authentication-service (U5)

要件のNFR5に従い、HTTPリクエストのレイテンシ・エラー率、ヘルスチェック、構造化ログ(JSON、リクエストID付与)をOTEL基盤へエクスポートできること。本ユニットにもそのまま適用し、以下は本ユニット固有の追加事項である。

## NFR5.1: メトリクス

Q7=Aにより、次の5つをカウンタとして記録し、OTELへエクスポートする。異常な頻発時にアラートを出せるようにする。メトリクスのラベルには、個人を特定しうる値(メールアドレス・氏名・userId・sessionId)、トークン、鍵を含めない(NFR2.7)。実装はMicrometer(Spring Boot標準)で行い、他のユニットと同じ基盤に載せる(tech-stack-decisions.md)。

| メトリクス | 種類 | 内容 | ラベル |
|---|---|---|---|
| `auth_login_failed_total` | カウンタ | ログインが401で失敗した回数。**原因(登録されていない・パスワードの誤り・ロック中・無効化済み・招待中)を区別せずに数える**(rules.md BR5.2・BR5.14)。パスワードスプレー・総当たりのサイン。user-managementのQ5で、認証サービス側で計測すると決めた「パスワード検証の失敗」を含む | なし |
| `auth_account_locked_total` | カウンタ | アカウントのロックが有効になった回数(予約の更新が、しきい値に達して、`lockedUntil`を設定した回数、BR5.3)。多数のアカウントで続くときは、ロックの悪用・パスワードスプレーのサイン(NFR2.12) | なし |
| `auth_refresh_token_reuse_detected_total` | カウンタ | 猶予を超えた、無効になったリフレッシュトークンの再使用を検知し、Sessionを失効させた回数(BR5.6)。トークンの盗用のサイン | なし |
| `auth_login_hash_capacity_exceeded_total` | カウンタ | ログインが、ハッシュ計算の同時実行数の上限の超過により、503で終了した回数(BR5.15)。実際の検証・ダミーの検証のどちらも数える。ログインの混雑のサイン(NFR1.4)。user-managementの`user_password_hash_rejected_total`は、ハッシュ計算の許可を取れなかった回数を、招待受諾を含めて数えるのに対し、本メトリクスは、ログインのHTTP応答としての503を数える | なし |
| `auth_filter_unauthorized_total` | カウンタ | 認証フィルタが401を返した回数(BR5.11) | `reason`(次の3つのいずれか。`expired`=アクセストークンの有効期限切れ、`invalid`=署名・形式・必須の値・`sub`とSessionの`userId`の不一致など、トークン自体の不備、`session_inactive`=Sessionが存在しない、または有効でない) |

[assumption] `auth_filter_unauthorized_total`の`reason`は、Q7で確認した内容に、本ステージで加えた分類である。アクセストークンの有効期限切れは、正常な利用でも、10分ごとに起こるため、理由を区別しない単一の数では、アラートの判断に使えない(下記のアラートは、`invalid`だけを対象にする)。値は、3つの固定の分類だけで、個人を特定しうる値を含まない。BR5.14が、外部向けに区別を出さないと定めているのは、ログインの失敗の原因であり、認証フィルタの401の内部の分類(応答の内容は、NFR2.8のとおり同一)は、これに反しない。

[assumption] Q7で確認した上記5つに加え、NFR1・NFR3・NFR4の充足を確認するための派生指標として、次を設ける。要件に明示がないため、必要ならNFR Designで見直す。

- `auth_refresh_reuse_within_grace_total`(カウンタ): 再送の猶予内に、無効になったトークンが提出され、401だけで、Sessionを失効させなかった回数(BR5.6、NFR2.3)。猶予(既定10秒)の長さの見直しの判断に使う(残余リスク4)。
- `auth_db_unavailable_total`(カウンタ): 内部設定DBの障害により、503を返した回数(NFR4.2)。
- `auth_login_duration_seconds`(ヒストグラム): ログインの所要時間(NFR1.1の「3秒以内(p95)」の確認用)。
- `auth_filter_duration_seconds`(ヒストグラム): 認証フィルタの処理時間(NFR1.2の確認用)。`result`ラベル(`hit`・`miss`)を持つ。
- `auth_session_cache_requests_total`(カウンタ): Sessionのキャッシュの参照の回数(`result`ラベル: `hit`・`miss`)。キャッシュの容量(NFR3.2)が足りているかの確認用。

### アラートの暫定のしきい値

アラートのしきい値は、次の暫定値を置く([assumption]。運用フェーズが本MVPスコープ外で、確定する担当ステージがないため、暫定値として置く)。実際に運用する時点で、運用の担当者が実測に基づいて見直す。

| 対象 | しきい値 | 意味 |
|---|---|---|
| `auth_login_failed_total` | 5分間に30回以上 | 総当たり・パスワードスプレーの疑い(数十名規模で、通常の入力ミスが、この頻度で続くことは考えにくい) |
| `auth_account_locked_total` | 5分間に3回以上 | 複数のアカウントが続けてロックされている。ロックの悪用・パスワードスプレーの疑い |
| `auth_refresh_token_reuse_detected_total` | 5分間に1回以上 | トークンの盗用の疑い(1回でも起こったら通知する) |
| `auth_login_hash_capacity_exceeded_total` | 5分間に3回以上 | ログインの混雑。実行環境のCPUコア数や同時実行の上限の見直しの判断材料 |
| `auth_filter_unauthorized_total`(`reason=invalid`) | 5分間に10回以上 | 偽造・改ざんされたトークンの送信の疑い、または鍵の入れ替えの直後の一時的な増加(鍵の入れ替えの直後は、通知を許容する) |

`reason=expired`と`reason=session_inactive`の401は、通常の利用でも起こる(有効期限切れ・ログアウトのあとの再利用)ため、アラートの対象にしない。

## NFR5.2: ログ

構造化ログ(JSON、リクエストIDを付与、要件のFR14.1)に、次を記録する。ユーザーの識別には、userIdとsessionIdを用い、メールアドレス・氏名・パスワード・トークン・鍵は出力しない(NFR2.7)。

| 出来事 | レベル | 記録する内容 |
|---|---|---|
| ログイン成功 | INFO | userId・sessionId |
| ログイン失敗 | INFO | リクエストIDと、失敗したという事実だけ。原因(未登録・パスワードの誤り・ロック中など)の区別を出さない(BR5.14)。試行されたメールアドレスは記録しない |
| アカウントのロックが有効になった | WARN | userId・`lockedUntil` |
| 無効になったトークンの再使用の検知(Sessionの失効) | WARN | userId・sessionId |
| リフレッシュ時のユーザーの無効化の検知(Sessionの失効) | INFO | userId・sessionId |
| ログアウト(Sessionの失効) | INFO | userId・sessionId |
| アクティブロールの選択・リフレッシュによる変更 | INFO | userId・sessionId・roleId(ロールの識別子は、個人を特定する値ではない) |
| ハッシュ計算の待機超過による503 | WARN | リクエストIDだけ |
| 内部設定DBの障害による503 | ERROR | リクエストID・例外の種類(接続の取得の超過・ロックの待機の超過など)。例外のメッセージ全文や、SQLは出さない |
| Sessionの定期削除 | INFO(結果)・ERROR(失敗) | 削除した行数・所要時間。失敗は例外の種類 |
| 起動時の設定の検証の失敗 | ERROR | 誤っている設定のキー(値は出さない、NFR4.4) |
| 認証フィルタの401 | DEBUG | リクエストIDと、分類(NFR5.1の`reason`)。トークンの内容は出さない |

認証の出来事(ログイン・ログアウト・ロック・盗用の疑い)は、監査ログ(FR8)には発行しない(機能設計のAssumptionsと同じ)。追跡は、構造化ログと、上のメトリクスで行う。

## NFR5.3: 分散トレーシング

NFR5(OTEL基盤へのエクスポート)に従い、`/api/auth/*`の各エンドポイントと、認証フィルタ、C11の各メソッド、Sessionの定期削除を、スパンとして記録する。呼び出し元からの分散トレースコンテキストを継承する。

- スパンの名前と属性には、パスワード・トークン・鍵・メールアドレス・氏名・`Authorization`ヘッダーの値を含めない(NFR2.7)。HTTPの自動計装が、ヘッダーやボディを属性として収集しない設定にする。
- 認証系のAPIは、URLのパスに認証情報を含まない(C4は、認証情報をリクエストのボディで受け渡す)ため、パスの実際の値を、そのまま属性に用いてよい。

## NFR5.4: ヘルスチェック

authentication-serviceは、内部設定DBへの読み取り専用ヘルスチェックに参加する(アプリケーション全体のヘルスチェックの一部。ユニット単体の独立したヘルスチェックエンドポイントは設けない)。JWTの署名の鍵の妥当性は、起動時に検証する(NFR4.4)ため、ヘルスチェックの対象にしない。Sessionの定期削除の失敗は、アプリケーション全体をunhealthyにせず、ERRORログで検知する(NFR5.2)。

## NFR5.5: ダッシュボード

NFR5.1のメトリクスは、運用フェーズ(本MVPスコープ外)で構築されるダッシュボードの候補指標として記録しておく。本ステージでは具体的なダッシュボード設計は行わない。

## 根拠

- 要件: `inception/requirements-analysis/requirements.md` のNFR5(可観測性)・FR14
- 機能設計: `construction/authentication-service/functional-design/functional-spec.md`、`rules.md`(BR5.2・BR5.3・BR5.6・BR5.11・BR5.14・BR5.15)
- 契約: `inception/contract-design/contract-summary.md` のC4・C11・C14
- 質問回答: `nfr-requirements-questions.md` Q7
- 他ユニットの要件: `construction/user-management/nfr-requirements/observability-requirements.md` NFR5.1(`user_password_hash_rejected_total`)
