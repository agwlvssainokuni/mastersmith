# Security Design: notification

## トークンのログ非出力

メール本文に埋め込むトークン(registrationToken・resetToken・changeToken)はURLの一部として送信するが、SMTP送信失敗時のログにはサービスコンポーネントがEmailDispatchの内容(宛先・templateId)のみを記録し、これらの機微情報は含めない(NFR-DATA.1)。

## メールテンプレートのHTMLエスケープ

サービスコンポーネントがMustacheテンプレート(java-mustache-processor)を描画する際、テンプレート変数(利用者名、AccountInfoChangedEventのchangedFields等)はHTMLエスケープされた状態で埋め込み、メール本文へのHTML/スクリプト注入を防ぐ(NFR-INJECTION.1)。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T14:02:23Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ定義に紐付く自動検証ツールの指定は確認できなかったため、nfr-design 7ファイルとアップストリーム文書(nfr-requirements/7ファイル、functional-design/rules.md、functional-design/entities.md)との突き合わせによる手動検証を実施した。

| Tool | Result | Interpretation |
|---|---|---|
| (該当なし) | — | 本ステージにvalidationツールの指定なし。手動クロスチェックで代替。 |

### Summary

logical-components.mdが定義する2コンポーネント(イベントリスナー・サービス)以外への参照は7ファイル中に存在せず、リポジトリコンポーネントへの言及もない。traceability.jsonが列挙する7件のNFR ID(NFR1.1、NFR1.2、NFR-DATA.1、NFR-INJECTION.1、NFR2.1、NFR-FAILSAFE.1、NFR3.1)はいずれもnfr-requirements配下の対応ファイルに実在し、各design側の記述内容も要件文と矛盾しない。BR1.1〜BR3.1・EmailDispatch非永続化などfunctional-design(rules.md、entities.md)の記述との整合性にも齟齬はない。前回iteration 1のREADY判定時点から内容は変更されておらず、独立した再検証でも同一の結論(READY)に至った。

