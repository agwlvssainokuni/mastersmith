# Performance Design — user-management (U4)

`nfr-requirements/performance-requirements.md`の各要件を、設計に落とし込む。技術スタックはJava 25・Spring Boot・Gradle(`tech-stack-decisions.md`)。

## NFR1.1: 管理系APIの応答時間

- **キャッシュは設けない**。対象は数十〜数百件のUserで(NFR3.1)、内部設定DBへ直接問い合わせても、3秒(p95)の目標に収まる。キャッシュの一貫性の管理(無効化・遅延)を持ち込むより、単純さを優先する。
- **認可判定は1リクエストにつき1回**: `/api/users`系では、`PermissionEngineApi.canAccessScreen`(C10)をアプリケーションサービスの入口で1回だけ呼ぶ。permission-engine側のキャッシュは、permission-engine自身の設計に従う(本ユニットは関与しない)。
- **一覧(W7)**: `email`列の一意インデックスを利用し、email昇順で全件を取得する(ページングなし、BR4.14)。UserPreferenceは一覧の応答に含めないため、結合しない。`passwordHash`と`invitationToken`は、取得する列から除く(応答に含めないだけでなく、不要な読み出しも避ける)。
- **検証の方法**: 自動の負荷試験は行わない。統合テストで計測した応答時間を、Build and Testの結果に記録する(NFR1.1の検証方法)。

## NFR1.2: パスワードハッシュ(Argon2id)の計算時間

`PasswordHasher`という1つのコンポーネントに、ハッシュ計算・検証を集約する(logical-components.md)。

- 実装は、Spring Securityの`Argon2PasswordEncoder`を、`application.yml`のパラメータ(メモリ19456KiB・反復2回・並列度1が初期値)で構築して用いる。他のクラスは、`Argon2PasswordEncoder`を直接使わない。
- ハッシュの計算・検証は、必ず次の`HashConcurrencyLimiter`(NFR1.3)を通す。
- 上限(p95で300ms以下)は、**ハッシュ計測の単体ベンチマークテスト**で確認する(並列度1・同時実行なし・十分なウォームアップ後に反復計測し、CPUコア数・メモリを記録する)。

```java
// 概念的なインタフェース(実装はCode Generation)
public interface PasswordHasher {
    String hash(String rawPassword);                 // 128文字以下を前提
    boolean verify(String rawPassword, String hash); // 129文字以上は計算せずfalse
    boolean needsUpgrade(String hash);               // 保存済みのパラメータが現在の設定より古いか
}
```

## NFR1.3: 同時ハッシュ計算数の制御

`HashConcurrencyLimiter`が、ハッシュ計算の同時実行数を制御する。

- 仕組み: 公平(fair)なセマフォ。許可数の既定は**実行環境のCPUコア数**(`application.yml`で変更可)。計算を始める前に許可を取得し、終わったら必ず返す(例外時も返す)。
- 待機の上限: 許可の取得は最大**2秒**待つ。取得できなければ、専用の例外(`HashCapacityExceededException`)を投げる。
- 例外の変換: REST(招待受諾・`/api/users`系)では、U4の`UserApiExceptionAdvice`(対象をU4に限定した例外変換)が、この例外を503(RFC 9457形式のProblemDetails)に変換する。C11の`verifyPasswordHash`では、例外をそのまま呼び出し元(authentication-service)に伝え、HTTPへの変換は呼び出し元が行う(契約追補として保留、`tech-stack-decisions.md`の一覧)。
- **許可とDB接続の順序(不変条件)**: ハッシュ計算の許可を保持している間は、DB接続(トランザクション)を取らない。トランザクションを保持している間は、許可を取らない。したがって、ハッシュの計算は、必ずトランザクションの外側で、許可の中だけで行う(reliability-design.md「資源の取得順序」)。
- **ログイン成功時のハッシュ更新(security-design.md NFR2.2、Q3=A)**: 許可を取り、検証と、必要な場合の新しいハッシュの計算(1回の許可の中で連続して行う)を済ませて、許可を返す。そのあとで、独立した新しいトランザクション(`TransactionTemplate`の`REQUIRES_NEW`)で、条件付き更新を行う。検証と再計算を合わせた最大の所要時間は、通常の検証の約2倍になりうるが、更新が起きるのは、パラメータを強くした後の、各ユーザーの最初のログイン1回だけである。
- **招待受諾の順序**: トークンの存在確認(トランザクションの外の軽い読み取り)と、入力の検証を先に行い、未知のトークンや使用済みのトークンでは、ハッシュを計算しない。そのあとで、許可を取ってハッシュを計算し、許可を返してから、トランザクションを開始する(reliability-design.md NFR4.1)。認証のないAPIで、ハッシュ計算の許可を使い切って、ログインまで503にされることを避けるため。
- 待機の上限を超えて503とした回数は、メトリクスに記録する(observability-design.md NFR5.1)。

## NFR1.4: 招待メール送信の時間予算

- 送信は、専用のスレッドプール(`InvitationMailExecutor`)で行い、呼び出し側は結果を、10秒を上限に待つ。スレッド数は**5**で、待ち行列は持たない。招待の同時実行数の上限(5)は、このプールとは別に、`InvitationAdmission`(待たない許可、reliability-design.md NFR4.2)が制御する。プールが満杯のとき(打ち切り後の送信スレッドが残っている場合など)は、投入時の拒否を503に変換する。
- SMTPのタイムアウトは、メール送信ライブラリ(Jakarta Mail)に、接続3秒・読み取り5秒・書き込み2秒を与える。**これらは、いずれも1回の操作ごとの上限であり、送信全体の合計が10秒になることは保証しない**(SMTPの会話には、複数のコマンドの往復が含まれるため)。全体の10秒の上限は、**呼び出し側が結果を待つ時間を10秒で打ち切る**ことで実現する。超過した場合は、失敗として扱い、送信の完了を待たずに、ロールバックして503を返す。
- **打ち切り後の送信スレッド**: 呼び出し側が打ち切っても、ブロッキングI/O中の送信スレッドは、割り込みで止められないため、その間、プールを占有し続ける。この間に送信が完了すると、ロールバック済みで存在しないトークンのリンクを含むメールが届きうる(そのリンクは、受諾時に404になる)。制約として受け入れ、残余リスク7に記録する(security-design.md)。
- 招待APIは、NFR1.1の3秒目標の対象外(SMTPの応答時間が支配的なため)。ただし、メール送信を除いた処理時間は3秒以内に収める。
- 送信の自動リトライは行わない(管理者が再招待する)。

## 根拠

- 要件: `nfr-requirements/performance-requirements.md`(NFR1.1〜NFR1.4)、`inception/requirements-analysis/requirements.md`のNFR1
- 機能設計: `functional-design/functional-spec.md`(W1・W2・W7・W8)
- 契約: `inception/contract-design/contract-summary.md`のC5・C11
- 設計質問: `nfr-design-questions.md` Q3
