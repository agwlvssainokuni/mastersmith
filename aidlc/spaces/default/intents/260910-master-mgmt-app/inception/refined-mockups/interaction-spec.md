# Interaction Specification — MasterSmith(マスタ管理アプリ)

`mockups.md` の各画面で使用するコンポーネント単位のインタラクション仕様と、代表的なユーザーフローを定義する。コンポーネント仕様は `.claude/knowledge/aidlc-design-agent/component-spec-template.md` の形式に従う。

## コンポーネント仕様

### ロール選択セレクタ

| Field | Value |
|---|---|
| Component | RoleSwitcher |
| Description | 複数ロールを持つユーザーが操作対象ロールを切り替えるヘッダー内セレクタ |
| Category | navigation |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 選択中ロール名を表示 | page load |
| open | ロール一覧をドロップダウン表示 | クリック / Enter |
| single-role-hidden | 単一ロールユーザーには非表示 | ユーザーのロール数が1件 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| roles | array | yes | — | ユーザーが保持するロール一覧 |
| selectedRole | string | yes | 最後に選択したロール | 現在選択中のロール |
| onChange | function | yes | — | ロール切替時のコールバック |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (対象外) | — |
| tablet (768–1023px) | ヘッダー右側に維持、ラベルを短縮表示可 |
| desktop (1024px+) | 既定レイアウト |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `combobox` |
| Keyboard interaction | Tab で focus、Enter/Space で展開、矢印キーで選択、Enter で確定 |
| Label / aria-label | 視覚ラベル「ロール」+ `aria-label="操作ロールの選択"` |
| Contrast ratio | 4.5:1 以上 |
| Screen reader | ロール切替時に `aria-live="polite"` で「選択中ロール: [ロール名]」を通知 |
| Focus management | 選択確定後、フォーカスはセレクタ自身に留まる |

#### Usage Example

```
<RoleSwitcher roles={user.roles} selectedRole={current} onChange={handleRoleChange} />
```

---

### テーマ・フォントサイズメニュー

| Field | Value |
|---|---|
| Component | DisplaySettingsMenu |
| Description | [User]アイコンから開く、テーマ(ライト/ダーク)・フォントサイズ(大/中/小)・言語(日本語/English)選択メニュー |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| closed | 非表示 | 初期状態 |
| open | メニュー展開 | [User]アイコンクリック |
| applying | 選択即時に全画面へ反映中(視覚的にはほぼ瞬間) | ラジオ選択 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| theme | `"light" \| "dark"` | yes | ユーザー設定値 | 現在のテーマ |
| fontSize | `"small" \| "medium" \| "large"` | yes | ユーザー設定値 | 現在のフォントサイズ |
| locale | `"ja" \| "en"` | yes | ユーザー設定値(既定: `ja`) | 現在の表示言語(FR10.1, FR10.2) |
| onChange | function | yes | — | 選択即時に呼び出されるコールバック |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | ドロップダウン幅を維持 |
| desktop (1024px+) | 既定レイアウト |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `menu` / 各選択肢は `menuitemradio` |
| Keyboard interaction | Tab で focus、矢印キーで選択肢間移動、Enter/Space で確定、Escape で閉じる |
| Label / aria-label | `aria-label="表示設定メニュー"` |
| Contrast ratio | 4.5:1 以上(ダークテーマでも同水準を維持) |
| Screen reader | 選択確定時に「テーマを[ダーク]に変更しました」を `aria-live="polite"` で通知 |
| Focus management | メニューを開いたら最初の選択肢にフォーカス、閉じたら[User]アイコンへフォーカスを戻す |

#### 挙動

選択肢をクリックした瞬間に即座に全画面(自身を含む)へ反映する。保存ボタンは存在しない。UIの反映と並行して非同期でサーバーへ設定を永続化するが、ユーザー操作としては選択のみで完結する(`refined-mockups-questions.md` Q9回答A)。永続化に失敗した場合は画面表示自体はロールバックせず、次回ログイン時に再度サーバーから設定を取得し直す(永続化失敗時の通知はトースト等で軽微に行う)。言語(日本語/English)もテーマ・フォントサイズと同じ挙動で即時反映する(レビュー指摘R-02への対応、FR10.1・FR10.2)。言語切替時は、表示文言の切替と同時に文書のルート要素(`html`)の`lang`属性を選択言語(`ja`または`en`)に更新し、スクリーンリーダー等の支援技術が実際の表示言語で読み上げられるようにする(レビュー指摘R-11への対応)。

---

### 詳細検索アコーディオン

| Field | Value |
|---|---|
| Component | AdvancedSearchAccordion |
| Description | 一覧画面の検索フォームで、主要条件に加えた追加条件を同一画面内に展開表示する |
| Category | input |

#### States

| State | Description | Trigger |
|---|---|---|
| collapsed | 主要条件のみ表示(既定) | page load |
| expanded | 追加条件を含めて表示 | 「詳細検索」クリック |
| loading | 該当なし(クライアント側の表示切替のみ) | — |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| expanded | boolean | no | false | 展開状態 |
| fields | array | yes | — | 詳細検索対象フィールド定義(設定駆動) |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | フィールドを2列組に折り返し |
| desktop (1024px+) | フィールドを3〜4列組で表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | トリガーは `button`、展開領域は `region` |
| Keyboard interaction | Enter/Space で開閉、Tab で展開領域内を順送り |
| Label / aria-label | トリガーに `aria-expanded` を付与し、展開状態を明示 |
| Contrast ratio | 4.5:1 以上 |
| Screen reader | `aria-controls` で展開領域と関連付け |
| Focus management | 展開時、フォーカスはトリガーに留まる(強制的な移動はしない) |

---

### 一括削除確認モーダル

| Field | Value |
|---|---|
| Component | BulkDeleteConfirmModal |
| Description | 一覧画面でチェックボックス選択した複数行を一括削除する前の確認ダイアログ |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 削除対象件数と警告文を表示 | 「選択項目を一括削除」クリック |
| loading | 削除実行中 | 「削除する」クリック |
| error | 削除失敗(権限喪失・DBエラー等) | 削除API失敗 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| selectedCount | number | yes | — | 削除対象件数 |
| onConfirm | function | yes | — | 削除実行コールバック |
| onCancel | function | yes | — | キャンセルコールバック |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | モーダル幅を画面幅の80%程度に縮小 |
| desktop (1024px+) | 固定幅(480px程度)で中央表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`、`aria-modal="true"` |
| Keyboard interaction | Escapeで閉じる、モーダル内でフォーカストラップ |
| Label / aria-label | `aria-labelledby` でモーダルタイトル「N件を削除しますか?」と関連付け |
| Contrast ratio | 4.5:1 以上、削除ボタンは危険操作として明確な色分け+ラベルで示す(色のみに依存しない) |
| Screen reader | エラー時は `aria-live="assertive"` でエラー内容を通知 |
| Focus management | 開いたら「キャンセル」にフォーカス(誤操作防止のため既定フォーカスは非破壊的操作側)、閉じたら操作前のトリガー要素へフォーカスを戻す |

---

### 楽観ロック競合エラーバナー

| Field | Value |
|---|---|
| Component | OptimisticLockConflictBanner |
| Description | 詳細・編集画面の保存時、更新日時/バージョン列を持つテーブルで競合を検出した場合に表示するインラインバナー |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| hidden | 既定(競合なし) | page load / 保存成功 |
| visible | 競合検出時に表示 | 保存APIが409相当のレスポンスを返却 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| message | string | yes | 固定文言 | 「このデータは他のユーザーによって更新されています。編集内容を確認し、必要であれば画面を再読み込みしてください。」 |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | 画面上部全幅で表示 |
| desktop (1024px+) | 画面上部全幅で表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `alert` |
| Keyboard interaction | 該当なし(操作ボタンを持たない、Q6回答A) |
| Label / aria-label | バナー自体が `role="alert"` によりスクリーンリーダーへ自動通知される |
| Contrast ratio | 4.5:1 以上、警告色+アイコンで表現(色のみに依存しない) |
| Screen reader | 表示と同時に自動読み上げ(`aria-live="assertive"` 相当) |
| Focus management | フォーカス移動は行わない(ユーザーの編集操作を妨げないため) |

#### 適用条件

このバナーは、対象テーブルに更新日時またはバージョン列が定義されている場合にのみ表示され得る。当該列が定義されていないテーブルでは楽観ロック自体を行わず、後勝ちで保存が成立するためこのバナーは表示されない(`refined-mockups-questions.md` Q6-follow-up回答B)。

---

### CSVインポートエラー一覧モーダル

| Field | Value |
|---|---|
| Component | CsvImportErrorModal |
| Description | 業務データCSVインポート実行時、行単位のバリデーションエラーを一覧表示するモーダル |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| success | エラーなし。成功件数を表示 | インポート成功 |
| partial-error | 一部行にエラーあり。成功件数とエラー行一覧を表示 | インポート部分失敗 |
| all-error | 全行エラー(ファイル形式不正等) | インポート全失敗 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| successCount | number | yes | 0 | 取込成功件数 |
| errorRows | array | no | [] | エラー行(行番号・フィールド・エラー内容) |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | エラー一覧を縦スクロール表示 |
| desktop (1024px+) | エラー一覧テーブルをそのまま表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog` |
| Keyboard interaction | Escapeで閉じる、フォーカストラップ |
| Label / aria-label | `aria-labelledby` で「インポート結果」と関連付け |
| Contrast ratio | 4.5:1 以上 |
| Screen reader | 開いた時点で成功/失敗件数のサマリーを読み上げ |
| Focus management | 開いたら「閉じる」ボタンにフォーカス |

---

### JSON設定インポート確認モーダル

| Field | Value |
|---|---|
| Component | ConfigImportConfirmModal |
| Description | 設定管理画面でJSON設定ファイルをインポートする前の上書き確認、および設定不正時のfail fastエラー表示 |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| confirm | 「現在の設定を上書きします」の警告と実行/キャンセル | ファイル選択後、インポート実行クリック |
| validating | 設定内容の検証中 | 実行クリック後 |
| invalid | 設定定義に誤り(必須プロパティ欠落等)があり、fail fastで実行を中止 | バリデーション失敗 |
| success | インポート完了 | バリデーション・反映成功 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| fileName | string | yes | — | 選択されたファイル名 |
| validationErrors | array | no | [] | 設定定義の検証エラー一覧 |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | モーダル幅を画面幅の80%程度に縮小 |
| desktop (1024px+) | 固定幅(560px程度)で中央表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`、`aria-modal="true"` |
| Keyboard interaction | Escapeで閉じる(validating中を除く)、フォーカストラップ |
| Label / aria-label | `aria-labelledby` でモーダルタイトルと関連付け |
| Contrast ratio | 4.5:1 以上 |
| Screen reader | invalid状態への遷移時、`aria-live="assertive"` でエラー件数を通知 |
| Focus management | invalid状態では最初のエラー項目へフォーカスを移動 |

#### fail fastの扱い

設定定義自体の誤り(必須プロパティ欠落等)は、開発者向けのスタックトレースを表示せず、`{行/キー: エラー内容}` の形式でユーザーが理解できる文言に変換して一覧表示する(FR1.3, `.claude/aidlc-common/`配下の共通方針および `aidlc/spaces/default/memory/project.md` のエラーハンドリング区別方針に整合)。

---

### ユーザ招待モーダル

| Field | Value |
|---|---|
| Component | InviteUserModal |
| Description | ユーザ管理画面から新規ユーザーを招待する際に、氏名・メールアドレス・付与ロールを入力するモーダル(FR2.1) |
| Category | input |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 入力フォームを表示 | 「+ ユーザーを招待」クリック |
| loading | 招待メール送信中 | 「招待する」クリック |
| error | 送信失敗(メールアドレス重複、SMTP接続エラー等) | 招待API失敗 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| onSubmit | function | yes | — | 招待実行コールバック(氏名・メールアドレス・ロールを渡す) |
| onCancel | function | yes | — | キャンセルコールバック |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | モーダル幅を画面幅の80%程度に縮小、フォーム項目を1カラム表示 |
| desktop (1024px+) | 固定幅(480px程度)で中央表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`、`aria-modal="true"` |
| Keyboard interaction | Escapeで閉じる、フォーム項目間はTabで移動、フォーカストラップ |
| Label / aria-label | 各入力欄に`<label>`、`aria-labelledby`でモーダルタイトル「ユーザーを招待」と関連付け |
| Contrast ratio | 4.5:1 以上 |
| Screen reader | 送信失敗時は`aria-live="assertive"`でエラー内容を通知 |
| Focus management | 開いたら氏名入力欄にフォーカス、閉じたら「+ ユーザーを招待」ボタンへフォーカスを戻す |

---

### ユーザー無効化確認モーダル

| Field | Value |
|---|---|
| Component | DisableUserConfirmModal |
| Description | ユーザー編集画面/ユーザー一覧画面から、ユーザーを無効化する前の確認ダイアログ(FR2.3) |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 対象ユーザー名と無効化の影響(次回以降ログイン不可)を表示 | 「無効化」クリック |
| loading | 無効化実行中 | 「無効化する」クリック |
| error | 無効化失敗(権限喪失・DBエラー等) | 無効化API失敗 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| userName | string | yes | — | 無効化対象のユーザー氏名 |
| onConfirm | function | yes | — | 無効化実行コールバック |
| onCancel | function | yes | — | キャンセルコールバック |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | モーダル幅を画面幅の80%程度に縮小 |
| desktop (1024px+) | 固定幅(420px程度)で中央表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`、`aria-modal="true"` |
| Keyboard interaction | Escapeで閉じる、フォーカストラップ |
| Label / aria-label | `aria-labelledby`でモーダルタイトル「[氏名]を無効化しますか?」と関連付け |
| Contrast ratio | 4.5:1 以上、無効化ボタンは危険操作として明確な色分け+ラベルで示す(色のみに依存しない) |
| Screen reader | エラー時は`aria-live="assertive"`でエラー内容を通知 |
| Focus management | 開いたら「キャンセル」にフォーカス(誤操作防止)、閉じたら操作前のトリガー要素へフォーカスを戻す |

---

### 監査ログ詳細比較モーダル

| Field | Value |
|---|---|
| Component | AuditLogDiffModal |
| Description | 監査ログ閲覧画面で「詳細」をクリックした際、変更前後の値を比較表示するモーダル(FR8.1) |
| Category | display |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 変更前後の値を項目ごとに並べて表示 | 「詳細」クリック |
| create-only | 作成操作の場合、変更前は「(なし)」と表示 | 操作種別が作成 |
| delete-only | 削除操作の場合、変更後は「(削除済み)」と表示 | 操作種別が削除 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| operationType | `"create" \| "update" \| "delete"` | yes | — | 操作種別 |
| beforeValues | object | no | null | 変更前の値(作成時はnull) |
| afterValues | object | no | null | 変更後の値(削除時はnull) |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| tablet (768–1023px) | 変更前/変更後を上下に積んで表示 |
| desktop (1024px+) | 変更前/変更後を左右2列で表示 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`、`aria-modal="true"` |
| Keyboard interaction | Escapeで閉じる、フォーカストラップ |
| Label / aria-label | `aria-labelledby`でモーダルタイトル「変更内容の詳細」と関連付け |
| Contrast ratio | 4.5:1 以上。変更箇所の強調は色のみに依存せず、太字やアイコンも併用する |
| Screen reader | 開いた時点で操作種別(作成/更新/削除)を読み上げる |
| Focus management | 開いたら「閉じる」ボタンにフォーカス、閉じたら「詳細」リンクへフォーカスを戻す |

---

## 主要ユーザーフロー

### Flow: 初回ログイン(招待メールから)

```
Flow: 初回ログイン
Persona: 招待メールを受け取った新規ユーザー
Trigger: 招待メール内のリンクをクリック
Steps:
  1. [招待メール] -> リンクをクリック -> [初期パスワード設定画面]へ遷移
  2. [初期パスワード設定画面] -> 氏名・パスワードを入力し「設定してログイン」を押す -> 設定完了 -> [トップ画面]へ自動ログイン遷移
Success outcome: パスワード設定が完了し、ログイン済み状態でトップ画面が表示される
Error paths:
  - リンクの有効期限切れ・使用済み -> 「このリンクは無効です。管理者に再招待を依頼してください」を表示し、フォーム自体を表示しない
  - パスワードが8文字未満 -> フォーカスアウト時にインラインエラー表示、再入力を促す
```

### Flow: アカウントロック中のログイン試行

```
Flow: アカウントロック中のログイン試行
Persona: 連続ログイン失敗によりロックされたユーザー
Trigger: ロック中にログインを試みる
Steps:
  1. [ログイン画面] -> メールアドレス/パスワードを入力しログイン -> 認証処理
  2. システムはロック状態を検出するが、通常の認証エラーと同一の汎用メッセージ「メールアドレスまたはパスワードが正しくありません」のみを表示する
Success outcome: 該当なし(ロック中は成功しない)
Error paths:
  - ロック中である旨・残り時間・残り試行回数は一切表示しない(セキュリティ上の配慮、`refined-mockups-questions.md` Q12回答C)
```

### Flow: 業務データの一括削除

```
Flow: 業務データの一括削除
Persona: DELETE権限を持つ業務担当者
Trigger: 一覧画面で複数行を選択し、まとめて削除したい
Steps:
  1. [一覧画面] -> 行のチェックボックスを2件以上選択 -> ツールバー下に「選択中: N件」と「選択項目を一括削除」が出現
  2. [一覧画面] -> 「選択項目を一括削除」を押す -> [一括削除確認モーダル]が開く
  3. [一括削除確認モーダル] -> 「削除する」を押す -> 削除実行 -> モーダルを閉じ、一覧を再取得して表示
Success outcome: 選択した行が一覧から削除され、監査ログに操作が記録される(FR8.1)
Error paths:
  - DELETE権限がない -> チェックボックス列自体が表示されない(Q4回答A)
  - 削除実行中にDBエラー -> モーダル内にエラーメッセージを表示し、モーダルを閉じずに再試行を促す
```

### Flow: 業務データCSVエクスポート/インポート

```
Flow: 業務データCSVインポート
Persona: CREATE権限を持つ業務担当者
Trigger: 大量の業務データをまとめて登録・更新したい
Steps:
  1. [一覧画面] -> ツールバーの「CSVインポート」を押す -> ファイル選択ダイアログが開く
  2. ファイルを選択 -> アップロード・検証処理が実行される
  3a. 全行成功 -> [CSVインポートエラー一覧モーダル](successのみ表示)-> 「閉じる」-> 一覧を再取得
  3b. 一部行エラー -> [CSVインポートエラー一覧モーダル]に成功件数とエラー行一覧を表示 -> ユーザーはエラー行を修正して再インポート
Success outcome: 業務データが一覧に反映され、監査ログに作成・更新が記録される
Error paths:
  - ファイル形式が不正(CSV以外等) -> 全行エラーとして「ファイル形式を確認してください」を表示
```

### Flow: 設定ファイル(JSON)のエクスポート/インポート

```
Flow: 設定ファイルのインポート
Persona: 業務メニュー設定の権限を持つ管理者
Trigger: 別環境で作成した設定を本環境に取り込みたい
Steps:
  1. [設定管理画面] -> 「ファイルを選択」でJSONファイルを選択 -> 「インポート実行」を押す
  2. [JSON設定インポート確認モーダル(confirm)] -> 現在の設定を上書きする旨を確認 -> 「実行する」を押す
  3a. 検証成功 -> 設定が反映され、成功メッセージを表示
  3b. 検証失敗(必須プロパティ欠落等) -> [JSON設定インポート確認モーダル(invalid)]にエラー内容を一覧表示し、インポートは実行しない(fail fast, FR1.3)
Success outcome: 新しい設定が反映され、業務メニュー設定画面に最新の内容が表示される
Error paths:
  - ファイルがJSON形式でない -> 「JSONファイルを選択してください」を表示し、実行しない
```

## Assumptions & Open Questions

None(詳細は `mockups.md` および `refined-mockups-questions.md` の Assumptions & Open Questions を参照)。
