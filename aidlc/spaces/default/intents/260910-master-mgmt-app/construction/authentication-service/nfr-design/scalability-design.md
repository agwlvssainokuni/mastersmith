# Scalability Design — authentication-service (U5)

`nfr-requirements/scalability-requirements.md`の各要件を、設計に落とし込む。本プロジェクトは、単一の実行可能WAR(1プロセス)であり、専用のスケーリングの仕組み(分散キャッシュ、キュー、シャーディング)は、設計の対象外である。

## NFR3.1: 想定規模

- 想定規模(数十名、同時50ユーザー、有効なSessionは数十〜数百件、認証フィルタの呼び出しは数百リクエスト/秒未満)を、設計の前提とする。専用の仕組みは設けず、内部設定DBのインデックス付きテーブルと、プロセス内のキャッシュで足りる。
- この前提を超える規模になった場合の再検討は、NFR3.5に記録する。

## NFR3.2: 認証フィルタのSession参照とキャッシュ

`SessionCache`の設計(Q1=A)。

| 項目 | 設計 |
|---|---|
| 実装 | Caffeine(プロセス内)。追加のミドルウェア(Redis等)は導入しない |
| キー | `sessionId` |
| 値 | `SessionState`(`userId`・`activeRoleId`・`status`・`refreshExpiresAt`)。パスワード・トークン・ハッシュを含めない(security-design.md NFR2.7) |
| 最大件数 | **1,000件**(既定。設定で変更できる)。NFR3.1の、同時に有効なSessionの上限の目安(数百件)を、すべて保持できる大きさとする |
| 有効期間(TTL) | 書き込みから**60秒**(Q1=A、安全網。設定で変更できる)。通常は、明示的な無効化で、直ちに反映される |
| 読み込み | `cache.get(sessionId, loader)`(キーごとの原子的な読み込み)。`loader`は、`SessionRepository`から、主キーで1行を読む(3秒のクエリのタイムアウト、performance-design.md NFR1.2)。Sessionが存在しない場合は、キャッシュに入れない(否定的な結果を保持しない) |
| 無効化 | Sessionを書き換える更新が**コミットされた後**に、`cache.invalidate(sessionId)`(reliability-design.md NFR4.3) |
| 統計 | `recordStats()`を有効にし、Micrometerの標準のキャッシュのメトリクスで、ヒット・ミスを記録する(observability-design.md NFR5.1) |

- **メモリ**: 1件あたり数百バイトのため、1,000件で1MiBに満たない。JVMヒープへの影響は無視できる。
- 容量を超えると、Caffeineが、利用の少ないものから追い出す。追い出されたSessionは、次のリクエストで、DBから読み直す(NFR1.2のミス時の目標、p95で50ミリ秒以下)。
- **キャッシュに載らないもの**: パスワード、トークン(平文・ハッシュ)、鍵。

## NFR3.3: ハッシュ計算のリソース

- authentication-serviceは、ハッシュ計算のメモリを、追加で使わない。同時実行の上限(既定はCPUコア数)と、ハッシュ計算のメモリ(1回あたり約19MiB、上限×19MiB)は、user-managementのscalability-design.md NFR3.3に従う。
- 実行環境のJVMヒープ(`-Xmx`)を決める際は、user-managementの見込みに加えて、Sessionのキャッシュ(1MiB未満)は、無視できる大きさとして扱う。

## NFR3.4: データの増加

テーブルの設計(Flywayの移行スクリプトで作成し、インデックスを明示的に定義する。JPAのDDL自動生成には委ねない、他ユニットと同じ方式)。

**`auth_session`**

| 列 | 型 | 制約 |
|---|---|---|
| `session_id` | 可変長文字列(43文字、Base64URLの128ビット乱数) | 主キー |
| `user_id` | 可変長文字列 | NOT NULL |
| `active_role_id` | 可変長文字列 | NULL可 |
| `refresh_token_hash` | 固定長文字列(43文字) | NOT NULL、一意 |
| `previous_refresh_token_hash` | 固定長文字列(43文字) | NULL可、一意(NULLは複数許す) |
| `issued_at`・`last_refreshed_at`・`refresh_expires_at` | 日時(UTCのミリ秒精度) | NOT NULL |
| `status` | 可変長文字列(`active`・`revoked`) | NOT NULL |

- インデックス: 主キー、`refresh_token_hash`の一意、`previous_refresh_token_hash`の一意、`refresh_expires_at`(定期削除の検索のため、NFR4.5)。`user_id`のインデックスは設けない(本ユニットに、`user_id`で検索する処理がないため)。

**`account_login_state`**

| 列 | 型 | 制約 |
|---|---|---|
| `user_id` | 可変長文字列 | 主キー |
| `consecutive_failures` | 整数 | NOT NULL、既定0 |
| `locked_until` | 日時(UTC) | NULL可 |
| `generation` | 長整数 | NOT NULL、既定0 |

- 行数の見込み: `auth_session`は、定期削除(有効期限から7日後)により、定常的に数千行以下。`account_login_state`は、activeなユーザーごとに1行で、ユーザー数(数十〜数百件)を超えない。いずれも、主キーまたは一意キーによる検索のため、行数が増えても、NFR1に影響しない。
- Flywayの移行スクリプトの名前・版番号の採番は、他ユニットとの調整事項として、logical-components.mdの共通基盤・他ユニットへの要求に記録する。

## NFR3.5: 拡張方針

- 認証のAPIは、リクエスト間で状態を持たない(状態は、内部設定DBのSessionと、プロセス内のキャッシュだけ)。プロセスを増やしても、ロックの回数の更新(予約型)と、リフレッシュトークンの更新(条件付きの更新)は、DBの原子的な更新で整合するため、正しさは保たれる。
- **複数プロセス構成での制約**: Sessionのキャッシュの無効化は、プロセス内に限られる。あるプロセスでの、ログアウト・ロール選択・失効が、別のプロセスのキャッシュに届かないため、別のプロセスでは、**最大60秒(TTL、Q1=A)の間、古いSessionの内容**(失効前の有効な状態)で認証される。本MVPは、単一プロセスを前提とし、この遅れは生じない。複数プロセス構成にする場合は、TTLを短くする(Q1のC案の10秒)か、無効化を配信する仕組みを、その時点で設計する。内部設定DBが組込みDBであることによる、複数プロセス構成の制約も、他ユニットと共通の事項として、全体設計に委ねる(user-managementのscalability-design.md NFR3.4と同じ)。
- 想定を超える規模(有効なSessionが、キャッシュの最大件数を大きく超える、など)では、最大件数の設定を上げる。

## 根拠

- 要件: `nfr-requirements/scalability-requirements.md`(NFR3.1〜NFR3.5)、`nfr-requirements/performance-requirements.md`(NFR1.2)、`nfr-requirements/reliability-requirements.md`(NFR4.3・NFR4.5)、`nfr-requirements/security-requirements.md`(NFR2.7)、`nfr-requirements/observability-requirements.md`(NFR5.1)、`nfr-requirements/tech-stack-decisions.md`、`inception/requirements-analysis/requirements.md`のNFR3
- 機能設計: `functional-design/functional-spec.md`(W1・W5、Open Questions)、`rules.md`(BR5.3・BR5.6・BR5.11)、`entities.md`
- 契約: `inception/contract-design/contract-summary.md` のC4・C14
- 質問回答: `nfr-design-questions.md` Q1
