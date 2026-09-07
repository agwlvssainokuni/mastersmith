# Logical Components: frontend-admin

## コンポーネント構成

frontend-adminは以下3つの論理コンポーネントで構成する。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| 画面コンポーネント | ロール一覧・編集・割り当て、グループ管理、監査ログ、アカウント管理、設定管理、スキーマ取り込み、エクスポート/インポート、メニュー管理の各ページ | 共通UIコンポーネント、APIクライアント |
| 共通UIコンポーネント | make-you-chic-uiベースの共通部品(ConfirmDialog、ConfigTabPanel、AuditLogTable、SchemaImportPreview、MenuTree等) | (なし) |
| APIクライアント | バックエンドREST呼び出しの共通化(fetchラッパー)、403応答時の共通エラー表示への切り替え | (バックエンドREST API) |

## 障害ドメインとブラストラディウス

frontend-admin自身の障害(レンダリングエラー等)はUI表示のみに影響し、バックエンドの状態には影響しない。APIクライアントがバックエンドから403・404・5xx等のエラー応答を受けた場合、画面コンポーネントはエラー状態に切り替わるのみで、他画面には影響しない。

## 共有リソース

frontend-admin自身は永続化状態を持たない。認証状態(アクセストークン等)はfrontend-core(ログイン画面)側で管理され、frontend-admin側はそれをAPIクライアント経由で参照するのみである。
