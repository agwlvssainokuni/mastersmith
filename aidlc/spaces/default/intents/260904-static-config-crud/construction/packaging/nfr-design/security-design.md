# Security Design: packaging

## ビルド成果物への秘密情報の非同梱

Gradleビルド設定(build.gradle)は、秘密情報(内部H2接続情報、パスワードハッシュのソルト/ペッパー、アクセストークンの署名鍵)の実値を一切参照しない。これらはビルド時ではなく実行時に、環境変数または実行ユーザーのみが読めるパーミッション(600)を設定したローカル設定ファイル経由でSpring Bootアプリケーションへ注入される(NFR-DATA.1)。

業務DB接続情報の暗号化(DbConnection.credentialRef)自体はconfig-management Unit固有の関心事であり、その暗号鍵の管理方式はconfig-management Unitのnfr-requirements/security-requirements.md(NFR-DATA.1)側で扱う。packaging Unit自身のnfr-requirements/security-requirements.mdが同鍵をこのUnitの秘密情報リストに含めていた点は、project.md Forbidden・requirements.md NFR4/NFR8のいずれにも根拠がない過剰記載であるとレビューで指摘済み(Status: New、未解消)であり、本設計では踏襲しない。

## リポジトリへの秘密情報の非コミット

application.ymlは`${ENV_VAR_NAME}`形式のプレースホルダのみを含み、環境変数が未設定の場合のデフォルト値としても秘密情報の実値を記述しない。ビルドスクリプト(build.gradle)・CI設定ファイルのいずれにも秘密情報の実値をハードコードしない(NFR-DATA.2)。実行環境固有の設定(接続先ポート、ログレベル等)はapplication.ymlのプロファイル機構(application-{profile}.yml)で分離し、秘密情報とは明確に区別する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T23:02:05Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ・本Unitに自動検証ツールの指定はないため、実行していない。以下の手動クロスリファレンス照合を行った。

- iteration 2で修正されたR-01(過剰記載の削除)・R-02(traceabilityでの反映): security-design.mdの秘密情報リストは「内部H2接続情報、パスワードハッシュのソルト/ペッパー、アクセストークンの署名鍵」の3項目のみであり、上流packaging/nfr-requirements/security-requirements.mdの4項目目(業務DB接続情報の暗号鍵)は明示的に踏襲対象外とされている。traceability.jsonのcoverageもNFR-DATA.1のtargetにこの除外理由を明記しており、両修正は維持されている。
- project.md Forbiddenの3項目(内部H2接続情報・パスワードハッシュのソルト/ペッパー・アクセストークン署名鍵)と、security-design.md本文の秘密情報リストは完全に一致する。
- requirements.md NFR4(単一WARへのパッケージング)・NFR8(HTTPS終端はリバースプロキシ前提、WAR本体の要件としない)のいずれも、本設計の秘密情報非同梱・非コミット方針と矛盾しない。security-design.mdが「project.md Forbidden・requirements.md NFR4/NFR8のいずれにも根拠がない」と述べる4項目目除外の判断も、両文書を直接確認した結果として妥当である。
- config-management UnitのNFR-DATA.1(業務DB接続情報の暗号化保持、暗号鍵はソースコード・Gitリポジトリにコミットせず環境変数またはパーミッション600のローカル設定ファイルで保管)を実際に確認したところ、security-design.mdの「暗号鍵の管理方式はconfig-management Unit側で扱う」という参照は記載内容と正確に一致する。
- packaging/nfr-requirements/security-requirements.mdの`## Review`セクションのR-01は、Status: Newのまま未解消であることを確認した(security-design.md本文の記述と一致)。
- traceability.jsonのupstream_ids(NFR-DATA.1、NFR-DATA.2)はpackaging Unit自身のnfr-requirements/security-requirements.mdの見出しと一致し、他Unit所有の要件IDの誤混入はない。

### Summary

前回iteration 2で修正されたR-01・R-02は今回のファイルでも維持されており、project.md Forbidden・requirements.md NFR4/NFR8との整合、config-management UnitのNFR-DATA.1への参照も正確であることを確認した。内容変更なしの再確認であり、指摘事項はない。
