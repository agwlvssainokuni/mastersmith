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
**Date:** 2026-09-08T17:26:22Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | nfr-design/traceability.json > NFR4のcoverageエントリ | traceability.jsonはNFR4を「logical-components.md(JDBCドライバのアプリケーション内包)」に対応付けているが、requirements.mdのNFR4は「フロントエンド/バックエンドを単一の実行可能WARにパッケージングすること」であり、JDBCドライバのバンドルとは異なる関心事である。JDBCドライバ内包の根拠は本来project.md Mandated(TC-01由来)であり、NFR4のtarget記述として誤っている。 | traceability.jsonのNFR4行を、実際に対応する上流ID(該当するNFR、または project.md Mandated由来である旨)に修正するか、NFR4(WARパッケージング)に対応する記述を別途追加する。 | Unresolved |
| R-02 | Minor | nfr-design/scalability-design.md ならびに traceability.json > NFR2.1/NFR2.2 | nfr-requirements/scalability-requirements.mdはNFR2.1(単一インスタンス・単一業務前提)とNFR2.2(走査の対象範囲)を分けて定義しているのに対し、nfr-design側の記述粒度がやや粗く、両IDの対応関係がtraceability.jsonの記載だけでは読み取りづらい。実質的な設計内容自体に誤りはない。 | scalability-design.mdの見出し・本文でNFR2.1/NFR2.2それぞれに対応する記述であることを明示し、traceability.jsonのtarget記述の粒度をnfr-requirements側に揃える。 | New |

### Validation Tool Results

本ステージ定義に紐づく自動検証ツールの明示的な指定は確認されなかったため、上流文書(nfr-requirements配下の6ファイル、functional-design/rules.md・functional-spec.md・entities.md)との突き合わせによる手動検証を実施した。

| Tool | Result | Interpretation |
|---|---|---|
| (自動検証ツールなし) | N/A | 上流文書・logical-components.mdの2コンポーネント定義との相互参照を手動で確認 |

### Summary

今回はnfr-designステージ全体に対する2回目のRequest Changes(auth Unitのsecurity-design.mdにおけるStatus値不正の是正が目的)を受けた再確認であり、schema-ingestion自身の7ファイルは内容を一切変更していない。performance-design.md/security-design.md/scalability-design.md/reliability-design.md/observability-design.mdはいずれもnfr-requirements配下の対応するNFR(NFR1.1〜NFR-RESILIENCE.1)およびfunctional-design/rules.mdのBR5.1・BR6.1と整合しており、logical-components.mdで定義された2コンポーネント(RESTコントローラ・サービス)以外への参照も確認されなかった。既知の繰延べ指摘R-01(traceability.json NFR4のtarget記述不一致、Major・Unresolved)とR-02(NFR2表記粒度、Minor・New)はいずれも非ブロッキングとして踏襲し、新規のCritical/Major指摘はないためREADYと判定する。
