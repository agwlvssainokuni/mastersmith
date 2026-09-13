# NFR Design Questions — data-import-export (U8)

`construction/data-import-export/nfr-requirements/`(確定済みNFR要件)・`construction/data-import-export/functional-design/functional-spec.md`(Assumptions & Open Questions)に基づき、data-import-exportユニットのNFR設計(具体的な技術パターン)を確定するための質問。

本プロジェクトはAWSクラウドへのデプロイを対象外としており(Operationフェーズ全ステージSKIP、単一実行可能WARとしてオンプレミス/任意環境で実行)、Infrastructure Design(3.4)もSKIP対象です。そのため本ステージの設計はSpring Boot単一プロセス内のアプリケーションレベルのパターンに限定し、AWSサービス選定は対象外とします。

## Q1: 実行者(actor)情報の取得方式

`functional-spec.md`のAssumptions & Open Questionsは、`ImportExecutedEvent.actor`の取得経路がC13契約に未定義であることを課題としていました。DataImportExportとrecord-edit-engineは同一プロセス内(同一スレッド)で呼び出されるため、Spring Securityの`SecurityContextHolder`(スレッドローカルで現在の認証済みユーザーを保持)を用いれば、C13のメソッドシグネチャにactorパラメータを追加せずにactorを取得できます。この方式を採用しますか。

- A. 採用する。DataImportExportは`SecurityContextHolder.getContext().getAuthentication()`から実行者のユーザーIDを取得する。これにより`functional-spec.md`で指摘したC13契約への追補は不要になる(Contract Designの追補課題を本ステージで解消する)
- B. 採用しない。`functional-spec.md`の指摘どおり、C13契約に明示的なactorパラメータを追加する方式のままとする
- X. Other (please specify)

[Answer]: B. 採用しない。functional-spec.mdの指摘どおり、C13契約に明示的なactorパラメータを追加する方式のままとする

## Q2: 列単位の実効READ権限一覧(permittedColumnNames)の受け渡し方式

同様に、`CsvExportRequest.permittedColumnNames`もC1契約への追補が必要な項目として指摘されていました。list-engineとDataImportExportは同一プロセス内の直接メソッド呼び出し(C13、Javaインタフェース)であるため、Java側のメソッドパラメータとして直接受け渡すことができます。これはHTTP契約(C1)とは別の内部インタフェース契約(C13)の話であるため、Q1のSecurityContextHolder方式とは独立した論点です。確認させてください。

- A. `permittedColumnNames`はC13(DataImportExportApiの内部Javaインタフェース)の`exportCsv`メソッドのパラメータとして直接受け渡す。C1(list-engineのfrontend-ui向けREST API)へのfilter/sortパラメータ追補は別途必要(こちらは内部呼び出しではなく、フロントエンドからのHTTPリクエストで運ばれる情報のため)
- B. 上記の理解と異なる想定がある(自由記述)
- X. Other (please specify)

[Answer]: A. permittedColumnNamesはC13(DataImportExportApiの内部Javaインタフェース)のexportCsvメソッドのパラメータとして直接受け渡す。C1へのfilter/sortパラメータ追補は別途必要

## Q3: リトライ・サーキットブレーカーの要否

`rules.md`・`components.md`により、DataImportExportの依存先はConfigEngine(同一プロセス内直接呼び出し)とAuditLogging(イベント発行、fire-and-forget)のみで、外部ネットワーク越しの呼び出し(HTTP、外部API)を持ちません。リトライ・サーキットブレーカー等の耐障害性パターンの適用要否を確認します。

- A. 適用しない。同一プロセス内の直接メソッド呼び出しにはネットワーク障害の概念がなく、`contract-summary.md`の前提(「バックエンドユニット間はJava例外で表現し、タイムアウト・リトライの概念自体を適用しない」)どおり、リトライ・サーキットブレーカーは設計しない
- X. Other (please specify)

[Answer]: A. 適用しない。同一プロセス内の直接メソッド呼び出しにはネットワーク障害の概念がなく、リトライ・サーキットブレーカーは設計しない

## Q4: DBコネクションプールの設定方針

CSVエクスポート(カーソル読み取り)・インポート(1トランザクションでの一括コミット)は、既存のSpring Boot標準のDBコネクションプール(HikariCP)をそのまま利用する、という理解でよいですか。大量データ処理専用の別プール等は必要ですか。

- A. 既存のHikariCPプール(アプリ全体で共有)をそのまま利用する。専用プールは設けない。ただし大量データのエクスポート(カーソルオープン中は接続を保持し続ける)・インポート(1トランザクション)が長時間接続を占有することを踏まえ、コネクションタイムアウト・最大接続数の設定値には注意を払う(具体的な数値はCode Generation時に確定)
- B. 大量データ処理専用の別コネクションプールを設ける
- X. Other (please specify)

[Answer]: A. 既存のHikariCPプール(アプリ全体で共有)をそのまま利用する。専用プールは設けない

## Q5: メトリクス実装方式

`observability-requirements.md`のNFR5.1(メトリクス要件)を、Spring Bootでどう実装しますか。

- A. Micrometer(Spring Boot標準の計装ライブラリ)でカウンタ・タイマーを実装し、後続のNFR設計(全体のOTELエクスポート基盤、FR14/NFR5全体)経由でエクスポートする
- X. Other (please specify)

[Answer]: A. Micrometer(Spring Boot標準の計装ライブラリ)でカウンタ・タイマーを実装し、後続のNFR設計(全体のOTELエクスポート基盤)経由でエクスポートする

## Q6: ロジカルコンポーネント構成(logical-components.md向け)

data-import-exportユニット内部のロジカルコンポーネント分割(例: CSVパース層、バリデーション層、永続化層を分離するか、単一のサービスクラスにまとめるか)について、設計上の方針はありますか。

- A. 責務ごとに分離する: (1) CSV読み書き(Apache Commons CSVラッパー)、(2) 行バリデーション・型変換(config-engineのvalidationRule適用)、(3) 一時バッファ管理、(4) DBコミット・監査イベント発行、を別クラスとして分離し、単体テストしやすくする
- B. 単一のサービスクラス(`DataImportExportService`)にまとめ、内部でメソッド分割する程度にとどめる
- X. Other (please specify)

[Answer]: A. 責務ごとに分離する: (1) CSV読み書き、(2) 行バリデーション・型変換、(3) 一時バッファ管理、(4) DBコミット・監査イベント発行、を別クラスとして分離する

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
