# Scalability Requirements — user-management (U4)

## NFR3.1: 想定規模

- 利用規模は要件のNFR3に従い、**数十名程度**(前身ツールの約10名規模からの拡大)とする。同時アクセスは最大50ユーザー(NFR1)。
- [assumption] Userレコード数は、数十〜数百件を上限の目安とする(利用者数に、無効化されたユーザーの累積を加えた規模)。要件は利用者数の目安のみを定めるため、Userレコード数の上限は本ステージの仮定である。
- UserPreferenceはUserと1対0または1で対応するため、Userと同じ規模となる。

## NFR3.2: ユーザー一覧のスケール方針

- `GET /api/users`は、ページングを行わず全件をemail昇順で返す(functional-spec.md W7、rules.md BR4.14、C5は配列を返す定義)。NFR3.1の規模では、NFR1.1の3秒(p95)を満たせる。
- Userレコード数が想定を大きく超えて増えた場合(目安として、全件返却で3秒目標を満たせなくなる、または1,000件を超えるとき)は、ページングの導入を再検討する。導入にはC5の追補が必要になる。

## NFR3.3: ハッシュ計算のリソース

- Argon2id(NFR1.2)は、1回の計算でメモリ約19MiBを使う。同時実行の上限(既定はCPUコア数、NFR1.3)により、ハッシュ計算が使うメモリの最大値は、おおむね「上限×19MiB」に抑えられる(例: 8コアなら約152MiB)。
- ハッシュ計算のパラメータを強くする場合(メモリ・反復回数を増やす場合)は、この見積もりと同時実行の上限をあわせて見直す。
- 上限の既定値(CPUコア数)は、CPUコア数が多い環境やコンテナ環境では、想定よりメモリを使う場合がある。実行環境のJVMヒープ(`-Xmx`)を決める際は、「同時実行の上限×約19MiB」を、ハッシュ計算のために別途見込む。この見込みに収まらない環境では、`application.yml`で上限を下げる。

## NFR3.4: 拡張方針

- 要件のNFR3どおり、想定を超えて同時接続数が増えた場合は、線形にリソースを追加することで対応できる設計とする。user-managementのAPIはリクエスト間で状態を持たない。
- 同時実行の上限(NFR1.3)は、プロセス単位の制御である。複数プロセスへ拡張する場合は、プロセスごとの上限とメモリ使用量をあわせて見積もる。
- 内部設定DBは組込みDB(例: H2)であり、複数プロセス構成での扱いを含む内部設定DB全体の制約は、他ユニットと共通の事項として全体設計(NFR Design・Infrastructure Design)に委ねる。招待受諾の並行更新は、DBの条件付き更新で整合を保つ(NFR4.1)。

## 根拠

- 要件: `inception/requirements-analysis/requirements.md` のNFR1・NFR3
- 機能設計: `construction/user-management/functional-design/functional-spec.md`(W7)、`rules.md`(BR4.14)
- 契約: `inception/contract-design/contract-summary.md` のC5(`GET /api/users`)
- 質問回答: `nfr-requirements-questions.md` Q1・Q2・Q4
