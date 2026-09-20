# Scalability Design — user-management (U4)

`nfr-requirements/scalability-requirements.md`の各要件を、設計に落とし込む。

## NFR3.1: 想定規模に対する設計

- 想定: 利用者は数十名、同時アクセスは最大50ユーザー、Userレコードは数十〜数百件(NFR3.1の仮定)。
- この規模では、データの分割(シャーディング)、専用のキャッシュ、非同期のキューなどの仕組みは、導入しない。内部設定DB(組込みDB)へのインデックス付きテーブルで足りる。
- Userの検索に使う列に、インデックスを設ける: `email`(一意)、`invitationToken`(非nullのとき一意)。`userId`は主キー。

## NFR3.2: ユーザー一覧のスケール方針

- `GET /api/users`は、ページングせずに、email昇順の全件を返す(BR4.14)。
- **見直しの条件**: Userレコード数が、全件返却で3秒(p95)を満たせなくなる場合、または1,000件を超える場合は、ページングの導入を再検討する。導入する場合は、C5の追補(クエリパラメータと応答の形式)が必要になる。この見直しの担当は、規模が想定を超えた時点の、保守の担当者とする。

## NFR3.3: ハッシュ計算のリソース

- 同時に実行できるハッシュ計算の数は、`HashConcurrencyLimiter`の許可数で決まる(既定はCPUコア数、performance-design.md NFR1.3)。メモリの最大値は、許可数×約19MiB。
- **JVMヒープの見込み**: 実行環境のヒープ(`-Xmx`)を決めるときは、「許可数×約19MiB」を、通常のアプリケーションの使用量とは別に見込む。コア数が多い環境や、コンテナ環境で、この見込みに収まらない場合は、`application.yml`で許可数を下げる。
- ハッシュのパラメータ(メモリ・反復回数)を強くした場合は、この見込みと許可数を、あわせて見直す。

## NFR3.4: 拡張方針

- user-managementのAPIは、リクエスト間で状態を持たない(セッションは持たない)。
- **プロセス内で持つ状態**: `HashConcurrencyLimiter`の許可、`InvitationMailExecutor`のスレッド、正規化後email単位の排他(`EmailLockRegistry`)は、**プロセスごと**に持つ。
- **複数プロセス構成にした場合の制約**: 上記はプロセスをまたいで共有されないため、次のとおり扱う。
  - ハッシュ計算の許可数と、招待の同時実行数は、プロセスごとの上限になる(全体の上限は、プロセス数倍になる)。メモリの見込みは、プロセスごとに立てる。
  - 同一emailへの同時招待の排他は、プロセスをまたげない。DBの一意制約違反(422)と、DBのロック待ちタイムアウト(503)が、最後の防御になる(reliability-design.md NFR4.2)。
  - 内部設定DBが組込みDBであることによる、複数プロセス構成の制約は、他ユニットと共通の事項として、全体設計(Infrastructure Design・運用設計)に委ねる。
- 想定を超えて同時接続数が増えた場合は、要件のNFR3どおり、リソースの追加(CPU・メモリ、必要ならプロセス数)で対応する。

## 根拠

- 要件: `nfr-requirements/scalability-requirements.md`(NFR3.1〜NFR3.4)、`inception/requirements-analysis/requirements.md`のNFR1・NFR3
- 機能設計: `functional-design/functional-spec.md`(W7)、`rules.md`(BR4.14)
- 契約: `inception/contract-design/contract-summary.md`のC5(`GET /api/users`)
