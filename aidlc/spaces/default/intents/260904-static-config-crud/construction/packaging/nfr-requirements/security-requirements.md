# Security Requirements: packaging

## NFR-DATA.1: ビルド成果物への秘密情報の非同梱

単一WAR成果物には、以下の秘密情報を一切含めない(project.md Forbidden)。

- 内部H2データストアの接続情報
- パスワードハッシュのソルト/ペッパー
- アクセストークンの署名鍵(HMAC秘密鍵、またはRSA/EC鍵ペア)
- 業務DB接続情報(DbConnection.credentialRef)を暗号化するための暗号鍵

これらはビルド時にリポジトリ・成果物へ埋め込むのではなく、実行時に環境変数、または実行ユーザーのみが読めるパーミッション(600)を設定したローカル設定ファイル(`.gitignore`登録)経由で外部化する。

## NFR-DATA.2: リポジトリへの秘密情報の非コミット

ビルド設定(build.gradle等)・application.ymlのテンプレートにも、上記秘密情報の実値をコミットしない。application.ymlはプレースホルダ(環境変数参照)のみを含む形でリポジトリに含め、実値は実行環境側で注入する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T14:28:36Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | security-requirements.md > NFR-DATA.1 | 秘密情報リストの4項目目「業務DB接続情報(DbConnection.credentialRef)を暗号化するための暗号鍵」は、project.md Forbiddenの3項目(内部H2接続情報・パスワードハッシュのソルト/ペッパー・アクセストークン署名鍵)にも、requirements.md NFR1〜NFR9のいずれにも根拠がない。traceability.jsonのcoverageにも対応するエントリがなく、根拠のない要件の追加(過剰記載)となっている。 | 4項目目を削除するか、根拠となる一次情報(業務DB接続情報の暗号化方式を規定する上流文書)を追記し、traceability.jsonに対応するエントリを追加する。 | New |

### Validation Tool Results

本ステージ定義に自動検証ツールの指定はないため、実行していない。手動でのクロスリファレンス照合(requirements.md NFR1〜NFR9、project.md Mandated/Forbidden、3成果物間の記述)を行った。

### Summary

tech-stack-decisions.mdはrequirements.md NFR4・NFR8およびproject.md MandatedのJDBCドライバ内包方針と正確に一致しており、traceability.jsonのNFR1〜NFR9のstatus判定(他Unitへの責務分担を含む)も妥当である。唯一の懸念は、security-requirements.mdが根拠文書にない秘密情報項目を1件追加している点(R-01、Major)であり、Critical指摘はなく、Major指摘も1件のみのためREADYと判定する。
