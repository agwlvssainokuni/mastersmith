# Frontend Components: frontend-admin

design-system-mapping.md・interaction-spec.mdに基づく、frontend-adminが使用するmake-you-chic-uiコンポーネントと、新規/確認中コンポーネントの一覧。

## 既存コンポーネント(再利用)

| コンポーネント | 用途 | 使用画面 |
|---|---|---|
| AppShell | 全画面共通のシェル(Topbar・サイドナビ・コンテンツ領域) | 全画面 |
| Table | ソート・ページング対応の一覧テーブル | ロール一覧(7a)、アカウント一覧(13a)、監査ログ(12、AuditLogTable経由) |
| TextField / Select | テキスト入力・セレクト | 各種フォーム、検索・フィルタ入力 |
| Textarea / Checkbox / Switch / RadioGroup | フォーム部品 | 設定管理(10)のマトリクス内セル、アカウント編集フォーム(13b) |
| ConfirmDialog | 確認ダイアログ | 設定インポート確認(9)、ロール/グループ削除確認(7a/7e)、監査ログ保持期間超過削除確認(12)、メニュー項目削除確認(14) |
| Tabs | タブ切り替え | ロール編集(7b)のテーブル権限/カラム権限タブ、設定管理(10)の各設定種別タブ(ConfigTabPanel内部) |
| Accordion | 展開可能な一覧 | グループ管理(7e)のメンバー一覧展開 |

## 新規/確認中コンポーネント(interaction-spec.mdより)

| コンポーネント | 用途 | 状態 |
|---|---|---|
| Modal | FK参照検索モーダル(FkReferencePicker)等の汎用モーダルコンテナ | 既存の`ConfirmDialog`が汎用`Modal`の上に構築されているか未確認 [assumption]。frontend-admin自身はFK参照検索を使用しない(frontend-core側の画面) |
| ConfigTabPanel | 設定管理画面(10)の設定種別タブ・マトリクス | interaction-spec.md定義済み。props: tableName, activeTab, onSave |
| SchemaImportPreview | スキーマ取り込みプレビュー(11) | interaction-spec.md定義済み |
| AuditLogTable | 監査ログ画面(12)の一覧・絞り込み | interaction-spec.md定義済み |
| AccountEditForm | アカウント編集フォーム(13b) | interaction-spec.md定義済み。業務データロール(チェックボックス群)と管理者権限(独立ラジオボタン)を別モデルとして扱う(refined-mockups.mdレビューR-01フォロー) |
| MatrixGrid | 設定管理タブ内・ロール編集(7b)のテーブル権限/カラム権限マトリクス | design-system-mapping.md候補。ロール編集画面7bで使用しているものと同一構造との想定 [assumption] |
| CheckableList | スキーマ取り込みプレビュー(11)のテーブル選択チェックボックスリスト | design-system-mapping.md候補 |
| **MenuTree**(新規、本Unitで新設) | メニュー管理画面(14)のツリー表示・編集 | フォルダ/グループノード(tableIdなし)・テーブルノードを`role="tree"`/`role="treeitem"`/`aria-expanded`で表示。ドラッグ&ドロップに加え、キーボード操作(上下ボタン)による並べ替え・親変更を必須の代替手段として提供する。既存コンポーネント一覧に相当するものが見当たらないため新規追加候補とする [assumption]。実際のコンポーネントAPIの確定はコード生成段階で行う |

## 未確認事項

design-system-mapping.mdに記載のとおり、`make-you-chic-ui`は未公開・プロトタイプ段階のリポジトリであり、実際のコンポーネントAPI(props名・型)は本ドキュメント作成時点で参照できていない。上記のマッピングは仮定であり、コード生成段階で実際のコンポーネントAPIと突き合わせて確定する [assumption]。
