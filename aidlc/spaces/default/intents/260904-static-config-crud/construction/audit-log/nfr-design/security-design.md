# Security Design: audit-log

## 認可アーキテクチャ

全操作(参照・エクスポート・削除・設定変更)は、Spring SecurityのメソッドセキュリティまたはHTTPフィルタ層で、アクセストークンのisAdminクレームを検証する(BR5.1)。isAdminがfalseまたは未設定の場合は403(RFC 7807形式の`ProblemDetail`)を返し、コントローラのビジネスロジックには到達させない(認可はコントローラ層の入口で完結させ、サービス層以降には認可判定を持ち込まない)。

## 入力検証

- エクスポートの`format`パラメータ(BR3.1): 許容値(csv/json)をenum型のBean Validationアノテーション(`@Pattern`または専用enum)で検証し、違反時は400(RFC 7807)を返す。
- 削除の`olderThanDays`パラメータ(BR4.3): `@Min(1)`のBean Validationアノテーションで1以上の整数であることを検証する。
- 設定変更の`retentionDays`(BR4.2): 同様に`@Min(1)`で検証する。

いずれもコントローラのDTOレベルで検証し、不正な値がサービス層・リポジトリ層へ到達しない設計とする。

## 記録の完全性(改ざん耐性)

AuditLogEntryのリポジトリは更新(UPDATE)操作を公開せず、作成(INSERT)と削除(DELETE、保持期間超過分の一括削除のみ)のみを提供する(BR6.1)。個別レコードの選択的削除・改変を行うAPI・メソッドは実装しない。削除範囲は`olderThanDays`の入力検証(上記)により、意図しない大量削除(0以下・負の値による全件削除等)を事前に防ぐ。

## 機微情報の非出力

targetDescriptionはイベント発行元Unitが構成する人間可読な説明であり、本Unit自身はその内容を機微情報かどうか判定・フィルタしない(発行元Unit側の責務)。記録失敗時の構造化ログ(BR1.2)には、actionType・occurredAt・例外メッセージのみを含め、イベントpayload全体はログに出力しない。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T20:42:31Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ定義にaudit-log Unit向けの自動検証ツールの指定は見当たらず、目視でのクロスリファレンス検証のみを実施した。

- `logical-components.md`で定義された4コンポーネント(イベントリスナー・RESTコントローラ・サービス・リポジトリ)以外への依存参照は、performance/security/scalability/reliability/observability-design.mdのいずれにも存在しない(iteration 1で指摘された未定義「サービス」依存の問題は解消済みであることを再確認)。
- `traceability.json`のupstream_ids(NFR1.1〜NFR-SEC.1、NFR5)は、`nfr-requirements/traceability.json`のcoverage(NFR1〜NFR9の詳細化)と完全に対応しており、抜け漏れ・孤立参照はない。
- security-design.md・performance-design.md・scalability-design.md・reliability-design.md・observability-design.mdの各記述は、`functional-design/rules.md`のBR1.1〜BR6.1、`functional-design/functional-spec.md`のワークフロー1〜5と矛盾しない(BR5.1のisAdmin認可、BR6.1の記録不変性、BR4.1/BR4.2/BR4.3のretentionDays/olderThanDays下限などを個別に突き合わせ済み)。

### Summary

今回のレビュー依頼はdynamic-data-access Unitの表記フォーマット不具合修正に伴うnfr-designステージ全体のstate-level reject後の再確認であり、audit-log Unit自身の7ファイルの内容はiteration 2時点から変更がないことを確認した。上流のnfr-requirements・functional-designとの整合性、logical-components.mdで定義されたコンポーネント以外への参照がないこと、traceability.jsonの網羅性のいずれにも問題は見つからず、iteration 2と同様にREADY判定とする。
