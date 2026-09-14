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

# Security Design — schema-introspector (U2)

`nfr-requirements/security-requirements.md`(NFR2.1〜NFR2.3)に基づく、schema-introspectorユニットのセキュリティ設計。

## NFR2.1: 認証・認可アーキテクチャ

```
POST /api/config/schema-introspection
  → Spring Security フィルタチェーンでBearer JWTを検証(authentication-serviceが発行したトークン、本ユニット固有の検証ロジックは追加しない)
  → コントローラ層でactiveRoleIdを認証コンテキストから取得
  → PermissionEngineApi.canAccessScreen(activeRoleId, "config-import-export") を呼び出す
      IF false → 403 Forbidden(ProblemDetails)を返し、以降の処理を行わない
      ELSE → 後続処理(RDBMS方言判定等)へ進む
```

- 認可判定はコントローラ層(REST境界)の入口で行い、以降のサービス層はこの判定を再検証しない(1リクエスト内で一度だけ判定する設計。境界を越えるたびに再検証するpermission-engine自身の設計とは異なり、本ユニットは単一の内部処理フローのため冗長な再検証は不要)。
- 画面表示側(frontend-ui)のボタン非表示等はあくまで利便性のためのUI制御であり、サーバー側の本判定に置き換わるものではない(`project.md` Mandated)。

## NFR2.2: 接続情報・シークレットの非露出

- 業務データ用RDBMSへの接続情報(URL・ユーザー名・パスワード)は、Spring Bootの外部化設定(`application.yml`のプレースホルダ + 環境変数、または将来的なシークレットマネージャ連携)から取得し、ソースコードに直接記述しない。
- ログ出力実装(observability-design.md参照)では、接続情報・パスワードに該当するフィールドを構造化ログの出力対象から明示的に除外する(ログ用DTOに接続情報フィールドを含めない設計とすることで、実装ミスによる混入を防ぐ)。
- 422エラーレスポンス(BR2.9)には、接続失敗の一般的な理由(例:「対象データベースに接続できません」)のみを含め、接続文字列やドライバの詳細な例外メッセージ(スタックトレース含む)はレスポンスボディに含めない(サーバー側ログにのみ記録する)。

## NFR2.3: CIセキュリティゲート

本ユニット固有の追加設計はない。`ci-pipeline`ステージで定義される全ユニット共通のSAST・シークレットスキャンがそのまま適用される。

## 脅威対応設計(security-requirements.mdのSTRIDE分析への対応)

| 脅威 | 設計上の対応 |
|---|---|
| Spoofing | Spring SecurityのJWTフィルタで検証、本ユニットは認証ロジックを持たない |
| Tampering | HTTPS終端(インフラ層)、リクエストボディのバリデーション(schemaName/tableNamesの型・形式チェックをコントローラ層のBean Validationで実施) |
| Repudiation | observability-design.mdの構造化ログに実行結果を記録(監査ログではなく運用ログとしての記録に留まる制約は既知、security-requirements.md参照) |
| Information Disclosure | NFR2.1の認可判定、NFR2.2の接続情報非露出設計 |
| Denial of Service | NFR1.1の30秒応答時間目標、同時実行を想定しない設計(performance-design.md参照) |
| Elevation of Privilege | NFR2.1のcanAccessScreen判定、権限昇格防止自体はPermissionEngine側の責務 |
