# Units Generation — 確認事項(MasterSmith)

Domain Designで確定したコンポーネントカタログ(`inception/domain-design/components.md`)・ADR(`inception/domain-design/decisions.md`)・要件定義書(`inception/requirements-analysis/requirements.md`)を踏まえ、Construction フェーズで並行開発可能な単位(Unit of Work)へと分解する前に、以下を確認します。

## Q1. ユニット境界の分割方針

Domain Designでは11個のコンポーネント(ConfigEngine, SchemaIntrospector, ListEngine, RecordEditEngine, PermissionEngine, UserManagement, AuthenticationService, MenuNavigation, AuditLogging, ConfigImportExport, DataImportExport)が確定しています。技術スタック(`team-practices.md`)により、バックエンドはJava 25 + Spring Boot単一実行可能WAR、フロントエンドはTypeScript + Vite + Reactと既に確定済みです。この前提の下、Unit of Workへの分割方針はどうしますか?

- A. ドメイン親和性でグルーピングする(推奨)。緊密に協調する・循環依存があるコンポーネント同士を同一ユニットにまとめ、5〜7ユニット程度に抑える。具体的には次のような案を想定する: (1) config-and-schema = ConfigEngine + SchemaIntrospector、(2) permission-and-audit = PermissionEngine + AuditLogging(意図的な循環依存をユニット内に閉じ込める)、(3) identity-and-access = UserManagement + AuthenticationService、(4) record-engine = ListEngine + RecordEditEngine + DataImportExport(一覧・編集・業務データIOの中核エンジン)、(5) navigation-and-config-io = MenuNavigation + ConfigImportExport、(6) frontend-ui = フロントエンド一式、(7) packaging = WARパッケージング・ビルド定義(FR13)
- B. コンポーネント単位でそのまま1コンポーネント=1ユニットとする(11+α個の細粒度ユニット)。Domain Designの境界を最も忠実に反映するが、Construction側の per-unit ステージ運用コストが増える
- C. バックエンド全体を1つの大きなユニットとし、フロントエンドのみ別ユニットとする(2ユニット、単一WARというデプロイ形態を最も強く反映するが並行開発の余地がほぼない)
- X. Other (please specify)

[Answer]: B

## Q2. ユニット粒度(推定ユニット数)

Q1の方針を踏まえ、推定ユニット数の目安はどの程度が適切ですか?

- A. 5〜7ユニット程度(Q1のAで示したドメイン親和性グルーピングに対応、推奨)
- B. 10ユニット以上(コンポーネントごとの細粒度、Q1のBに対応)
- C. 3ユニット以下(バックエンド/フロントエンド/パッケージングのみ、Q1のCに対応)
- X. Other (please specify)

[Answer]: B

## Q3. 依存順序・並行開発の許容

Unit of Work間の依存関係(DAG)について、並行開発の余地をどう扱いますか? (2.9 Delivery Planningでの経済的な着手順序決定とは別に、本ステージではトポロジーのみを扱う)

- A. 依存のないユニット群は並行して開発してよいとし、DAG上に複数の妥当なトポロジカル順序が存在することを許容する(推奨)
- B. 厳密に単一のトポロジカル順序のみを許容し、ユニット間の並行開発は行わない前提とする
- X. Other (please specify)

[Answer]: A

## Q4. ユニット間の連携方式(統合ポイント)

ユニット間でデータ・処理をどう連携させますか? Domain DesignのADR-005では、AuditLoggingへの連携のみイベント発行(疎結合)とし、それ以外は同期呼び出しとすることが決定されています。

- A. 同一プロセス内のJavaインタフェース呼び出し(直接メソッド呼び出し)を基本とし、監査ログ記録(AuditLogging)への連携のみドメインイベント発行とする(ADR-005を踏襲、推奨)
- B. ユニット間連携はすべて内部REST API経由とする
- C. ユニット間連携はすべてイベント駆動(メッセージング)にする
- X. Other (please specify)

[Answer]: A

## Q5. デプロイモデル

Unit of Work単位のデプロイ形態はどうしますか? `team-practices.md`で技術スタックが実行可能WAR形式(フロントエンド同梱、Spring Bootから配信、CORS設定不要)と既に確定しています。

- A. モノリシックデプロイ — 全バックエンドユニットとフロントエンド成果物を単一の実行可能WARに統合する(確定済み技術スタック制約に整合、推奨)
- B. 独立デプロイ — ユニットごとに個別のデプロイ可能アーティファクトとする(確定済み技術スタックと矛盾するため非推奨)
- C. ハイブリッド — 一部のユニット(例: packaging)のみ別成果物とし、それ以外は単一WARに統合する
- X. Other (please specify)

[Answer]: A

## Assumptions & Open Questions

None.

## Decomposition Plan Summary

- **境界戦略**: Q1=B(コンポーネント単位1:1)。Domain Designの11コンポーネントをそれぞれ1ユニットとし、加えて `frontend-ui`(UI)・`packaging`(WARビルド)を追加した13ユニット構成とする。
- **推定ユニット数**: 13(Q2=B、10ユニット以上)。
- **依存構造**: Unit依存DAGはDomain Designのsync依存のみを踏襲し、event依存(AuditLoggingへの発行)はDAGから除外して循環を解消する(Q4=A)。並行開発可能な層構造は7層。
- **kind割当**: config-engine, schema-introspector, permission-engine, user-management, authentication-service, menu-navigation, audit-logging, data-import-export, config-import-export, list-engine, record-edit-engine = service。frontend-ui = ui。packaging = packaging。
- **デプロイモデル**: 全ユニットembedded(単一実行可能WARに統合、Q5=A)。packagingはビルド成果物そのもの。

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
