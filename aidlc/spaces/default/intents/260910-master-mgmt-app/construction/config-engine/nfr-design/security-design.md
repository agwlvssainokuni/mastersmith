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

# Security Design — config-engine (U1)

`construction/config-engine/nfr-requirements/security-requirements.md`（NFR2.1〜NFR2.4）を実現する具体設計。

## 認可アーキテクチャ

- config-engineは認可判定ロジックを自ら実装せず、`PermissionEngineApi`（C10契約）へ委譲する（NFR2.1）。
- 管理画面からの設定変更操作（`ConfigModelStore`が公開する更新系メソッド、将来のREST APIの裏側）は、呼び出し前に`PermissionEngineApi.canAccessScreen`または`resolveEffectivePermission`による判定結果を要求する前提でメソッドシグネチャを設計する（判定結果を引数として受け取るのではなく、呼び出し元が判定済みであることを保証する契約とする。config-engine自身が二重に判定を行わない）。

## 入力検証・データ保護設計

- **バリデーション層**: `ConfigValidator`（Q2で論理分割を確定）が、Jakarta Bean Validationアノテーション + カスタム`ConstraintValidator`（`tech-stack-decisions.md`確定）を用いてBR1.1〜BR1.4のfail-fast検証を実行する。
- **SQLインジェクション対策**: config-engine自身は業務データRDBMSへSQLを発行しない（メタデータの型正規化のみ、`rules.md` BR1.12）。内部設定DB（H2）へのアクセスはSpring Data JPA/JDBCのパラメータバインディングのみを用い、文字列結合によるクエリ構築は行わない。
- **エラー情報の安全な返却**: `ConfigValidator`が送出する`ConfigValidationException`は、フィールド名・ルール種別のみを含む構造化データとし、スタックトレースや内部実装詳細（テーブル名・SQL文等）を含まない（NFR2.3）。呼び出し元（config-import-export、C7契約）がRFC 9457形式へマッピングする。

## 暗号化・シークレット管理

- 内部設定DB（H2）の接続情報は、環境変数または`application.yml`の外部化設定（Spring Bootのプロファイル機構）から読み込み、コード・ログに平文で残さない（`phases/construction.md` Security）。
- TableConfig/ColumnConfig/TranslationEntryはInternal分類（NFR2.4）のため、保存時暗号化は必須としない。ただし内部設定DBファイル自体はアプリケーションプロセスのみがアクセス可能なファイルシステムパーミッションで保護する。

## セキュリティヘッダー・CSRF/XSS対策

- config-engineはHTTPレスポンスを直接返さない（内部呼び出しのみ）ため、セキュリティヘッダー・CSRF/XSS対策は本ユニットの設計対象外とする。frontend-ui（U12）・各REST公開ユニット（list-engine等）の責務とする。将来のconfig-engine自身のREST API（W6等）が追加される場合は、Contract Design追補時にこれらの対策を含める。

## 監査ログ連携

- TableConfig/ColumnConfig/TranslationEntryの変更操作は、`ConfigModelStore`がAuditLoggingへドメインイベント（`ConfigChanged`等）を発行する（`components.md`のConfigEngine→AuditLogging依存を踏襲）。イベントには操作者・操作対象・変更前後の値を含める（FR8.1）。
