# Design System Mapping — MasterSmith(マスタ管理アプリ)

社内デザインシステム make-you-chic-ui の詳細なコンポーネント一覧は本ステージ時点で未確認のため、`refined-mockups-questions.md` Q3の回答(B)に基づき、既存コンポーネントを最大限再利用しつつ本アプリ固有のUIは新規コンポーネントとして追加する方針を採る。想定コンポーネント名は汎用的な名称で記載し、実装フェーズ(機能設計・コード生成)で make-you-chic-ui の実際のコンポーネント名へ読み替える前提とする。

## マッピング方針

- **既存コンポーネントを再利用する対象**: 汎用的なUI部品(ボタン、テキスト入力、select、テーブル、モーダル、タブ、ページネーション等)
- **新規コンポーネントとして追加する対象**: 本アプリ固有の複合UI(業務メニューカード、ロール選択セレクタ、テーマ・フォントサイズメニュー、詳細検索アコーディオン等)。これらは既存の基本部品(ボタン・ドロップダウン等)を内部で組み合わせて実装する

## マッピング表

| UI要素(画面) | 想定コンポーネント | 区分 | 備考 |
|---|---|---|---|
| ログインフォーム(ログイン画面) | `Form`, `TextField`, `PasswordField`, `Button(primary)` | 既存再利用 | 標準フォーム部品を組み合わせる |
| 業務メニューカード(トップ画面) | `MenuCard`(新規) | 新規 | 既存`Card`をベースに、アイコン+ラベル+クリック導線を組み合わせた新規複合コンポーネント |
| ロール選択セレクタ(ヘッダー) | `RoleSwitcher`(新規) | 新規 | 既存`Dropdown`/`Select`をベースにした新規コンポーネント(`interaction-spec.md`参照) |
| テーマ・フォントサイズメニュー([User]メニュー) | `DisplaySettingsMenu`(新規) | 新規 | 既存`Menu`+`RadioGroup`を組み合わせた新規コンポーネント |
| サイドバーナビゲーション | `SideNav`, `TreeItem` | 既存再利用 | 既存のツリー型ナビゲーションコンポーネントを想定。N階層ネストと`aria-current`対応が必要 |
| 検索フォーム(主要条件) | `Form`, `TextField`, `Select`, `Button(secondary)` | 既存再利用 | |
| 詳細検索アコーディオン | `AdvancedSearchAccordion`(新規) | 新規 | 既存`Accordion`をベースにした新規コンポーネント(`interaction-spec.md`参照) |
| データテーブル(一覧画面) | `DataTable`, `Checkbox`, `SortableColumnHeader` | 既存再利用 | ソート・行選択チェックボックス対応の既存テーブルコンポーネントを想定 |
| ページネーション | `Pagination`, `PageSizeSelector` | 既存再利用 | |
| 一括削除確認モーダル | `ConfirmModal`(既存)をベースにした`BulkDeleteConfirmModal`(新規) | 一部新規 | 既存の汎用確認モーダルに削除件数表示を追加 |
| CSVインポートエラー一覧モーダル | `Modal`, `DataTable`(既存)をベースにした`CsvImportErrorModal`(新規) | 一部新規 | |
| 詳細・編集フォーム部品(1行テキスト等) | `TextField`, `TextArea`, `NumberField`, `DecimalField`, `DatePicker`, `DateTimePicker`, `Select`, `RadioGroup`, `Switch`, `Checkbox` | 既存再利用 | FR1.1の設定駆動フォーム部品一式に対応する既存部品群を想定 |
| 楽観ロック競合エラーバナー | `InlineAlert(role="alert")` | 既存再利用 | 既存の警告バナーコンポーネントを想定 |
| 保存/キャンセルボタン | `Button(primary)`, `Button(secondary)` | 既存再利用 | |
| ユーザ招待モーダル | `Modal`, `Form`(既存)をベースにした`InviteUserModal`(新規) | 一部新規 | `interaction-spec.md`参照 |
| ユーザー無効化確認モーダル | `ConfirmModal`(既存)をベースにした`DisableUserConfirmModal`(新規) | 一部新規 | `interaction-spec.md`参照(レビュー指摘R-07への対応) |
| 監査ログ詳細比較モーダル | `Modal`(既存)をベースにした`AuditLogDiffModal`(新規) | 一部新規 | make-you-chic-uiに差分表示部品がないため新規コンポーネントとした。`interaction-spec.md`参照(レビュー指摘R-06への対応) |
| 業務メニュー設定タブ | `Tabs`, `TabPanel` | 既存再利用 | |
| JSON設定インポート確認モーダル | `Modal`, `FileInput`(既存)をベースにした`ConfigImportConfirmModal`(新規) | 一部新規 | |

## トークン・スタイル方針

- 配色・タイポグラフィ・スペーシング(4px, 8px, 16px, 24px, 32px, 48pxのスケール)は make-you-chic-ui のデザイントークンをそのまま採用する。本アプリ独自のカラーパレット追加は行わない
- ダークテーマは make-you-chic-ui が提供するダークテーマトークンをそのまま利用する(ライト/ダーク切替はテーマトークンの切替のみで実現し、コンポーネント側の分岐は持たない)
- フォントサイズ(大/中/小)は、make-you-chic-ui のタイポグラフィスケールのうち3段階を採用する。具体的なpx/rem値は実装フェーズで確定する

## Assumptions & Open Questions

- make-you-chic-ui の実際のコンポーネント一覧・命名規則は本ステージ時点で未確認のため、上表のコンポーネント名は暫定的な想定名である。機能設計・コード生成フェーズで実際のライブラリ内容と突き合わせて読み替える必要がある(`refined-mockups-questions.md` Q3回答Bのトレードオフとして`memory.md`に記録済み)。
