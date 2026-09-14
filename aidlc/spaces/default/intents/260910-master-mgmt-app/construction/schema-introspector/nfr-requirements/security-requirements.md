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

# Security Requirements — schema-introspector (U2)

`inception/requirements-analysis/requirements.md`のNFR2、`functional-design/rules.md`のBR2.8、および`project.md` Mandatedに基づく、schema-introspectorユニットのセキュリティ要件。

## NFR2.1: 認証・認可

- **Method**: `POST /api/config/schema-introspection`(C8)はBearer JWT認証を必須とする(authentication-service, U5が発行するアクセストークンをそのまま検証する。本ユニット固有の認証方式は追加しない)。
- **Authorization**: 呼び出し元のアクティブロール(activeRoleId)に対し`PermissionEngine.canAccessScreen(activeRoleId, "config-import-export")`を呼び出し、設定管理画面へのアクセス権限をサーバー側で再検証する(BR2.8)。画面表示の出し分けだけに依存しない(`project.md` Mandated)。
- **Audit**: 認可判定の結果自体(許可/拒否)はobservability-requirements.mdの構造化ログに記録する。判定拒否時は403 Forbidden(C8)を返す。

## NFR2.2: 接続情報・シークレットの非露出

- 対象RDBMS(業務データ用)への接続情報(接続文字列・認証情報)は環境変数またはシークレット管理の仕組みで保持し、ソースコード・ログ・エラーメッセージに平文で出力しない(Construction Phase Guardrails「Never hardcode credentials, API keys, or secrets」)。
- メタデータ読み取り時に接続エラーが発生した場合(BR2.9)、422のエラーレスポンス・ログのいずれにも接続文字列やパスワードそのものは含めない。

## NFR2.3: CIセキュリティゲート

- `team.md`確定事項(SAST・シークレットスキャンをマージ前のCIブロッキングチェックとして導入)は全ユニット共通であり、本ユニット固有の追加ツール選定は行わない。具体的なCI構成は`ci-pipeline`ステージで扱う。
- 依存関係の脆弱性スキャンは`team.md`確定事項により今回は対象外とする。

## 脅威considerations(STRIDE、簡易)

| カテゴリ | 脅威 | 対策 |
|---|---|---|
| Spoofing | JWTなしでの呼び出し・偽装トークン | Bearer JWT必須(NFR2.1)。authentication-serviceのトークン検証に委譲 |
| Tampering | リクエストボディ(schemaName/tableNames)の改ざん | TLS必須(HTTPS)。リクエストボディは対象スキーマ名・テーブル名一覧のみで、業務データの値そのものは含まない |
| Repudiation | 誰が実行したか否認される | observability-requirements.mdの構造化ログに実行結果を記録するが、AuditLogging(改ざん不可なappend-onlyログ)の直接の記録対象ではない。config-engine側が`writeTableConfigDraft`呼び出し時にConfigChangedEvent(actor="system")を発行する(`construction/config-engine/functional-design/rules.md` BR1.13)ため、生成された設定変更自体の監査記録はconfig-engine側で担保されるが、実行者IDがactorへ伝搬されない既知の制約はconfig-engine側のAssumptions & Open Questionsに記録済みで、本ユニット側で新たに解決すべき事項ではない |
| Information Disclosure | スキーマ構造(テーブル/カラム名)の外部漏洩 | NFR2.1の認可判定により管理者ロールに限定。本ユニットは業務データの値そのものは読み取らない(BR2.1、メタデータのみ) |
| Denial of Service | 大規模スキーマや繰り返し呼び出しによる過負荷 | NFR1.1の30秒応答時間目標、Q2で対象規模を数十テーブル程度と明示。同時実行は想定しない(低頻度の管理操作) |
| Elevation of Privilege | 権限のない利用者が設定変更を実行 | BR2.8のサーバー側実効権限再検証。権限昇格防止は`project.md` Forbidden(権限管理者の明示的操作を経ない昇格禁止)がPermissionEngine側で担保する |

## Compliance

- 本プロジェクトに適用される法規制・業界標準の指定は`project.md`に記載がなく、該当なし。
