# Security Design: schema-ingestion

## 認可アーキテクチャ

全操作(接続テスト・スキーマ一覧・プレビュー)は、RESTコントローラ層でアクセストークンのisAdminクレームを検証する(NFR-AUTHZ.1)。isAdminがfalseの場合は403(RFC 7807形式)を返す。

## 認証情報の取り扱い

本Unit(サービスコンポーネント)は、config-managementから渡されたDbConnection.credentialRefの復号済みの値をJDBC接続にそのまま使用するのみであり、暗号化・復号ロジック、暗号鍵の保持・参照は行わない(NFR-DATA.1、暗号化・復号はconfig-management側の責務)。復号済みの認証情報は、接続確立に使用した後、メモリ上に保持し続けず、リクエスト処理の終了とともに破棄する(コネクションプールを持たない設計、performance-design.md参照)。

## エラー内容の制限

接続失敗・走査失敗時の例外メッセージ(BR6.1により呼び出し元へ伝播)には、認証情報(パスワード等)の値そのものを含めない(NFR-DATA.2)。JDBCドライバが例外メッセージに接続文字列を含める場合があるため、サービスコンポーネントは例外を呼び出し元へ伝播させる前に、認証情報部分をマスクまたは除去したメッセージへ置き換える。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T20:49:59Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | nfr-design/traceability.json > NFR4のcoverageエントリ | NFR4のtargetが「logical-components.md(JDBCドライバのアプリケーション内包、tech-stack-decisions.md継続)」となっているが、logical-components.mdの本文にはJDBCドライバのアプリケーション内包(バンドル)に関する記述が実際には存在しない(同ファイルが扱うのはJDBC DatabaseMetaDataによるRDBMS方言差異の吸収ロジックのみ)。JDBCドライバ内包の記述は上流のnfr-requirements/tech-stack-decisions.mdにのみ存在する。前回レビュー(iteration 1、READY・Major 1件/Minor 1件)で指摘済みの繰延べ事項であり、その後dynamic-data-access Unitの表記フォーマット不具合を理由とするnfr-designステージ全体へのRequest Changesでper-unit reviewステータスがリセットされたことに伴う再確認だが、schema-ingestion Unit自身の成果物ファイルの内容(本Major指摘の対象箇所を含む)は前回レビュー時点から変更されておらず、指摘は未解消のまま残っている。 | traceability.jsonのNFR4エントリのtargetを「tech-stack-decisions.md(JDBCドライバのアプリケーション内包)」を主参照とする記述に修正するか、logical-components.mdの「コンポーネント構成」節にJDBCドライバをアプリケーションに内包する旨(project.md Mandated根拠)を明記したうえでtargetの記述と実体を一致させる。 | Unresolved |
| R-02 | Minor | nfr-design/scalability-design.md > スケーリングアーキテクチャ | 「単一インスタンス構成(NFR2)」と、上流IDの粒度(NFR2.1/NFR2.2)ではなく上位の「NFR2」を直接引用している。同ファイル内の次節は「NFR2.2」と粒度を揃えて引用しており、また本ファイル自身のtraceability.jsonはこの記述をNFR2.1のcoverage対象としているため、本文中の参照ID表記がNFR2.1ではなくNFR2になっている点で軽微な不整合がある。 | 「単一インスタンス構成(NFR2)」を「単一インスタンス構成(NFR2.1)」に修正し、traceability.jsonの対応関係と本文引用IDの粒度を揃える。 | New |

### Validation Tool Results

本ステージ定義に紐づく自動検証ツールの明示的な指定は確認されなかったため、上流文書(nfr-requirements配下の各NFR要件ファイル、functional-design/rules.md BR1.1〜BR6.1、functional-spec.md、entities.md)との突き合わせによる手動検証のみを実施した。

| Tool | Result | Interpretation |
|---|---|---|
| (自動検証ツールなし) | N/A | 上流文書・BR定義・traceability.jsonとの相互参照を手動で確認 |

### Summary

今回のレビュー依頼は、別Unit(dynamic-data-access)の表記フォーマット不具合修正に伴うnfr-designステージ全体へのRequest Changesでper-unit reviewステータスが一律リセットされたことによる再確認であり、schema-ingestion Unit自身の7ファイル(performance-design.md、security-design.md、scalability-design.md、reliability-design.md、observability-design.md、logical-components.md、traceability.json)はいずれも前回READY判定時点から内容の変更が確認されなかった。performance-design.md/reliability-design.md/observability-design.md/logical-components.mdは、対応する上流NFR要件(NFR1.1/NFR1.2、NFR-RESILIENCE.1、NFR3.1、NFR4以外)およびfunctional-design/rules.mdのBR1.1〜BR6.1と整合しており、logical-components.mdで定義された「RESTコントローラ」「サービス」以外のコンポーネントへの参照もない。既知のMajor指摘(traceability.jsonのNFR4参照不整合)は前回同様未解消のまま繰延べ事項として記録し、新たにscalability-design.mdの参照ID粒度に関するMinor指摘を追加した。Critical指摘はなく、Majorも1件(繰延べ)にとどまるため、前回同様READYと判定する。
