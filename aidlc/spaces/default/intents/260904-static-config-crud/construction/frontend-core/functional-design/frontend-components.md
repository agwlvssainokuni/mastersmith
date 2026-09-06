# Frontend Components: frontend-core

design-system-mapping.md・interaction-spec.mdに基づく、frontend-coreが使用するmake-you-chic-uiコンポーネントと、確認中コンポーネントの一覧。

## 既存コンポーネント(再利用)

| コンポーネント | 用途 | 使用画面 |
|---|---|---|
| AppShell | 全画面共通のシェル(Topbar・サイドナビ・コンテンツ領域) | 全画面 |
| Table | ソート・ページング対応の一覧テーブル | 一覧画面(3)、FK参照ポップアップ検索(6)の検索結果 |
| TextField / Select | テキスト入力・セレクト | ログイン(1)、検索・フィルタ(3)、編集画面(5)、自己サービス画面群(7) |
| Textarea / Checkbox / RadioGroup | フォーム部品 | 編集画面(5) |

## 確認中コンポーネント(interaction-spec.mdより)

| コンポーネント | 用途 | 状態 |
|---|---|---|
| FkReferencePicker | FK参照検索モーダル(6) | interaction-spec.md定義済み。汎用`Modal`コンテナの上に構築されているか未確認 [assumption](design-system-mapping.md参照) |
| LoginLockoutNotice | ログイン試行回数制限のロック中表示(1) | interaction-spec.md定義済み |
| Modal | FkReferencePickerの基盤コンテナ候補 | design-system-mapping.md候補、未確認 [assumption] |

## 未確認事項

design-system-mapping.mdに記載のとおり、`make-you-chic-ui`は未公開・プロトタイプ段階のリポジトリであり、実際のコンポーネントAPI(props名・型)は本ドキュメント作成時点で参照できていない。自己サービス画面群(パスワード忘れ・登録完了・情報変更・メールアドレス変更確認)は専用コンポーネントの定義がなく、編集画面(5)と同じ1カラムフォームレイアウト(TextField・Buttonの組み合わせ)を踏襲する汎用フォームとして実装する想定 [assumption]。上記のマッピングは仮定であり、コード生成段階で実際のコンポーネントAPIと突き合わせて確定する。
