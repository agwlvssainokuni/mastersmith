<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Security Requirements — config-engine (U1)

`inception/requirements-analysis/requirements.md`のNFR2、`project.md`のMandated/Forbidden、`construction/config-engine/functional-design/`の確定内容に基づく、config-engineユニットのセキュリティ要件。

## データ分類

| データ | 分類 | 理由 |
|---|---|---|
| TableConfig/ColumnConfig | Internal | 業務テーブル・カラムの表示設定であり、個人情報・機微情報を含まない（`requirements.md` Constraints「機微情報の非対象」） |
| TranslationEntry | Internal | i18nキーに対する言語別表示テキストのみで機微情報を含まない |
| 業務データ本体（行データ） | 対象外 | ConfigEngineは業務データ本体を保持・アクセスしない（業務データ用RDBMSへは一切アクセスしない） |

## STRIDE脅威分析

| 脅威 | 該当性 | 対策 |
|---|---|---|
| Spoofing（なりすまし） | 現行（内部Javaインタフェースのみ）は同一プロセス内呼び出しのため対象外。NFR2.2（下記）で扱う将来の管理用REST APIが対象 | NFR2.2参照 |
| Tampering（改ざん） | TableConfig/ColumnConfig/TranslationEntryへの不正な変更 | 管理画面からの変更操作は認可済み管理者のみ許可（NFR2.1）。変更はAuditLoggingへドメインイベントとして発行し追跡可能にする（`components.md`のConfigEngine→AuditLogging依存を踏襲） |
| Repudiation（否認） | 管理者による設定変更の実施有無に関する否認 | AuditLoggingへの変更イベント発行（操作者・変更前後の値を含む、FR8.1）により担保。config-engine自体は監査記録を保持しない（責務はaudit-logging、U7） |
| Information Disclosure（情報漏えい） | 設定データ自体は機微情報を含まないため低リスク。ただしConfigValidationExceptionのエラー詳細（フィールド名・DB構造の一部）が未認可者に露出するリスク | NFR2.3（fail-fastエラーのRFC 9457マッピングでフィールド単位のみ返却し、スタックトレースを含めない） |
| Denial of Service（サービス妨害） | ほぼ全ユニットから高頻度で呼び出される内部APIのため、単一障害点化のリスク | NFR1.2（起動時メモリキャッシュ）により内部設定DBへの都度アクセスを避け、可用性への影響を局所化する |
| Elevation of Privilege（権限昇格） | 将来の管理用REST API（W6等）を通じた未認可の設定変更 | NFR2.1（認可チェック必須）。`project.md` Forbidden「権限の昇格を、権限管理者による明示的な操作を経ずに許可しない」はPermissionEngine（U3）の責務であり、config-engineはPermissionEngineの判定結果に従う |

## NFR2.1: 設定変更操作の認可

```
NFR-AUTHZ-1: 設定変更操作の認可
Model: RBAC（PermissionEngine、C10契約に委譲）
Roles: 設定管理権限を持つロールのみ、TableConfig/ColumnConfig/TranslationEntryの変更操作を実行できる
Resource granularity: スキーマ・テーブル単位（PermissionEngineのcanAccessScreen等、既存の権限判定機構を用いる）
Delegation: 権限の委譲・昇格は権限管理者の明示的操作を経ずに許可しない（project.md Forbidden、config-engine自体はこの判定を行わずPermissionEngineへ委譲する）
Audit: すべての設定変更操作をAuditLoggingへイベント発行する（FR8.1）
```

- **根拠**: `functional-spec.md`のAssumptions & Open Questionsで指摘した、W6（i18n管理画面）およびTableConfig/ColumnConfigのフィールド単位編集を実現する新規REST APIは、既存の他ユニットREST契約（C1〜C8）と同様に、Bearer認証（authentication-serviceが発行するアクセストークン）とサーバー側での実効権限再検証（project.md Mandated「画面表示の出し分けだけに依存せず、必ずサーバー側で実効権限を再検証する」）を要求しなければならない。

## NFR2.2: 内部呼び出しの認証境界

```
NFR-AUTH-1: 内部呼び出しの認証境界
Method: 現行のConfigEngineApi（C9、Javaインタフェース）は同一プロセス内呼び出しのため、個別の認証機構を持たない（プロセス境界自体が信頼境界）
Token lifetime: N/A（内部呼び出し）
MFA requirement: N/A
将来のREST API: authentication-service（U5）が発行するアクセストークンによるBearer認証を要求する（contract-summary.mdの前提を踏襲）
```

## NFR2.3: エラー情報の安全な返却

```
NFR-DATA-1: fail-fast検証エラーの安全な返却
Classification: Internal（設定定義エラーの詳細情報）
要件: ConfigValidationExceptionは、エラーの生じたフィールド（schemaName/tableName/columnConfigId等）とルール種別を含む構造化エラー情報を保持し、開発者向けのスタックトレースは決して外部（利用者・API応答）に露出しない（`rules.md` BR1.11）
呼び出し元でのマッピング: config-import-export（C7契約）は本エラー情報をRFC 9457のerrors配列（field, message）へマッピングして返す
```

## Security Anti-Requirements（除外事項）

- 「ConfigEngineは安全でなければならない」のような測定不能な目標は設定しない。
- 依存関係の脆弱性スキャンは、`team.md`の確定事項により本プロジェクトでは意図的に対象外とする（config-engine固有の要件としても追加しない）。
- config-engineは業務データ本体（PII等の機微情報を含みうる）を一切保持・アクセスしないため、データ保護要件（暗号化等）はTableConfig/ColumnConfig/TranslationEntryの分類（Internal）に見合った標準的な内部設定DB保護（NFR2.4参照）にとどめ、過剰な暗号化要件は課さない。

## NFR2.4: 内部設定DBの保護

```
NFR-DATA-2: 内部設定DBのアクセス制御
Classification: Internal
Encryption at rest: 必須としない（Internal分類のデータであり、Confidential/Restrictedに該当するデータを含まないため）。ただし内部設定DB（H2）はアプリケーションプロセスからのみアクセス可能とし、外部ネットワークに直接公開しない
Encryption in transit: N/A（H2は組込みDBであり、アプリケーションと同一プロセス/ホスト内で完結する）
アクセス制御: アプリケーションプロセスのみが内部設定DBへ接続する。業務データ用RDBMSとは別接続とし、認証情報は環境変数またはシークレット管理の仕組みから読み込み、コード・ログに平文で残さない
```
