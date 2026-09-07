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
**Date:** 2026-09-07T15:06:08Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | nfr-design/observability-design.md > 監査ログイベントによる可観測性 | nfr-requirements/observability-requirements.md NFR3.1は「イベント発行失敗は主処理をブロックしない(契約#5〜#8のasync仕様どおり)」を要件本文に明記しているが、nfr-design/observability-design.mdはactionType語彙と機微情報非出力のみを記述し、この非ブロッキング特性への言及が一切ない。同一契約(#5〜#8)を発行するauth Unitのnfr-design/observability-design.mdは同種の記述末尾に「イベント発行失敗は主処理をブロックしない。」を明示的に記載しており、本Unitのみこの記述が欠落している(traceability.jsonはNFR3.1をOKとして完全網羅と主張しているが、要件本文の一部が設計成果物に反映されていない) | observability-design.md(または関連してreliability-design.mdの契約失敗時の扱いの節)に、AuditableActionOccurredEvent発行失敗時に主処理をブロックしない旨(契約#5〜#8のasync仕様に基づく)を一文追記し、traceability.jsonのNFR3.1 targetの記述粒度をauth Unitと揃える | New |
| R-02 | Minor | nfr-design/observability-design.md > メトリクス・分散トレーシング | 「本Unit固有の追加実装は行わない」とAuthやOTel委任を明記しているが、schema-ingestion呼び出し失敗(NFR-FAILSAFE.1)やインポート拒否(NFR-CONSISTENCY.1)といった本Unit固有の異常系について、標準メトリクス以外に着目すべきメトリクス/ログ観点(例: インポート拒否件数、schema-ingestion呼び出し失敗回数)の記録要否が未検討のまま残っている | 標準計装に委ねる判断自体は妥当だが、異常系の可観測性について「標準メトリクスでカバー範囲内と判断し追加実装は行わない」という判断理由を一文補足すると、繰延べでなく明示的な決定として残る | New |

### Validation Tool Results

このステージにはツールによる自動検証は指定されていない(手動でのクロスリファレンス検証のみ実施)。performance-design.md/security-design.md/scalability-design.md/reliability-design.md/observability-design.md/logical-components.mdの6ファイルを、対応するnfr-requirements(performance/security/scalability/reliability/observability-requirements.md、NFR1.1〜NFR3.2の14項目)、functional-design/rules.md(BR2.1〜BR2.8・BR4.3・BR5.1〜BR5.3・BR6.1〜BR6.2・BR7.1・BR8.1)、entities.md、inception/contract-design/contract-summary.md(契約#1・#22・#23、GET /api/menuのOpenAPIブロック)と突き合わせ、数値・挙動レベルの不一致は見つからなかった。traceability.jsonのupstream_ids(14件)はnfr-requirements側の全14詳細ID(NFR1.1/1.2、NFR-AUTHZ.1/2、NFR-DATA.1/2/3、NFR2.1/2.2、NFR-CONSISTENCY.1/2、NFR-FAILSAFE.1、NFR3.1/3.2)と過不足なく一致している。logical-components.mdで定義された3コンポーネント(RESTコントローラ・サービス・リポジトリ)以外への参照は他6ファイルに存在しない。functional-spec.mdの`## Review`セクションに記録された既知の繰延べ事項R-03(entities.md DbConnection行の「契約#19注記」という誤った出典表記)・R-04(TableConfig.foreignKeysのnullable記法の曖昧さ)は、いずれもentities.md固有の表記問題であり、security-design.mdは当該箇所を「entities.md」とのみ参照して誤った出典を引用していないため、本ステージの成果物への悪影響(伝播)は確認されなかった。ただし、nfr-requirements/security-requirements.md NFR-DATA.1は依然として「契約#19注記」という誤った出典を保持しており、これはnfr-requirementsステージ側の既承認artifactの問題であり本レビューのスコープ外として記録するに留める。

### Summary

7ファイルはnfr-requirements・functional-design(rules.md/entities.md)・共有契約(contract-summary.md)と数値・挙動レベルで正確に一致しており、ファイル間の矛盾も見当たらない。ただしNFR3.1が要件本文に明記する「イベント発行失敗の非ブロッキング特性」がobservability-design.mdに反映されておらず、同一契約を発行するauth Unitの設計との記述粒度に差異がある(R-01、Major)。この1件のMajorとMinor1件(R-02)はいずれも実装を妨げるほどのアーキテクチャ上の欠陥ではなく、一文の追記で解消可能な文書完成度の問題であるため、READYと判定する。
