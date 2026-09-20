# Performance Requirements — authentication-service (U5)

authentication-serviceは、ログイン・リフレッシュ・ログアウト・アクティブロールの選択(C4)と、認証を必要とするすべてのAPIで毎回動く認証フィルタ(rules.md BR5.11)、およびC14の`getActiveRoleId`を担う。認証フィルタは、他のすべてのユニットの応答時間に上乗せされるため、認証フィルタの追加時間にも目標を置く(Q6=A)。

## NFR1.1: 認証系APIの応答時間

`POST /api/auth/login`・`POST /api/auth/refresh`・`POST /api/auth/logout`・`PUT /api/auth/active-role`の応答時間は、要件のNFR1に従い**3秒以内(95パーセンタイル)**、同時アクセス最大50ユーザーの条件で満たす(Q6=A)。

- 測定対象: HTTPリクエストの受信からレスポンス送信まで(サーバー側処理時間)。
- ログインは、パスワードのハッシュ計算(user-managementのNFR1.2: 1回300ms以下、p95)を含む。ハッシュ計算の順番待ちが2秒を超えた場合は、目標を待たずに503で打ち切る(user-managementのNFR1.3、rules.md BR5.15)。
- 上限の見積もり(最悪の場合でも、3秒に収まることの確認)。各段階の時間は、内部設定DB(組込み)の主キー・一意キーによる検索・更新を前提とした仮定である([assumption]。要件・質問回答に明示がなく、本ステージで置いた値)。

| 処理 | 段階 | 上限の見積もり |
|---|---|---|
| ログイン | C11の`findByEmail`(DBの読み取り) | 50ms |
| ログイン | 試行の枠の確保(予約の更新、BR5.3) | 50ms |
| ログイン | `verifyPasswordHash`(順番待ち+計算) | 順番待ちは最大2秒(user-managementのNFR1.3)+計算300ms(user-managementのNFR1.2) |
| ログイン | 成功の更新とSessionの作成(NFR4.1)、JWTの署名、リフレッシュトークンの生成 | 110ms |
| リフレッシュ | ハッシュによるSessionの検索、C11の`isDisabled`と`findByUserId`(DBの読み取り2回)、条件付きの更新 | 200ms |
| ログアウト・ロール選択 | 認証フィルタ(NFR1.2)、C11の`findByUserId`(ロール選択のみ)、Sessionの更新 | 150ms |

- ログインの最悪の場合は、「順番待ち2秒+計算0.3秒+DBの処理約0.2秒」で約2.5秒であり、3秒に収まる。ハッシュのパラメータが古い場合にログイン成功時に行われる再ハッシュ(user-managementのC11追補: 検証と同じ許可の中で、続けてもう1回計算する)は、計算がもう1回増えるため、最悪で約2.8秒となり、余裕は約0.2秒になる。この再ハッシュは、パラメータを変更したあとの、利用者ごとの最初のログインだけに起こる、まれなケースである。
- 検証方法: team.mdは負荷・性能テストを既定に含めない(Q7でD不採用)ため、同時50ユーザーでの負荷試験による自動検証は行わない。本項は設計目標とし、統合テストで計測した各APIの応答時間を、Build and Testの結果に記録して確認する([assumption]。user-managementのNFR1.1と同じ扱い)。

## NFR1.2: 認証フィルタの追加時間

認証フィルタ(Bearerトークンの署名・有効期限の検証と、`sid`のSessionの解決、BR5.11)が、認証を必要とする各リクエストに足す時間は、次のとおりとする(Q6=A)。

- インメモリのキャッシュにSessionがあるとき(キャッシュヒット): **p95で5ミリ秒以下**。
- キャッシュにSessionがなく、内部設定DBを1回引くとき(キャッシュミス): **p95で50ミリ秒以下**。
- 測定対象: 認証フィルタの入口から出口まで(後続の処理は含めない)。
- 意図: 認証フィルタは、`/api/**`のうち認証を必要とするすべてのリクエストで動く。他のユニットの応答時間の目標(たとえば、permission-engineのNFR1.1の50ms以内)を守るために、認証フィルタの追加分を、その目標に対して無視できる大きさに抑える。
- 検証方法: 認証フィルタの処理時間を、ヒット・ミスのそれぞれで計測する統合テストを用意し、結果をBuild and Testの結果に記録する。負荷試験は行わない(NFR1.1と同じ理由)。キャッシュのヒット・ミスの数は、NFR5.1の派生指標で観測する。
- キャッシュの有効期間・容量・無効化の方式は、NFR Design(3.3)で確定する。要件としては、NFR3.2(容量)とNFR4.3(整合性)を満たすこと。

## NFR1.3: ハッシュ計算資源との関係

- authentication-serviceが、パスワードのハッシュ計算を起こすのは、ログイン(C11の`verifyPasswordHash`)と、ダミーの検証(C11の`dummyVerify`、rules.md BR5.2)だけである。どちらも、user-managementが管理する、同時実行数の上限(既定はCPUコア数)と、順番待ちの上限(2秒)を共有する。authentication-service独自の上限は設けない(上限が二重になり、実際の待ち時間の予測が難しくなるため)。
- リフレッシュトークンの照合(NFR2.3のSHA-256)は、ハッシュ計算資源を使わない。リフレッシュ・ログアウト・ロール選択・認証フィルタは、ハッシュ計算の順番待ちの影響を受けない。
- C11の各メソッドは、トランザクションの外で呼ぶ(rules.md BR5.15)。ハッシュ計算の順番待ちの間に、内部設定DBの接続を保持しない。

## NFR1.4: 同時ログインの集中(既知の限界)

Q6でA(同時ログインの集中を目標に含めない案)を選んだため、始業時などにログインが同一の瞬間に集中する場合の目標は置かない。ただし、次の限界を、既知の事項として記録する。

- ハッシュ計算の同時実行の上限(既定はCPUコア数、1回300ms)と、順番待ち2秒(user-managementのNFR1.3)から、順番待ちが2秒以内で受け付けられる同時ログインの数は、おおむね「CPUコア数×(2秒÷0.3秒)」である(たとえば4コアで約26件)。この数を超えて、同一の瞬間にログインが集中した場合は、超えた分が503(BR5.15)になる。
- 利用者は、503のあとにログインを再試行できる(Q5=Aにより、503は認証の失敗と区別される)。数十名規模で、ログインの時刻が分散することを前提とし、実際に503が観測される場合は、実行環境のCPUコア数またはuser-managementの同時実行の上限の設定を見直す(NFR5.1の`auth_login_hash_capacity_exceeded_total`で観測する)。

## 根拠

- 要件: `inception/requirements-analysis/requirements.md` のNFR1(応答3秒・同時50ユーザー)、FR3.1
- 機能設計: `construction/authentication-service/functional-design/functional-spec.md`(W1〜W6)、`rules.md`(BR5.1〜BR5.3・BR5.6・BR5.11・BR5.15)
- 契約: `inception/contract-design/contract-summary.md` のC4(REST API)・C11(`verifyPasswordHash`)・C14
- 質問回答: `nfr-requirements-questions.md` Q6
- 他ユニットの要件: `construction/user-management/nfr-requirements/performance-requirements.md` NFR1.2・NFR1.3
