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
**Date:** 2026-09-08T14:27:25Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ定義に検証ツールの指定はなく、成果物7ファイルおよび上流文書(nfr-requirements配下5ファイル、functional-design/rules.md・functional-spec.md)を手動で突き合わせた。

### Summary

audit-logのnfr-design成果物7ファイルは、以前のREADY判定時点から内容が変更されていないことを確認した。logical-components.mdで定義された4論理コンポーネント(イベントリスナー・RESTコントローラ・サービス・リポジトリ)以外への参照は存在せず、performance-design.md・scalability-design.md・reliability-design.md・observability-design.md・security-design.mdはいずれもnfr-requirements配下の対応するNFR(NFR1.1/1.2、NFR2.1/2.2、NFR3.1/3.2、NFR-RESILIENCE.1、NFR-AUTHZ.1、NFR-INTEGRITY.1、NFR-DATA.1)およびfunctional-design/rules.mdのBR1.1〜BR6.1・functional-spec.mdのワークフローと矛盾しない。traceability.jsonのNFR-SEC.1(N/A判定)もnfr-requirements時点の判定を正しく踏襲している。今回の独立検証でも新規の指摘事項はなく、前回と同一の結論(READY)に至った。
