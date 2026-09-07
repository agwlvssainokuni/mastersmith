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
**Date:** 2026-09-07T14:41:28Z
**Iteration:** 2

### Findings

(指摘なし)

前回iteration(1)のCritical指摘R-01「RESTコントローラの依存列に未定義の『サービス』が記載されており、BR2.1・BR2.2・BR3.1・BR4.1の実装帰属先が宙に浮いている」について、`logical-components.md`のコンポーネント表に「サービス」コンポーネントが追加され、以下のとおり解消を確認した。

- サービスの責務欄に、BR2.1・BR2.2(絞り込み条件とtargetDescription部分一致のAND組み立て)、BR3.1(CSV/JSON形式のエクスポート生成)、BR4.1(`olderThanDays`からの削除基準日時算出と一括削除実行)、BR4.2(retentionDays設定更新)が明記されており、rules.mdの記載と一致する。RESTコントローラ→サービス→リポジトリという依存列も宙に浮いた参照なく解決している。
- BR4.3(olderThanDaysの1以上整数制約)はサービスの責務欄には明記されないが、security-design.md「入力検証」節で「コントローラのDTOレベルで検証し、不正な値がサービス層・リポジトリ層へ到達しない設計とする」と記載されており、コントローラ層での検証という帰属先も明確であり矛盾はない。
- security-design.mdが前提とする「認可はコントローラ層の入口で完結させ、サービス層以降には認可判定を持ち込まない」という境界は、logical-components.mdの新しいサービス定義(検索条件組み立て・エクスポート生成・削除基準算出・設定更新のみを責務とし、認可判定を含まない)と整合している。
- reliability-design.mdのコード例(イベントリスナーが`auditLogRepository.save(...)`を直接呼び出す)は、logical-components.mdの「イベントリスナー→リポジトリ」の依存(サービスを経由しない)と一致しており、BR1.1・BR1.2の記録受付処理には新設のサービスコンポーネントが関与しない設計であることも整合している。
- performance-design.md(BR2.1・BR2.2のインデックス設計・ページネーション)、scalability-design.md(単一インスタンス・BR4.1の一括削除による増加抑制)、observability-design.md(BR1.2の構造化ログ)は、いずれも新設のサービスコンポーネントの責務範囲と矛盾しない。
- traceability.jsonのカバレッジ記述(NFR-AUTHZ.1〜NFR-DATA.1)もsecurity-design.mdの各節と一致しており、新設コンポーネントによる不整合は生じていない。
- functional-design/rules.md(BR1.1〜BR6.1)との突き合わせでも、全ビジネスルールの実装帰属先(イベントリスナー/RESTコントローラ/サービス/リポジトリのいずれか)が明確になっており、宙に浮いたルールは残っていない。

### Validation Tool Results

本ステージに指定された自動検証ツールはない(スキル・ステージ定義に検証ツールの記載なし)。手作業でのクロスリファレンス確認(7ファイル間、およびfunctional-design/rules.mdとの突き合わせ)を実施した。

### Summary

iteration 1のCritical指摘(R-01)は、logical-components.mdへの「サービス」コンポーネント追加により実質的に解消されている。BR2.1・BR2.2・BR3.1・BR4.1・BR4.2の実装帰属先はサービスに明確に定まり、BR4.3はコントローラ層での入力検証として帰属先が明確である。security-design.mdが前提とする「サービス層に認可判定を持ち込まない」境界も新しいサービス定義と整合しており、他の6ファイル・functional-design(rules.md)との間に新たな矛盾は見つからなかった。Critical・Majorの指摘はなく、READY と判定する。
