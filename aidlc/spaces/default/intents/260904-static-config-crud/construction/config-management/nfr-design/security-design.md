# Security Design: config-management

## 管理者限定アクセス(GET /api/menuを除く)

RESTコントローラは、config-managementの全操作(DB接続先設定・テーブル設定・メニュー構成・エクスポート/インポート・キャッシュクリア)についてisAdminクレームを検証する(BR7.1)。isAdminがfalseの場合は403を返す。

## GET /api/menuの権限フィルタ(例外)

GET /api/menu(契約#22・#23)は、isAdmin検証の代わりにX-Active-Roleヘッダーの検証を行う。X-Active-Roleがアクセストークンのrolesクレームに含まれない場合は403を返す。それ以外の場合、サービスコンポーネントが契約#22でpermission UnitからcanList=trueのtableId集合を取得し、テーブルノードをその集合でフィルタする(BR4.3)。

## 業務DB接続情報の暗号化保持

DbConnection.credentialRefは対称鍵暗号でリポジトリへ保存する(entities.md)。暗号鍵はソースコード・Gitリポジトリにコミットせず、環境変数または権限600のローカル設定ファイル経由で外部化する(project.md Forbidden)。具体的な暗号アルゴリズム(AES-GCM等)の選定はcode-generation段階で行う。

## エクスポート時の非復号

設定エクスポート(BR5.1)は、DbConnection.credentialRefを暗号化された値のまま出力し、復号は行わない。

## ログへの機微情報非出力

credentialRefの実値(復号後の業務DB認証情報)は、アプリケーションログ・監査ログのいずれにも出力しない。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T13:33:20Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | construction/config-management/nfr-design/observability-design.md > 監査ログイベントによる可観測性 | observability-design.mdが、監査ログイベント発行失敗時の非ブロッキング特性(契約#5〜#8のasync仕様、姉妹Unit authでは明記済み)への言及を欠いていた。 | 「イベント発行失敗は主処理をブロックしない(契約#5〜#8のasync仕様どおり)。」という一文を該当節に追記する。 | Resolved |
| R-02 | Minor | construction/config-management/nfr-design/observability-design.md > メトリクス・分散トレーシング | 異常系(schema-ingestion呼び出し失敗時の5xx応答、監査ログ発行失敗等)の可観測性について、Spring Boot Actuator + Micrometerの標準メトリクスに委ねる判断理由(本Unit固有の追加実装を行わない根拠)が記載されていない。 | 標準メトリクスで異常系を十分捕捉できると判断した理由、またはTBDである旨を一文で補記する。 | Unresolved |

### Validation Tool Results

本stageに割り当てられた自動検証ツールの実行結果なし(スキーマ検証・循環依存検証等の専用ツールは本stage定義に列挙されていない)。手動でのクロスリファレンス検証を実施した。

- logical-components.mdで定義された3コンポーネント(RESTコントローラ・サービス・リポジトリ)以外への参照なし。他Unitへの言及はいずれも契約IDを伴うプロセス内呼び出し(契約#1: schema-ingestion、契約#22: permission、契約#5〜#8: audit-log)であり、契約summary.mdの記載と整合する。
- performance-design.md/scalability-design.md/reliability-design.md/security-design.md/observability-design.mdが引用するBR ID(BR2.1〜BR2.8、BR4.3、BR5.1〜5.3、BR6.1〜6.2、BR7.1、BR8.1)は、いずれもfunctional-design/rules.mdに実在することを確認した。
- traceability.jsonの14件のupstream_ids(nfr-requirements配下の全NFR ID)は全件coverageに列挙され、statusはすべてOK。NFR3.1のtargetはobservability-design.mdの今回追記部分(発行失敗時の非ブロッキング特性)を反映済みで、参照先の記述と一致する。
- 契約summary.md 107行目「asyncという記載は、監査ログの記録失敗が呼び出し元の主処理を止めないことを意味する」と、observability-design.mdの追記文とを突き合わせ、内容が一致することを確認した。

### Summary

前回Major指摘(R-01)はobservability-design.mdへの一文追記により解消を確認した。前回Minor指摘(R-02)は今回未修正のため既知の繰延べ事項として維持する(非ブロッキング)。その他、上流NFR要件・rules.md・contract-summary.mdとの整合性、logical-components.md外への参照の有無、traceability.jsonの網羅性を再検証したが新規の指摘事項はなかった。
