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
**Date:** 2026-09-07T14:51:40Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | nfr-design/traceability.json > NFR4のcoverageエントリ | `{ "id": "NFR4", "status": "OK", "target": "logical-components.md(JDBCドライバのアプリケーション内包、tech-stack-decisions.md継続)" }` とあるが、logical-components.mdの本文には「JDBCドライバ」「内包」「バンドル」等の語が一切登場せず、JDBCドライバのアプリケーション内包に関する記述が実際には存在しない(nfr-requirements/tech-stack-decisions.mdにのみ記載がある)。traceability.jsonが指し示す参照先ファイルにその内容がなく、開発者がNFR4の設計根拠を探す際に誤誘導される。 | logical-components.mdにJDBCドライバをアプリケーションに内包する旨を一文追記するか、traceability.jsonのtargetを実際に内容が存在するtech-stack-decisions.mdのみに修正する。 | New |
| R-02 | Minor | nfr-design/security-design.md > 認証情報の取り扱い、nfr-requirements/security-requirements.md > NFR-DATA.2 | 上流のnfr-requirements/security-requirements.mdは同stageの前回レビュー(R-01, Minor)で「NFR-DATA.2がBR6.1を『400または500』と過大に引用している」と既に指摘済みだが未修正のまま残っている。本stageのsecurity-design.mdはBR6.1(500のみ)と整合する記述をしており矛盾はないが、上流の未解消の食い違いは本Unitのnfr-requirements側で解消されるべき事項として申し送る。 | nfr-requirements/security-requirements.mdのNFR-DATA.2記述を「500として応答する」に修正する(schema-ingestion nfr-requirementsステージの改訂対象)。 | New |

### Validation Tool Results

本ステージ定義に紐づく自動検証ツールの明示的な指定は確認されなかったため、上流文書(nfr-requirements配下6ファイル、functional-design/rules.md・entities.md・functional-spec.md、および統合点としてinception/contract-design/contract-summary.mdのschema-ingestion API定義)との突き合わせによる手動検証のみを実施した。

| Tool | Result | Interpretation |
|---|---|---|
| (自動検証ツールなし) | N/A | 上流文書・shared契約との相互参照を手動で確認 |

### Summary

logical-components.mdが定義する2コンポーネント(RESTコントローラ・サービス)以外への参照は7ファイル中どこにも見つからず、audit-log Unitで見つかったような「実装帰属先が宙に浮く」Critical欠陥は本Unitには存在しない。traceability.jsonのupstream_idsとcoverageは、schema-ingestion nfr-requirements/traceability.jsonで確定した全10項目(NFR1.1・NFR1.2、NFR-DATA.1・NFR-DATA.2・NFR-AUTHZ.1、NFR2.1・NFR2.2、NFR-RESILIENCE.1、NFR3.1、NFR4)を過不足なく網羅している。ステートレス設計(永続エンティティなし)とlogical-components.mdがリポジトリコンポーネントを持たない設計とは整合しており、functional-design(rules.md BR1.1〜BR6.1、entities.md)との矛盾も見つからなかった。唯一、NFR4のtraceabilityエントリが実際にはlogical-components.mdに存在しない内容を参照先として主張している点をMajor指摘とし、加えて上流security-requirements.mdの既存Minor指摘(未解消)を申し送りとして記録した。Critical指摘はなくMajorも1件のみのためREADYと判定する。
