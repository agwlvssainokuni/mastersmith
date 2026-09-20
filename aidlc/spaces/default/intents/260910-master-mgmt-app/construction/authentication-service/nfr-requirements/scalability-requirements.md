# Scalability Requirements — authentication-service (U5)

## NFR3.1: 想定規模

- 利用規模は要件のNFR3に従い、**数十名程度**(前身ツールの約10名規模からの拡大)とする。同時アクセスは最大50ユーザー(NFR1)。
- 同時に有効なSession(端末ごとのログイン)の数は、利用者数に、1人あたりの端末数を掛けた、**数十〜数百件**を上限の目安とする([assumption]。要件は利用者数の目安のみを定めるため、Sessionの数は本ステージの仮定である)。アクセストークンの有効期限が10分、リフレッシュトークンが30分のため、有効なSessionの多くは、直近30分以内に使われたものである。
- 認証フィルタの呼び出しの頻度は、同時50ユーザーが、それぞれ数リクエスト/秒を送る場合でも、**数百リクエスト/秒未満**を上限の目安とする([assumption]。要件に明示がない)。リフレッシュは、各クライアントが、アクセストークンの有効期限(10分)ごとに1回程度であり、数十リクエスト/分に満たない。

## NFR3.2: 認証フィルタのSession参照とキャッシュ容量

- 認証フィルタは、認証を必要とするすべてのリクエストで、`sid`のSessionを確認する(BR5.11)。NFR1.2の目標(キャッシュヒット時にp95で5ミリ秒以下)を満たすため、インメモリのキャッシュを置く。
- キャッシュの容量は、NFR3.1の**同時に有効なSessionの上限の目安(数百件)を、すべて保持できる**大きさとする。容量が足りずに、有効なSessionが追い出されると、そのたびに内部設定DBを引くことになり、NFR1.2のキャッシュミスの目標(50ミリ秒以下)に頼ることになる。
- キャッシュされる内容は、Sessionの状態(userId・activeRoleId・status・refreshExpiresAt)であり、パスワード・トークン・ハッシュを含めない(NFR2.7)。
- キャッシュの有効期間・具体的な容量・無効化の方式は、NFR Design(3.3)で確定する。要件としては、NFR4.3(整合性)を満たすこと。

## NFR3.3: ハッシュ計算のリソース

- ハッシュ計算(Argon2id、1回あたりメモリ約19MiB)を起こすのは、ログインとダミーの検証だけであり、同時実行の上限(既定はCPUコア数)は、user-managementのNFR1.3・NFR3.3に従う。本ユニットは、ハッシュ計算のメモリを、追加で使わない。
- ハッシュ計算が使うメモリの最大値(おおむね「上限×19MiB」)は、実行環境のJVMヒープ(`-Xmx`)を決める際に、別途見込む(user-managementのNFR3.3)。

## NFR3.4: データの増加

- **Session**: ログインのたびに1行が増える。数十名が、1日に数回ログインすると、1年でおよそ数万行が増える([assumption]。質問Q4の見積もり)。NFR4.5の定期削除(有効期限から7日後)を行うと、定常的に保持する行数は、おおむね数千行以下に収まる。削除しない場合の増加は、Q4でBを選ばなかったため、想定しない。
- **AccountLoginState**: activeなユーザーごとに1行であり、行数は、ユーザー数(数十〜数百件、user-managementのNFR3.1)を超えない。無効化されたユーザーの行は、削除しない(ユーザーの記録を無期限に保持する方針、user-managementのNFR2.6に合わせる)。
- どちらのテーブルも、検索は、主キーまたは一意なキー(`sessionId`・`refreshTokenHash`・`userId`)で行うため、行数が数万に増えても、NFR1の目標に影響しない。Sessionの削除(NFR4.5)が、`refreshExpiresAt`で検索するため、この列に索引を置く(Flywayの移行スクリプトで明示的に定義する。具体的な設計はNFR Designで確定する)。

## NFR3.5: 拡張方針

- 要件のNFR3どおり、想定を超えて同時接続数が増えた場合は、線形にリソースを追加することで対応できる設計とする。認証のAPIは、リクエスト間で状態を持たない(状態は、内部設定DBのSessionと、キャッシュにだけ持つ)。
- ロックの回数の更新(BR5.3)と、リフレッシュトークンの更新(BR5.6)は、DBの条件付きの原子的な更新で整合を保つため(NFR4.1)、プロセスの数が増えても、正しさは保たれる。一方、認証フィルタのSessionのキャッシュの無効化は、プロセス内に限られる。複数プロセス構成では、あるプロセスでの、ログアウト・ロールの選択が、別のプロセスのキャッシュに反映されない。この扱いは、内部設定DBが組込みDBであることによる複数プロセス構成の制約とあわせて、他ユニットと共通の事項として、全体設計(NFR Design・Infrastructure Design)に委ねる(functional-spec.mdのOpen Questionsと同じ)。本MVPは、単一プロセスを前提とする。

## 根拠

- 要件: `inception/requirements-analysis/requirements.md` のNFR1・NFR3・FR3.2
- 機能設計: `construction/authentication-service/functional-design/functional-spec.md`(W1・W5、Open Questions)、`rules.md`(BR5.3・BR5.6・BR5.8・BR5.11)
- 契約: `inception/contract-design/contract-summary.md` のC4・C14
- 質問回答: `nfr-requirements-questions.md` Q4・Q6
- 他ユニットの要件: `construction/user-management/nfr-requirements/scalability-requirements.md` NFR3.1・NFR3.3・NFR3.4
