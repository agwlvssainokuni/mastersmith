# Security Design: permission

## セキュアバイデフォルト(テーブル単位)

サービスコンポーネントのテーブル単位権限判定ロジック(BR5.1)は、対象ロール・テーブルのTablePermissionレコードが存在しない場合、すべての操作(list/view/create/edit/delete)を拒否と判定する。これはコード上、「明示的な許可レコードが1件でも見つかった場合のみ許可を検討する」という否定形の実装(ホワイトリスト方式)とし、「拒否レコードがなければ許可」という肯定形の実装(ブラックリスト方式)にしない。

## デフォルト許可(カラム単位)

カラム単位権限判定ロジック(BR5.2)は、対象ロール・テーブル・カラムのColumnPermissionレコードが存在しない場合、editable(更新可)を返す。これは、既存テーブルへの新規カラム追加時に、権限設定を明示的に行うまでは全カラムが非表示になってしまう(業務データが見えなくなる)ことを避けるための設計判断であり、テーブル単位のデフォルト拒否とは意図的に非対称である。

## 管理系エンドポイントの認可

RESTコントローラが受け付ける管理系エンドポイント(ロール・グループのCRUD、メンバー管理、権限設定、ロール割当)は、Spring SecurityによりisAdminクレームを検証する。`GET /api/me/roles`(BR4.1、自身の切替可能ロール一覧取得)のみ、isAdminの有無を問わず全利用者がアクセス可能とする(認証済みであることのみを要求する)。

## 内部呼び出しAPIの認可境界

内部呼び出しAPI(契約#3・#20・#21・#22)は、呼び出し元Unit(dynamic-data-access・auth・account-management・config-management)自身が別途の認可コンテキスト(それぞれのUnitでのisAdmin検証・X-Active-Role検証等)で動作するプロセス内呼び出しであり、permission自身はこの経路にisAdmin検証を適用しない(NFR-AUTHZ.3)。プロセス内呼び出しであるため、ネットワーク境界を越える認証(APIキー等)も不要とする。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T15:03:12Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| grep(観測性設計と共有契約publishers一覧の整合) | observability-design.mdの記述`[config-management, dynamic-data-access, auth, account-management]`は、contract-summary.md 監査ログイベント契約(#5〜#8)のpublishers一覧と完全一致し、permissionが含まれないことも一致 | iteration 2で修正されたCritical指摘(監査ログ対象範囲の誤記述)は現在のファイルでも維持されている |
| grep(logical-components.mdのコンポーネント参照範囲) | RESTコントローラ・内部呼び出しAPI・サービス・リポジトリの4コンポーネント以外への参照なし。security-design.md/observability-design.md/reliability-design.md/performance-design.md/scalability-design.mdのいずれも同4コンポーネントの用語のみを用いている | コンポーネント境界の逸脱なし |
| grep(BR参照の解決確認) | 参照されているBR1.2・BR1.4・BR1.5・BR3.2・BR3.3・BR4.1・BR5.1・BR5.2・BR5.3はすべてfunctional-design/rules.mdに定義済み | 業務ルール参照はすべて解決する |
| grep(契約参照の解決確認) | 参照されている契約#3・#20・#21・#22はいずれもcontract-summary.mdに定義済みで、Consumer/Provider/Failure behaviorの記述内容もnfr-design側の記述と矛盾しない | 契約参照はすべて解決する |
| traceability.json上流ID確認 | NFR1.1・NFR1.2・NFR-AUTHZ.1〜.3・NFR-FAILSAFE.1(security/reliability両requirements)・NFR2.1・NFR2.2・NFR3.1・NFR5はすべてnfr-requirements配下の対応ファイルに存在する | 上流トレーサビリティに欠落なし |

### Summary

iteration 2で修正されたCritical指摘(observability-design.mdの監査ログ対象範囲記述と契約summary.mdのpublishers一覧との不整合)は、現在のファイル内容でも正しく維持されている。7ファイルの内容は前回iteration 2からの変更がなく、上流のnfr-requirements・functional-design(rules.md)・contract-summary.mdとの整合性、およびlogical-components.mdで定義された4コンポーネント以外への参照がないことを再確認し、新たな指摘は見つからなかった。
