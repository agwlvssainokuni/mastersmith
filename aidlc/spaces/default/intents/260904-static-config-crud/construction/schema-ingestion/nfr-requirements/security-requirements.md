# Security Requirements: schema-ingestion

## NFR-DATA.1: 認証情報の取り扱い

業務DBへの接続に用いる認証情報(DbConnection.credentialRef)は、config-management側で暗号化して内部H2に保持され、本Unitは復号済みの値を呼び出し元(config-management)経由で受け取るのみで、自身では永続化しない(functional-design-questions.md Q4)。暗号鍵・認証情報をリポジトリにコミットしないこと(project.md Forbidden)を、本Unitが前提とする制約として明記する。

## NFR-DATA.2: エラー内容の制限

業務DBへの接続失敗・認証失敗は例外として呼び出し元へ伝播し、REST境界で400または500として応答する(rules.md BR6.1)。この際、認証情報そのもの(パスワード等)をエラーメッセージ・ログに含めない。

## NFR-AUTHZ.1: 管理者限定アクセス

schema-ingestionの全操作(スキーマ/データベース一覧取得・プレビュー実行)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる(functional-design BR5.1)。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T13:20:55Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | security-requirements.md > NFR-DATA.2 | 「業務DBへの接続失敗・認証失敗は例外として呼び出し元へ伝播し、REST境界で400または500として応答する(rules.md BR6.1)」と記載しているが、rules.md BR6.1は「接続確立に失敗したとき」に対して500エラーのみを規定しており、400には言及していない。同じBR6.1を引用するreliability-requirements.md(本Unit内の別文書)は「500として応答する」と正しく記載しており、両文書間で参照先ルールの記述に食い違いがある。 | NFR-DATA.2の記述を「500として応答する」に修正するか、400が発生しうる別の根拠(例: リクエスト自体の入力検証エラー)を明示した上でBR6.1以外の出典を追加する。 | New |

### Validation Tool Results

本ステージ定義に紐づく自動検証ツールの明示的な指定は確認されなかったため、上流文書(requirements.md NFR1〜NFR9、functional-design/entities.md、rules.md BR1.1〜BR6.1、functional-design-questions.md Q4、functional-spec.md)との突き合わせによる手動検証のみを実施した。

| Tool | Result | Interpretation |
|---|---|---|
| (自動検証ツールなし) | N/A | 上流文書との相互参照を手動で確認 |

### Summary

security-requirements.mdのNFR-DATA.1(認証情報の取り扱い)はfunctional-design-questions.md Q4およびfunctional-spec.md #1の記述と正確に一致しており、NFR-AUTHZ.1もBR5.1と整合する。traceability.jsonのNFR5(N/A判定、entities.mdの「永続エンティティを持たない」記述と整合)およびNFR6(OK判定、team.md Testing Postureのスキーマ読み込み層限定の前倒し特性テスト運用と整合)も妥当である。唯一、NFR-DATA.2がBR6.1の内容を「400または500」と過大に引用しており、同じ根拠を引くreliability-requirements.mdの記述(500のみ)と食い違う点をMinor指摘とした。他にBR1.1〜BR6.1との矛盾は確認されず、Critical/Majorに該当する指摘はないためREADYと判定する。
