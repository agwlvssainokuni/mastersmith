# Performance Design — authentication-service (U5)

`nfr-requirements/performance-requirements.md`の各要件を、設計に落とし込む。本プロジェクトは、単一の実行可能WAR(1プロセス)であり、AWSなどのクラウドのインフラは設計の対象外である。ここでの設計は、プロセス内の部品と、内部設定DB(組込みDB)の使い方である。

## 全体の構成

```
ブラウザ ─(Bearer)→ AuthRequestSizeLimitFilter → BearerAuthenticationFilter → 各ユニットのController
                                                        │
                                                        ├→ SessionCache(Caffeine) ─miss→ SessionRepository(内部設定DB)
                                                        └→ AccessTokenVerifier(HS256)
ログイン ─→ AuthController ─→ AuthenticationApplicationService
                                  ├→ UserAccountLookupApi(C11、トランザクションの外)
                                  ├→ LoginAttemptGate(短いトランザクション)
                                  └→ SessionService(短いトランザクション)、AccessTokenIssuer、RefreshTokenGenerator
```

## NFR1.1: 認証系APIの応答時間

- 各APIは、内部設定DBの短いトランザクションを、必要な回数だけ順に実行する構成とし、トランザクションを、C11の呼び出し(ハッシュ計算の順番待ちを含む)の間、保持しない(reliability-design.md NFR4.1)。応答時間の主な要素は、ログインのハッシュ計算(user-managementの`HashConcurrencyLimiter`と`PasswordHasher`、1回300ms以下、最大の順番待ち2秒)である。
- DBの操作の数(応答時間の見積もりの根拠)。いずれの操作も、主キーまたは一意なキーによる、1行の検索・更新である。

| API | DBの操作(トランザクションの単位) | 合計 |
|---|---|---|
| ログイン(成功) | C11の`findByEmail`(読み取り、U4のトランザクション)、予約の更新(1)、成功の更新とSessionの作成(1)。ハッシュ計算はトランザクションの外 | 3回 |
| ログイン(失敗・未登録等) | 未登録・招待中・無効化済みは、`findByEmail`だけ。ロック中・パスワードの誤りは、`findByEmail`と予約の更新(1) | 1〜2回 |
| リフレッシュ | Sessionの検索(1〜2回、現在のハッシュ、次に1つ前のハッシュ)、C11の`isDisabled`と`findByUserId`(2回)、条件付きの更新(1) | 4〜5回 |
| ログアウト | 認証フィルタ(キャッシュミスのみ1回)、Sessionの失効(1) | 1〜2回 |
| ロール選択 | 認証フィルタ(キャッシュミスのみ1回)、C11の`findByUserId`(1)、Sessionの更新(1) | 2〜3回 |

- 各DBの操作が、内部設定DB(組込みDB)の主キー・一意キーの検索・更新であり、50〜100ミリ秒に収まることは、NFR1.1の見積もりの仮定である。この見積もりを超えて、遅延が観測された場合は、`auth_login_duration_seconds`(observability-design.md NFR5.1)で検知し、接続プールの設定(logical-components.mdの共通基盤への要求)を見直す。
- **計測の方法と合否**(NFR1.1の検証): team.mdが負荷・性能テストを既定に含めないため、統合テストで、次の条件で計測して、結果をBuild and Testの結果に記録する。合否は、結果の記録と、p95が目標を超えた場合の原因の分析とする(CIのマージ前の必須ゲートにはしない、[assumption])。
  - 環境: テストを実行する環境のCPUコア数とメモリを記録する。内部設定DBは、本番と同じ種類の組込みDBとする。
  - 負荷: 各APIを、50の同時リクエストで、それぞれ200回以上、繰り返す(ログインは、ハッシュ計算の同時実行の上限を、実行環境の既定のまま用いる)。計測の対象は、リクエストの受信からレスポンスの送信までである。
  - 集計: p95を、APIごとに算出する。
- **NFR1.1とNFR1.4の関係の解釈**: NFR1.1の「同時50ユーザー」は、50人が、それぞれのAPIを、通常の頻度で呼び出す状態を指す。ログインの同時の集中(ハッシュ計算の同時実行の上限を超える数のログインが、同一の瞬間に集中する場合)は、NFR1.4の既知の限界に含め、NFR1.1のp95の目標の対象外とする。上の計測でも、ログインは、上限の範囲内の同時数で計測する。

## NFR1.2: 認証フィルタの追加時間

認証フィルタ(`BearerAuthenticationFilter`)の処理を、次の順に行い、追加時間を最小にする。

1. `Authorization`ヘッダーから、Bearerトークンを取り出す(ヘッダーがなければ、`missing`として401)。
2. トークンを解析し、署名方式が`HS256`であることを確認し、HMACの署名を検証する(JDKとNimbus JOSE + JWTのメモリ内の計算のみ。数十マイクロ秒)。`exp`の検証は、時計のずれの許容を0として行う(security-design.md NFR2.2)。
3. `sid`のSessionを、`SessionCache`から引く(ヒット時は、メモリ内の検索のみ)。ミス時は、`SessionRepository`から、主キーで1行を読み、キャッシュへ入れる。
4. `sub`とSessionの`userId`の一致、Sessionの有効(statusがactiveで、`refreshExpiresAt`が現在時刻より後)を確認する。
5. `Operator`(userId・sessionId・activeRoleId)を、セキュリティコンテキストに設定する。

- ヒット時に、p95で5ミリ秒以下: 上の1〜5は、メモリ内の計算だけで、データベースにも、ネットワークにもアクセスしない。ヒット時の処理は、数十マイクロ秒〜1ミリ秒の見込みである([assumption]。実測はBuild and Testで記録する)。
- ミス時に、p95で50ミリ秒以下: DBの主キーによる1行の読み取り1回(接続プールから接続を取得し、1回のクエリ)である。読み取りには、**3秒のクエリのタイムアウト**を設定し、内部設定DBが応答しない場合に、長く待たず、503にする(reliability-design.md NFR4.2)。
- 計測: `auth_filter_duration_seconds`(`cache`タグ: `hit`・`miss`、observability-design.md NFR5.1)で、フィルタの処理時間を記録する。統合テストで、ヒット時・ミス時の処理時間を計測し、Build and Testの結果に記録する。
- **キャッシュの構成**(scalability-design.md NFR3.2に詳細): Caffeine、最大1,000件、書き込みから60秒のTTL(Q1=A)、統計を記録する。

## NFR1.3: ハッシュ計算資源との関係

- authentication-serviceは、ハッシュ計算の許可を、自前で持たない。user-managementの`HashConcurrencyLimiter`(セマフォ、待機2秒)を、C11の`verifyPasswordHash`と`dummyVerify`を通してだけ使う。
- **資源の取得順序の不変条件**: ハッシュ計算の許可を待つ間・保持する間は、内部設定DBの接続を保持しない。そのため、ログインは、次の順序で、C11の呼び出しを、トランザクションの外で行う。
  1. `findByEmail`(トランザクションの外)。
  2. 予約の更新(短いトランザクション、コミットして、接続を返す)。
  3. `verifyPasswordHash`または`dummyVerify`(許可を待つ間、接続を保持しない)。
  4. 成功の更新とSessionの作成(短いトランザクション)、または補償の更新(短いトランザクション)。
- `AuthenticationApplicationService`の各メソッドには、`@Transactional`を付けない(外側のトランザクションを作らない)。内側の短いトランザクションは、`TransactionTemplate`で、明示的に区切る。C11の呼び出し元が、トランザクションの中にいることによって、U4の資源の取得順序の不変条件が崩れることを、防ぐ(user-managementのNFR Designの未解消事項の、U5側の対応)。
- リフレッシュ・ログアウト・ロール選択・認証フィルタは、ハッシュ計算の許可を使わない。リフレッシュトークンの照合は、SHA-256のハッシュ(マイクロ秒)だけである。

## NFR1.4: 同時ログインの集中(既知の限界)

- 追加の制御(ログインの待ち行列、レート制限)を設けない(NFR Requirements Q6=A・Q3=A)。ハッシュ計算の許可を取れないログインは、user-managementのNFR1.3どおり、2秒で打ち切られて503(`auth.service.unavailable`、security-design.md NFR2.8)になる。
- 503は、`auth_login_hash_capacity_exceeded_total`で観測する(observability-design.md NFR5.1)。頻発する場合は、実行環境のCPUコア数、または、user-managementの同時実行の上限の設定を、見直す。
- ダミー検証(`dummyVerify`)が、許可枠を占有する可能性は、残余リスクとして受容した(security-design.md 残余リスク10、Q3=A)。

## 根拠

- 要件: `nfr-requirements/performance-requirements.md`(NFR1.1〜NFR1.4)、`nfr-requirements/scalability-requirements.md`(NFR3.2)、`nfr-requirements/reliability-requirements.md`(NFR4.1・NFR4.2)、`nfr-requirements/security-requirements.md`(NFR2.2)、`nfr-requirements/observability-requirements.md`(NFR5.1)、`nfr-requirements/tech-stack-decisions.md`
- 機能設計: `functional-design/functional-spec.md`(W1〜W6)、`rules.md`(BR5.1〜BR5.3・BR5.6・BR5.11・BR5.15)
- 契約: `inception/contract-design/contract-summary.md` のC4(REST API)・C11(`verifyPasswordHash`)・C14
- 質問回答: `nfr-design-questions.md` Q1・Q3
