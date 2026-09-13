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

## Assumptions & Open Questions

None.

## Decomposition Plan Summary

- **契約範囲**: REST/HTTP契約(OpenAPI、frontend-ui向け)8件(list-engine, record-edit-engine, menu-navigation, authentication-service, user-management, audit-logging, config-import-export, schema-introspector)+ 内部Javaインタフェース契約(shared-schema、バックエンド間)5件(config-engine, permission-engine, user-management→authentication-service, menu-navigation→config-import-export, data-import-export)= 計13契約。
- **対象外**: packaging(U13)は実行時APIを持たないビルド成果物のため契約対象外。
- **エラー形式**: RFC 9457(Problem Details for HTTP APIs)。401/403/404/422を適切に使い分ける。
- **バージョニング**: 内部・REST問わず一切のバージョン番号・パス予約を行わない(`/api/...`)。
- **所有権**: 各契約はプロバイダー側ユニットが所有し、破壊的変更はコンシューマーとの合意を要する。

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
