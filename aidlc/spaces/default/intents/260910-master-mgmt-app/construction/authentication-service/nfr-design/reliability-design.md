# Reliability Design — authentication-service (U5)

`nfr-requirements/reliability-requirements.md`の各要件を、設計に落とし込む。

## NFR4.1: 更新の原子性と競合の扱い

### トランザクションの境界

`AuthenticationApplicationService`の各メソッドは、外側のトランザクションを作らない(`@Transactional`を付けない)。内側の短いトランザクションを、`TransactionTemplate`で、明示的に区切る。トランザクションのタイムアウトは、**3秒**とする(内部設定DBが応答しない場合に、長く保持しない、NFR4.2)。C11の呼び出しは、トランザクションの外で行う(BR5.15、performance-design.md NFR1.3)。

| 処理 | トランザクション | 内容 |
|---|---|---|
| 予約 | 1つ(`LoginAttemptGate.reserve`) | `AccountLoginState`の、試行の枠の確保(下記) |
| 補償 | 1つ(`LoginAttemptGate.compensate`) | 条件付きの補償の更新 |
| ログイン成功 | 1つ | `AccountLoginState`のリセットと、Sessionの作成(同一トランザクション、NFR Requirementsの追補) |
| リフレッシュ | 1つ(条件付きの更新) | Sessionの検索は、更新とは別の読み取り。ローテーションの更新だけが、書き込みのトランザクション |
| 失効(ログアウト・盗用の検知・無効化の検知) | 1つ | statusを`revoked`にする(冪等) |
| ロール選択 | 1つ | `active_role_id`の更新 |

### 予約型のロック(`LoginAttemptGate.reserve`)

1つの短いトランザクションの中で、次を順に行う。時刻は、DBの時計ではなく、注入された`Clock`の値を、パラメータとして渡す(NFR4.6)。

1. 枠の確保の更新(ロック中でない、かつ、しきい値未満、またはロックの解除済みの場合だけ、1行を更新する):

```
UPDATE account_login_state SET
  consecutive_failures = CASE WHEN locked_until IS NOT NULL THEN 1 ELSE consecutive_failures + 1 END,
  generation  = CASE WHEN locked_until IS NOT NULL THEN generation + 1 ELSE generation END,
  locked_until = CASE WHEN (CASE WHEN locked_until IS NOT NULL THEN 1 ELSE consecutive_failures + 1 END) >= :threshold
                      THEN :now + :lockDuration ELSE NULL END
WHERE user_id = :u
  AND (locked_until <= :now OR (locked_until IS NULL AND consecutive_failures < :threshold))
```

   上のSET句の各式は、更新前の値で評価される(標準のSQLの動作。内部設定DBのH2はこの動作で、ロックの回数と解除の判定は、これに依存する。Code Generationのテストで確認する)。
2. 更新の件数が1: 確保できた。同じトランザクションで、行を読み直し、`generation`と、しきい値に達してロックを設定した場合の`locked_until`の値を、予約(reservation)として保持する。
3. 更新の件数が0: 行が存在しない、または、ロック中(`locked_until`が未来)、または、想定外の状態(`consecutive_failures`がしきい値以上で`locked_until`が空)のいずれかである。行を読む。
   - 行がなければ: 行を作る(`consecutive_failures`=1。しきい値が1なら、`locked_until`も設定)。同時の作成で、主キーの一意制約に違反した場合は、1回だけ、手順1からやり直す。
   - ロック中: 確保できない(ロック期間を延長しない、失敗も数えない)。
   - 想定外の状態(しきい値以上で`locked_until`が空): `locked_until`を、現在時刻に、ロック時間を加えた値に設定して(自己修復)、確保できない。

- 更新の対象の行は、更新のトランザクションが終わるまで、他のトランザクションの更新を待たせる(行のロック)。そのため、同時の試行が何件あっても、確保できる枠は、しきい値を超えない。

### 補償の更新(`LoginAttemptGate.compensate`)

ハッシュ計算の上限超過(503)のとき、確保した枠を返す。

```
UPDATE account_login_state SET
  consecutive_failures = consecutive_failures - 1,
  locked_until = CASE WHEN locked_until = :myLockedUntil THEN NULL ELSE locked_until END
WHERE user_id = :u AND generation = :g AND consecutive_failures > 0
```

更新の件数が0でも、エラーにしない(別の試行の成功によるリセット、ロックの解除後の新しい世代の予約など)。補償の更新が失敗した場合(内部設定DBの障害を含む)は、枠を失敗として数えたままにする(安全側)。ロックは、`locked_until`の経過で、必ず自動的に解除される。

### ログイン成功の更新

`AccountLoginState`のリセット(`consecutive_failures`=0、`locked_until`=NULL、`generation`+1)と、`auth_session`への行の追加を、同一のトランザクションで行う。どちらかが失敗した場合は、両方をロールバックし、503(内部設定DBの障害、NFR4.2)を返す。予約の枠は、失敗として数えたままになる(補償を試みる: 下記)。

### 予約のあとに、内部設定DBが使えなくなった場合

予約の更新が成功したあと、成功の更新のトランザクションが、内部設定DBの障害で失敗した場合は、補償の更新を、1回だけ試みる。補償も、障害で失敗した場合は、枠を、失敗として数えたままにする(上記のとおり、ロックは自動で解除される)。利用者には、503を返す(401にしない)。

### リフレッシュの更新

security-design.md NFR2.3の、条件付きの更新。更新の件数が1なら成功、0なら負けた側(401、Sessionは失効させない)。

### 失効の更新

```
UPDATE auth_session SET status = 'revoked' WHERE session_id = :sid AND status = 'active'
```

すでにrevokedでも、エラーにしない(冪等)。

## NFR4.2: 内部設定DBの障害時の応答

- **例外の変換**: `SessionRepository`・`AccountLoginStateRepository`の呼び出しの周りで、内部設定DBの障害を表す例外(接続の取得の失敗、クエリ・トランザクションのタイムアウト、ロックの待機の超過など、Springのデータアクセス例外のうち、一時的・接続の障害を表すもの)を、専用の非チェック例外`AuthStorageUnavailableException`に変換する。
  - 変換の対象: `DataAccessResourceFailureException`・`CannotCreateTransactionException`・`QueryTimeoutException`・`TransientDataAccessException`・`PessimisticLockingFailureException`(およびそれらの原因になるHikariCPの接続取得の例外)。
  - 対象外(バグや制約違反): `DataIntegrityViolationException`など。これらは、500として扱い、ERRORログに、例外の種類とリクエストIDを記録する。
- **503の返し方**: `AuthStorageUnavailableException`は、フィルタでは`ProblemDetailsWriter`が、コントローラでは`AuthApiExceptionAdvice`が、503(`auth.service.unavailable`)にする(security-design.md NFR2.8)。件数は、`auth_db_unavailable_total`に記録する。
- **fail closed**: Sessionを確認できないまま、リクエストを通さない。
- **キャッシュに有効なSessionがある場合**: 認証フィルタは、内部設定DBの障害の間も、キャッシュの内容で判定する(キャッシュは、失効・ロール選択・リフレッシュのたびに無効化されるため、DBの障害が、判定を古くすることはない)。キャッシュの内容は、`SessionState`の`refreshExpiresAt`と現在時刻の比較で、期限切れを判定する。後続の処理がDBを必要とするなら、そちらが503または500になる。
- **リフレッシュ**: 内部設定DBの障害で、更新のトランザクションが完了しなかった場合は、リフレッシュトークンを更新しない(ローテーションしない)。クライアントは、同じトークンで、再試行できる。ただし、コミットは成功したのに、応答が失われた場合(まれ)は、クライアントが古いトークンで再試行することになり、猶予内は401、猶予を超えれば盗用の疑いとして扱われる(NFR Requirementsの残余リスク4・5)。
- **クエリのタイムアウト**: 認証の読み取り・書き込みは、3秒のタイムアウトを設定する。内部設定DBの応答がない場合に、長く待たず、503になる。接続プール(HikariCP、共通基盤)の接続取得のタイムアウトも、3秒以内とすることを、共通基盤への要求とする(logical-components.md)。
- `Sessionのキャッシュの読み込み(loader)`が、DBの障害で失敗した場合、Caffeineは例外を保持せず、次のリクエストで、再び読み込みを試みる。

## NFR4.3: Sessionのキャッシュの整合性

- **無効化の順序**: 内部設定DBの更新のトランザクションが**コミットされた後**に、`SessionCache.invalidate(sessionId)`を呼ぶ(`TransactionTemplate.execute`が返った後)。コミットより前に無効化すると、その間に別のリクエストが古い内容を読み込んで、キャッシュに入れる可能性があるため、行わない。更新が失敗(ロールバック)した場合は、キャッシュを変更しない。
- **無効化の契機**: Sessionを書き換えるすべての更新(security-design.md NFR2.5の表): リフレッシュ成功(`refreshExpiresAt`の延長・ハッシュ・`activeRoleId`の変更を含む)・ログアウト・盗用の検知による失効・ユーザーの無効化の検知による失効・ロール選択。NFR Requirementsの本文は、無効化の契機として、失効・ロール選択・アクティブロールの再確認を挙げているが、リフレッシュ成功による`refreshExpiresAt`の延長も、キャッシュの内容(`SessionState.refreshExpiresAt`)を変えるため、無効化の対象に含める(これを含めないと、DBでは有効なSessionを、認証フィルタが古い期限で、401にし続けるおそれがある)。
- **読み込みと無効化の競合**: キャッシュの読み込みは、`cache.get(sessionId, loader)`で、キーごとに原子的に行う。読み込みの途中(DBを読んでいる間)に、別のスレッドが更新をコミットして、無効化を呼んだ場合、無効化は、進行中の読み込みが終わるのを待ってから、その結果を取り除く(Caffeineの、キーごとの原子性による)。そのため、読み込みが、更新の前の古い値を読んでいても、コミット後の無効化により、古い値が残らない。この性質を、並行実行のテストで確認する([assumption]。Caffeineの実装の性質に依存するため、Code Generationで、ストレステストにより確認する)。
- **TTLによる安全網**(Q1=A): 上記の無効化に、万一、漏れや競合があっても、書き込みから60秒で、古い内容は解消する。
- 複数プロセス構成でのキャッシュの整合は、scalability-design.md NFR3.5のとおり、全体設計に委ねる。

## NFR4.4: 起動時の設定検証(fail fast)

- `AuthProperties`(`@ConfigurationProperties`)が、設定を束縛し、Bean Validationと、追加の整合の確認で、検証する。不備があれば、アプリケーションを起動させない(project.md Mandated)。
- 検証の内容(NFR Requirementsの表): JWTの鍵(未設定・Base64として不正・デコード後が32バイト未満)、アクセストークンの有効期限(0以下)、リフレッシュトークンの有効期限(0以下、または、アクセストークンの有効期限より短い)、再送の猶予(負の値)、ロックのしきい値(0以下)、ロック時間(0以下)、Sessionの削除の保持日数(負の値)、実行間隔(0以下)。
- **エラーのメッセージに、値を出さない**: 鍵は、Bean Validationの制約(拒否された値がメッセージに出る)ではなく、`JwtKeyProvider`の初期化処理で検証し、例外のメッセージには、設定のキーの名前と理由(未設定・形式不正・長さ不足)だけを含める(security-design.md NFR2.2)。
- 検証の失敗のテスト(安全失敗のテスト、team.mdの必須テスト種別(a))を用意する(tech-stack-decisions.md NFR8.2)。

## NFR4.5: 期限切れ・失効したSessionの定期削除

`SessionCleanupJob`の設計(Q4=A)。

- **実行**: Springの`@Scheduled`(`fixedDelay`。前回の終了から、設定の実行間隔(既定1日)ごと)。1回目は、起動の10分後([assumption]。起動直後の負荷を避ける)。同時に2つ動かないよう、実行中は、次の実行を始めない(`fixedDelay`の性質と、実行中フラグ)。
- **削除の対象**: `refresh_expires_at`が、`現在時刻 − 保持日数(既定7日)`より前のSession(statusを問わない)。有効なSession(activeで、`refresh_expires_at`が未経過)は、この条件に該当しない。
- **1回のトランザクションの行数の上限**: 1,000行。次の2つのステートメントを、1つのトランザクションで行う(データベースの方言に依存しないため)。

```
SELECT session_id FROM auth_session WHERE refresh_expires_at < :cutoff ORDER BY refresh_expires_at LIMIT 1000
DELETE FROM auth_session WHERE session_id IN (:ids) AND refresh_expires_at < :cutoff
```

  1,000行未満になるまで、トランザクションを分けて繰り返す。1回の実行での上限は、100バッチ(10万行)とし、残りは、次の実行で削除する(実行が長く続くことを避ける)。
- **失敗の扱い**: 例外は捕捉し、ERRORログ(例外の種類と、それまでに削除した行数)に記録して、その回の実行を終える。次の実行で再試行する。認証のリクエストの処理には影響させない。ヘルスチェックには含めない(observability-design.md NFR5.4)。
- 削除の対象は、すでに有効でないSessionだけのため、認証の処理(ログイン・リフレッシュ・認証フィルタ)と、同じ行を書き換える競合は起きない。削除で、キャッシュの無効化は不要である(security-design.md NFR2.5)。
- **起動時の全削除**: `auth.session.revoke-all-on-startup`がtrueの場合は、起動時に、`auth_session`の全行を削除する(security-design.md NFR2.5)。

## NFR4.6: 時刻の扱い

- `java.time.Clock`(UTC)を、Springの部品として注入し、ロックの判定・Sessionの有効期限・JWTの`iat`・`exp`・リフレッシュの猶予・定期削除の基準時刻のすべてに、同じ`Clock`を用いる。DBの現在時刻(`NOW()`など)は使わず、`Clock`の値を、SQLのパラメータとして渡す。
- 日時は、UTCで保存する(内部設定DBの日時の列は、UTCのミリ秒精度)。
- テストでは、`Clock`を、固定またはずらせる実装に差し替え、有効期限・ロック・猶予の境界を検証する。
- サーバーの時刻の同期(NTPなど)は、環境の前提として記録する(tech-stack-decisions.md 確認事項5)。

## NFR4.7: 永続性・再起動・復元

- Session・`AccountLoginState`は、内部設定DBに永続化される。再起動後、有効なSessionは、リフレッシュトークンの有効期限まで有効である。内部設定DBが再起動で内容を失う構成(メモリのみの組込みDB)では、すべてのSessionが失われて、利用者は再ログインになる。認証の安全性は損なわれない。
- JWTの鍵は、再起動をまたいで、同じ値を注入する(security-design.md NFR2.2)。鍵が変わると、発行済みのアクセストークンが1回使えなくなるだけで、リフレッシュで復旧する。
- Sessionのキャッシュは、再起動で空になり、最初のリクエストごとに、DBから読み込む。
- **バックアップからの復元後**: 失効済みのSessionが、activeに戻る。運用の手順に、`auth.session.revoke-all-on-startup=true`での起動による全Sessionの削除を含める(security-design.md NFR2.5、環境側の前提)。

## NFR4.8: 可用性・バックアップ

- 認証は、アプリケーション本体(単一WAR)の可用性に従属する。ユニット単体のSLA/SLOは設定しない。認証が使えなくなると、認証を必要とするすべてのAPIが使えなくなる点は、他のユニットと異なる特徴である。
- 内部設定DBの障害時の応答は、NFR4.2のとおり、503とする。フロントエンドは、利用者を強制ログアウトせず、再試行を促す(security-design.md NFR2.11)。
- バックアップ・リカバリ方針は、内部設定DB全体(他ユニットと共通)のものに従う。復元後の注意は、NFR4.7に記録した。

## 根拠

- 要件: `nfr-requirements/reliability-requirements.md`(NFR4.1〜NFR4.8)、`nfr-requirements/performance-requirements.md`(NFR1.1〜NFR1.3)、`nfr-requirements/security-requirements.md`(NFR2.2・NFR2.5)、`nfr-requirements/scalability-requirements.md`(NFR3.4・NFR3.5)、`nfr-requirements/observability-requirements.md`(NFR5.1)、`nfr-requirements/tech-stack-decisions.md`、`inception/requirements-analysis/requirements.md`のNFR4
- 機能設計: `functional-design/functional-spec.md`(W1〜W6)、`rules.md`(BR5.3〜BR5.6・BR5.8・BR5.11・BR5.15)、`entities.md`
- 契約: `inception/contract-design/contract-summary.md` のC4・C11・C14
- 質問回答: `nfr-design-questions.md` Q1
- ルール: `aidlc/spaces/default/memory/project.md`(Mandated: 設定定義の誤りのfail fast)
