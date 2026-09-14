# Contract Design — 確認事項(MasterSmith)

Units Generationで確定したUnit依存DAG(`inception/units-generation/unit-of-work-dependency.md`)・Unit定義(`unit-of-work.md`)・コンポーネントカタログ(`inception/domain-design/components.md`)・要件定義書(`inception/requirements-analysis/requirements.md`)を踏まえ、ユニット間・外部との境界を正式な契約(Contract)として確定する前に、以下を確認します。

## Q1. 公開/外部API境界の有無

要件定義書の制約(「既存システム連携なし、社内認証基盤(SSO/LDAP等)との連携は対象外」「スタンドアロンアプリとして対象RDBMSに直接接続するのみ」)を踏まえ、本システムがシステム外部(他チーム・パートナー・パブリックインターネット)に公開するAPIはありますか?

- A. 外部公開APIなし。すべての契約はシステム内部(ブラウザ↔バックエンド間、バックエンドユニット間)のみとする(推奨)
- B. 将来の外部連携に備え、frontend-ui向けREST APIをOpenAPI形式で公開ドキュメント化し、外部公開も見据えた設計にする
- X. Other (please specify)

[Answer]: A(ただし、内部向けであっても標準のOpenAPI形式できちんと仕様化する。「内部だから簡易でよい」とはしない)

## Q2. 境界ごとの連携方式

Units Generationの`unit-of-work-dependency.md`では、frontend-ui↔各バックエンドユニット間はREST API(内部)、バックエンドユニット間(同一WAR内)は直接メソッド呼び出し(sync)と既に決定されています。この決定を契約設計にどう反映しますか?

- A. Units Generationの決定を踏襲する。frontend-ui↔バックエンドユニットの境界はREST/HTTP(OpenAPI仕様で契約化)、バックエンドユニット間(同一プロセス内)はJavaインタフェース契約(shared-schema形式で契約化)とする(推奨)
- B. 将来のマイクロサービス化に備え、バックエンドユニット間もすべてHTTP経由のREST APIとして契約化する
- X. Other (please specify)

[Answer]: A

## Q3. 契約の所有権

各契約(REST APIまたはインタフェース)は誰が所有し、破壊的変更をどう合意しますか?

- A. 各契約はプロバイダー側ユニット(提供側)が所有する。破壊的変更はコンシューマー側ユニットとの合意を要し、加法的変更(フィールド追加等)はコンシューマーが未知のフィールドを無視することで後方互換を保つ(推奨)
- B. アーキテクト(共通)がすべての契約を一元的に所有し、変更はアーキテクトの承認を要する
- X. Other (please specify)

[Answer]: A

## Q4. バージョニング・破壊的変更ポリシー

単一の実行可能WARとして全ユニットが同時にビルド・デプロイされる(Units Generation Q5=A モノリシックデプロイ)という前提を踏まえ、契約のバージョニング方針はどうしますか?

- A. バックエンドユニット間のJavaインタフェース契約はコンパイル時に整合性が保証されるためバージョニング不要とする。frontend-ui向けREST APIのみ、将来のバージョニングに備えたURLパス予約(例: `/api/v1/...`)は行うが、MVPスコープでは単一バージョンとし、破壊的変更が必要な場合はフロントエンドとバックエンドを同時にリリースする(単一WARで同時デプロイのため後方互換維持のコストが低い)(推奨)
- B. 全APIにセマンティックバージョニング(SemVer)を導入し、複数バージョンの共存をサポートする
- X. Other (please specify)

[Answer]: A(ただし、URLパスへのバージョン番号予約(`/api/v1/...`)も含め、内部・REST問わず一切のバージョニングを行わない。エンドポイントは`/api/...`のようにバージョン番号なしとする)

## Q5. エラー・タイムアウト・リトライ方針

`## Mandated`(project.md)により、利用者向けの入力データ検証エラーはフィールド単位のエラーメッセージとして返す(開発者向けスタックトレースではなく)ことが既に確定しています。この方針を契約レベルでどう具体化しますか?

- A. frontend-ui向けREST APIのエラーはRFC 7807(Problem Details for HTTP APIs)形式で統一し、フィールド単位のバリデーションエラーは`errors`配列で返却する。認証エラーは401、権限不足は403、リソース不在は404とする。バックエンドユニット間(同一プロセス内呼び出し)はJava例外で表現し、タイムアウト・リトライの概念自体を適用しない。frontend-uiからのHTTP呼び出しはブラウザ標準のfetchタイムアウトに委ね、アプリケーション層での自動リトライは行わない(MVPスコープ、推奨)
- B. サーキットブレーカー・指数バックオフによる自動リトライ戦略を今回のREST API契約に組み込む
- X. Other (please specify)

[Answer]: A(ただし、規格はRFC 7807ではなくRFC 9457(Problem Details for HTTP APIs、2023年7月発行、RFC 7807を正式にobsoleteした最新版)を採用する。フィールドの構成自体はRFC 7807から実質的な変更はないが、規格として最新のRFC 9457を明示的に参照する)

## Q6. C1(list-engine)エクスポートAPIへの検索条件・ソート順・列単位実効READ権限の反映方法

`construction/data-import-export/functional-design/functional-spec.md`のOpen Question(レビュー指摘R-01対応)により、W1(CSVエクスポート)は一覧画面の現在の検索条件・ソート順、および列単位の実効READ権限一覧(`permittedColumnNames`)を反映する必要があるが、既存のC1(`GET /records/export`)にはこれらのパラメータが定義されていなかった。どう追加しますか?

- A. `GET /records/export`に、既存の`GET /records`と同じ形状の`filter`・`sort`クエリパラメータを追加する。ただし`permittedColumnNames`(列単位の実効READ権限一覧)はWEB APIのパラメータにはしない。list-engineがサーバー側で(自身がPermissionEngineへ問い合わせ済みの)実効READ権限を算出し、内部インタフェース契約(C13)の`exportCsv`呼び出し時にのみ渡す。クライアント(ブラウザ)が権限一覧を指定できる余地を作らない(推奨)
- B. `permittedColumnNames`も含めすべてWEB APIのクエリパラメータとして公開する
- X. Other (please specify)

[Answer]: A(ユーザー確認済み: 権限一覧はWEB APIに追加する必要はなく、内部Java APIにのみ追加する)

## Q7. C13(data-import-export)インポートへの実行者ユーザーID(actor)の反映方法

`functional-spec.md`のOpen Question(レビュー指摘R-02対応)により、W2(CSVインポート)の監査ログイベント(`ImportExecutedEvent`)は実行者ユーザーIDを要求するが、既存のC13契約(`importCsv(tableConfigId, file)`)には実行者を渡すパラメータがなかった。どう追加しますか?

- A. `importCsv`の内部インタフェース契約(C13)にのみ`actor`パラメータを追加する(`importCsv(tableConfigId, file, actor)`)。record-edit-engineは自身のREST層(C2、Bearer認証済み)のSpring Security認証済みプリンシパルからユーザーIDを取得し、そのまま内部呼び出しの引数として渡す。WEB API(C2の`POST /records/import`)のリクエストボディに`actor`フィールドを追加する必要はない(推奨)
- B. WEB API(C2)のリクエストボディにも`actor`フィールドを追加する
- X. Other (please specify)

[Answer]: A(ユーザー確認済み: actorは内部Java APIにのみ追加し、WEB APIへの追加は不要)

## Q8. C9(config-engine)のColumnConfigへの主キー列情報の追加

`functional-spec.md`のOpen Question(レビュー指摘R-05対応)により、W2(CSVインポート)のupsert判定(INSERT/UPDATE)に必要な「対象テーブルの主キー列」情報が、既存のC9契約(`ColumnConfig`型)に定義されていなかった。どう追加しますか?

- A. `ColumnConfig`型に`isPrimaryKey: boolean`を追加する。単一主キー列を主な想定とし(複合主キーは今回のMVPスコープの主要な対象外)、schema-introspector(U2)が対象RDBMSのメタデータ読み取り時に判定し、`TableConfigDraft`(`writeTableConfigDraft`経由)に含める(推奨)
- B. `TableConfig`型に主キー列名を直接保持する(`ColumnConfig`側には追加しない)
- X. Other (please specify)

[Answer]: A

## Assumptions & Open Questions

None.

## Decomposition Plan Summary

- **契約範囲**: REST/HTTP契約(OpenAPI、frontend-ui向け)8件(list-engine, record-edit-engine, menu-navigation, authentication-service, user-management, audit-logging, config-import-export, schema-introspector)+ 内部Javaインタフェース契約(shared-schema、バックエンド間)6件(config-engine, permission-engine, user-management→authentication-service, menu-navigation→config-import-export, data-import-export, authentication-service→list-engine/record-edit-engine)= 計14契約(C14はレビュー指摘R-01対応で既に追加済み)。
- **対象外**: packaging(U13)は実行時APIを持たないビルド成果物のため契約対象外。
- **エラー形式**: RFC 9457(Problem Details for HTTP APIs)。401/403/404/422を適切に使い分ける。
- **バージョニング**: 内部・REST問わず一切のバージョン番号・パス予約を行わない(`/api/...`)。
- **所有権**: 各契約はプロバイダー側ユニットが所有し、破壊的変更はコンシューマーとの合意を要する。
- **本ラウンドで追加した3件の追補(data-import-exportユニットのCode Generation着手前に必要だった契約ギャップの解消)**:
  - **C1(list-engine `/records/export`)**: `filter`・`sort`クエリパラメータを追加(既存`GET /records`と同形状)。`permittedColumnNames`(列単位の実効READ権限一覧)はWEB APIには追加せず、C13の内部インタフェース経由でのみ渡す(Q6=A)。
  - **C13(data-import-export内部インタフェース)**: `exportCsv`に`filter`・`sort`・`permittedColumnNames`を追加、`importCsv`に監査ログ用の実行者ユーザーID`actor`を追加。いずれもWEB API(C1/C2)には現れない内部専用の拡張(Q6=A, Q7=A)。
  - **C9(config-engine `ColumnConfig`)**: `isPrimaryKey: boolean`を追加。単一主キー列を主な想定とする(Q8=A)。

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
