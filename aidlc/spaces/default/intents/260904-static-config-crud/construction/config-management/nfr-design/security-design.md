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
**Date:** 2026-09-08T21:04:06Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | observability-design.md > 監査ログイベントによる可観測性 | イベント発行失敗が主処理をブロックしない旨の記載(前回iteration 2で追記)が現行ファイルに維持されているか確認した。「イベント発行失敗は主処理をブロックしない(契約#5〜#8のasync仕様どおり)。」の一文が7行目に維持されている。 | なし。維持を確認済み。 | Resolved |
| R-02 | Minor | observability-design.md > メトリクス・分散トレーシング | 異常系(schema-ingestion呼び出し失敗、キャッシュ不整合等)発生時にActuator/Micrometer標準計装以外の追加可観測性(専用メトリクス・エラー種別の分類等)を設けない判断理由が明記されていない | 判断理由(標準計装で十分と判断した根拠、または将来検討事項であることの明記)をobservability-design.mdに一言補足する。非ブロッキングとして継続受理する | Unresolved |

### Validation Tool Results

このステージにはツールによる自動検証は指定されていない(手動でのクロスリファレンス検証のみ実施)。今回の再レビューではconfig-management自身の7ファイルの内容変更は無く(security-design.md自体からの旧Reviewセクション除去のみ)、以下を再確認した。
- nfr-requirements/配下の全5ファイル(performance/security/scalability/reliability/observability-requirements.md)とnfr-design/配下の対応ファイルとの間で、数値・挙動レベルの不一致は検出されなかった。
- functional-design/rules.md のBR2.1・BR4.3・BR5.1〜BR5.3・BR6.1〜BR6.2・BR7.1・BR8.1と、nfr-designの記述との間で矛盾は検出されなかった。
- inception/contract-design/contract-summary.md の契約#1(schema-ingestion→config-management)、契約#5(config-management→audit-log)、契約#22(permission→config-management)の呼び出し方向は、logical-components.md・security-design.md・reliability-design.mdの記述と一致している。
- logical-components.mdで定義された3コンポーネント(RESTコントローラ・サービス・リポジトリ)以外への参照は見当たらない(schema-ingestion・permission・内部H2への言及は許容されるUnit外部依存)。
- traceability.jsonのNFR1.1〜NFR3.2の全14件がstatus: OKでカバーされており、参照先ファイル名・見出しも現行ファイルと一致している。

### Summary

今回の再レビューは、別Unit(auth)のsecurity-design.mdの不正なStatus値修正に伴うステージ全体のRequest Changesを契機とするものであり、config-management自身の成果物には内容変更がない。前回iteration 2で修正されたMajor指摘(R-01、イベント発行失敗の非ブロッキング特性の明記)は維持されており、繰延べMinor指摘(R-02、異常系可観測性の判断理由未記載)も非ブロッキングとして継続受理する。上流要件・functional-design・契約summaryとの整合性にも新たな不一致は見つからなかった。
