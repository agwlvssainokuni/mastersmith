# NFR Requirements Questions — config-engine (U1)

`inception/requirements-analysis/requirements.md`のNFR1〜NFR8（アプリ全体の非機能要件）、および`construction/config-engine/functional-design/`（entities.md・rules.md・functional-spec.md）に基づき、config-engineユニット固有の非機能要件を確定するための質問。

config-engineはほぼ全ての他ユニット（list-engine, record-edit-engine, permission-engine, schema-introspector, data-import-export, config-import-export）から呼び出される高頻度パスであり、アプリ全体のNFR1（応答時間3秒以内p95）を満たす上で、config-engine自身のレイテンシが律速要因にならないことが重要となる。

## Q1: config-engine呼び出しの性能目標

NFR1は「一覧・詳細画面とも応答時間3秒以内（95パーセンタイル）」というアプリ全体の目標を定めています。list-engine/record-edit-engineは1リクエストあたりgetTableConfig・getColumnConfigsを複数回呼び出す可能性があり、config-engine自身の応答時間目標を明確にする必要があります。

- A. getTableConfig/getColumnConfigs等の内部API呼び出しは、単体で50ms以内（p95）を目標とする（同一プロセス内呼び出しのため、ネットワーク往復を含まない）
- B. Aと同様だが、目標値を100ms以内（p95）とする
- C. 個別の目標値は定めず、NFR1のアプリ全体目標（一覧・詳細画面3秒以内）の充足をもってconfig-engineの性能目標とする（個別分解は行わない）
- X. Other (please specify)

[Answer]: A. getTableConfig/getColumnConfigs等の内部API呼び出しは、単体で50ms以内（p95）を目標とする（同一プロセス内呼び出しのため、ネットワーク往復を含まない）

## Q2: 設定データのキャッシュ戦略

config-engineが保持するTableConfig/ColumnConfig/TranslationEntryは、一覧・詳細編集のリクエストごとに読み取られる高頻度アクセスデータですが、更新頻度は低い（管理者による設定変更時のみ）と想定されます。キャッシュ戦略はどうしますか。

- A. アプリケーション内メモリキャッシュを用いる。起動時に全設定を読み込みキャッシュし、管理画面からの更新時にキャッシュを更新する（内部設定DBへの都度アクセスを避ける）
- B. キャッシュを設けず、呼び出しのたびに内部設定DB（H2）へ問い合わせる（H2は組込みDBでネットワーク往復がないため、キャッシュなしでも許容範囲と判断する）
- C. 単一インスタンス構成（NFR3参照）を前提に、Aと同様のアプリケーション内メモリキャッシュとするが、TTLベースの再読込も併用する
- X. Other (please specify)

[Answer]: A. アプリケーション内メモリキャッシュを用いる。起動時に全設定を読み込みキャッシュし、管理画面からの更新時にキャッシュを更新する（内部設定DBへの都度アクセスを避ける）

## Q3: 内部設定DB（H2）の永続化・バックアップ方針

`project.md`の制約により、内部設定DB（RBAC・ユーザ管理・監査ログ・表示設定を含む）は業務データ用RDBMSとは別接続の組込みDB（H2等）を用いる方針が確定しています。config-engineが保持するTableConfig/ColumnConfig/TranslationEntryを含む内部設定DB全体の永続化モードとバックアップ方針を確認します。

- A. H2をファイルモード（永続化ファイル）で運用し、定期的なファイルバックアップ（例: 日次）を運用手順として想定する。自動バックアップの仕組み自体は本ワークフローのスコープ外（Operationフェーズ未対象）とし、方針の記録のみとする
- B. Aと同様だが、バックアップの詳細な頻度・世代管理方針もこのNFR要件として明記する
- C. 具体的なバックアップ方針はこのユニットのNFR要件としては扱わず、後続のOperationフェーズ（現状スコープ外）に委ねる。本ユニットでは「H2をファイルモードで永続化する」という事実のみ記録する
- X. Other (please specify)

[Answer]: A. H2をファイルモード（永続化ファイル）で運用し、定期的なファイルバックアップ（例: 日次）を運用手順として想定する。自動バックアップの仕組み自体は本ワークフローのスコープ外（Operationフェーズ未対象）とし、方針の記録のみとする

## Q4: config-engine固有の可観測性項目

NFR5（可観測性）はアプリ全体でHTTPリクエストのレイテンシ・エラー率・ヘルスチェック・構造化ログをOTEL基盤へエクスポートすることを求めています。config-engineは内部呼び出し（Javaメソッド呼び出し）が中心でHTTPエンドポイントを直接持たないため、config-engine固有に追加すべき可観測性項目（メトリクス・ログ）はありますか。

- A. 追加なし。呼び出し元（list-engine等）が発行するHTTPリクエストのレイテンシ・エラー率メトリクスでカバーされるとみなし、config-engine固有の追加メトリクスは設けない
- B. fail-fast検証エラー（ConfigValidationException）の発生をカウントするメトリクスと、発生時の構造化ログ（エラーとなったフィールド・ルール種別を含む）を追加する
- C. Bに加え、schema-introspectorからのドラフト取り込み（スキップ／新規作成の件数）を記録するメトリクスも追加する
- X. Other (please specify)

[Answer]: A. 追加なし。呼び出し元（list-engine等）が発行するHTTPリクエストのレイテンシ・エラー率メトリクスでカバーされるとみなし、config-engine固有の追加メトリクスは設けない

## Q5: 設定データの想定データ量・成長見込み

NFR3（スケーラビリティ）はアプリ全体の同時アクセス数（数十名規模）を扱っていますが、config-engineが保持するTableConfig/ColumnConfig/TranslationEntryの想定データ量（スキーマ内テーブル数・カラム数・言語数等）は明記されていません。想定規模を確認します。

- A. 1業務プロファイルあたりテーブル数は数十〜百程度、カラム数はテーブルあたり数個〜数十個、TranslationEntryは(ColumnConfig数+TableConfig数)×2言語（日英）程度を上限の目安とする。これらはキャッシュ全読み込み（Q2）を前提としても十分小さい規模である
- B. Aと同程度だが、将来的な業務プロファイル追加（設定の入れ替え運用）を考慮し、テーブル数百〜千程度まで許容する設計とする
- C. 具体的な数値目標は設けず、定性的に「キャッシュ可能な小規模データである」ことのみ記録する
- X. Other (please specify)

[Answer]: A. 1業務プロファイルあたりテーブル数は数十〜百程度、カラム数はテーブルあたり数個〜数十個、TranslationEntryは(ColumnConfig数+TableConfig数)×2言語（日英）程度を上限の目安とする。これらはキャッシュ全読み込み（Q2）を前提としても十分小さい規模である

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
