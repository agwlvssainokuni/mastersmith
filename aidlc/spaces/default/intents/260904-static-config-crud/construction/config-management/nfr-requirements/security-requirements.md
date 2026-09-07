# Security Requirements: config-management

## NFR-AUTHZ.1: 管理者限定アクセス(GET /api/menuを除く)

config-managementの全操作(DB接続先設定・テーブル設定・メニュー構成・エクスポート/インポート・キャッシュクリア)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる(BR7.1)。isAdminクレームがfalseの場合は403を返す。

## NFR-AUTHZ.2: GET /api/menuの権限フィルタ(例外)

GET /api/menu(契約#22・#23)は非管理者を含む全利用者が呼び出せる、BR7.1のisAdmin必須の唯一の例外である。X-Active-Roleヘッダーがアクセストークンのrolesクレームに含まれない場合は403を返す。それ以外の場合、契約#22(config-management → permission)でX-Active-Roleに対応するroleIdが持つcanList=trueのtableId集合を取得し、テーブルノードはその集合に含まれるもののみを返す(BR4.3)。フォルダ/グループノードは配下に可視なテーブルノードが1件もない場合は結果から除外する。

## NFR-DATA.1: 業務DB接続情報の暗号化保持

DbConnection.credentialRef(業務DBへの接続に用いる認証情報)は暗号化して内部H2に保持する(entities.md、契約#19注記)。暗号鍵をソースコード・Gitリポジトリにコミットしないこと。環境変数、または実行ユーザーのみが読めるパーミッション(600)を設定したローカル設定ファイル(`.gitignore`登録)で保管する(project.md Forbidden)。

## NFR-DATA.2: エクスポート時の非復号

設定エクスポート(BR5.1)は、DbConnection.credentialRefを暗号化された値のまま出力し、復号は行わない。これにより、エクスポートされた設定ファイル自体が漏洩しても、認証情報の平文が直接露出することはない。

## NFR-DATA.3: ログへの機微情報非出力

credentialRefの実値(復号後の業務DB認証情報)は、アプリケーションログ・監査ログのいずれにも出力しない。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T13:56:27Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | tech-stack-decisions.md > 認証情報の暗号化 行 | 「鍵はapplication.yml経由、外部管理」という表現は、security-requirements.md NFR-DATA.1・project.md Forbidden(環境変数またはパーミッション600のローカル設定ファイルで保管し、ソースコード・Gitリポジトリにコミットしない)と矛盾はしないが、「application.yml経由」という語のみを読むと鍵の値そのものをapplication.ymlに直書きするように誤読され得る | tech-stack-decisions.mdの当該行に、「application.ymlのプロパティは環境変数参照であり、鍵の実値はコミットしない」旨を一言補足し、NFR-DATA.1との整合を明示する | New |

### Validation Tool Results

このステージにはツールによる自動検証は指定されていない(手動でのクロスリファレンス検証のみ実施)。rules.md(BR1.1〜BR8.1)、entities.md(DbConnection.credentialRef)、contract-summary.md(契約#1・#22・#23)、requirements.md(NFR1〜NFR3)との突き合わせを行い、数値・挙動レベルの不一致は検出されなかった。

### Summary

performance/security/scalability/reliability/observability/tech-stack-decisions/traceability.jsonの7ファイルは、rules.mdの該当BR(特にBR6.1のCaffeineキャッシュ、BR7.1/BR4.3のGET /api/menu例外、BR5.1/BR5.2のエクスポート・インポート、BR8.1の監査ログイベント語彙)と数値・挙動レベルで正確に一致しており、7ファイル間の矛盾も見当たらない。traceability.jsonのNFR4〜NFR9のN/A判定(特にNFR7をDbConnection.credentialRefの暗号化保持で代替具体化した点)も、根拠が明示されており妥当である。tech-stack-decisions.mdの鍵管理表現にMinorな曖昧さが1件あるが、READY判定を妨げるものではない。
