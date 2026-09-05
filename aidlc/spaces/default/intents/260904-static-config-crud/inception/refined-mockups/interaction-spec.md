# Interaction Specification: MasterSmith MVP

mockups.md で追加・変更された画面の主要コンポーネントを、`.claude/knowledge/aidlc-design-agent/component-spec-template.md` の形式で具体化する。既存画面(wireframes.md 1.〜8.)のコンポーネントは変更がないため本ドキュメントには含めない。

---

## FK参照検索モーダル(FkReferencePicker)

| Field | Value |
|---|---|
| Component | FkReferencePicker |
| Description | 編集画面のFK入力項目から開く、参照先レコードの検索・選択モーダル |
| Category | feedback / navigation(モーダルダイアログ) |

### States

| State | Description | Trigger |
|---|---|---|
| default | 検索欄が空、検索結果は開いた時点の初期一覧(例: 直近作成順) | モーダルオープン |
| loading | 検索・絞り込み実行中 | Search押下 / フィルタ変更 |
| success | 検索結果一覧を表示 | 検索完了 |
| error | 検索処理に失敗 | API呼び出し失敗 |
| empty | 検索結果0件 | 該当なし |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| targetTable | string | yes | — | 検索対象テーブル名(参照先) |
| displayColumn | string | yes | — | 一覧に表示する代表列(FR4.1の名称解決結果) |
| filterColumns | array | no | [] | カラムごとの絞り込み条件(一覧画面と同様。Q2で採用) |
| onSelect | function | yes | — | 行選択時のコールバック(選択値をFK入力欄へ反映しモーダルを閉じる) |
| onCancel | function | yes | — | Cancelボタン押下時のコールバック |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 対象外(デスクトップ主体方針。Q6) |
| tablet (768–1024px) | 対象外 |
| desktop (>1024px) | 画面中央にモーダル表示、幅は640px程度 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`、`aria-modal="true"`、`aria-labelledby`でタイトルを参照 |
| Keyboard interaction | Escapeで閉じる(onCancel相当)、Tabでフォーカストラップ内を循環、行はEnterで選択 |
| Label / aria-label | タイトル文言「参照先を検索(<targetTable>)」 |
| Contrast ratio | WCAG AA(4.5:1 text, 3:1 UI components) |
| Screen reader | 開いた時点で検索入力欄にフォーカス移動をアナウンス。検索結果件数を`aria-live="polite"`で通知 |
| Focus management | オープン時: 検索入力欄。クローズ時: FK入力欄(トリガー要素)へ返す |

### Usage Example

```
<FkReferencePicker
  targetTable="会員"
  displayColumn="氏名"
  filterColumns={["status", "登録日"]}
  onSelect={(value) => setFieldValue("memberId", value)}
  onCancel={closeModal}
/>
```

---

## 設定管理タブパネル(ConfigTabPanel)

| Field | Value |
|---|---|
| Component | ConfigTabPanel |
| Description | 対象テーブルの設定種別(基本/メニュー/検索条件/一覧表示/編集対象外/バリデーション/フォーム部品/論理表示名)を切り替えるタブと、各タブ内の設定マトリクス |
| Category | input / layout |

### States

| State | Description | Trigger |
|---|---|---|
| default | 「基本」タブが選択された初期状態 | 画面遷移時 |
| loading | 設定データ取得中 | タブ切替 / 画面初回表示 |
| success | 設定マトリクスが表示された状態 | データ取得完了 |
| error | 保存に失敗 | Save押下後のAPIエラー |
| empty | スキーマ未取り込み(対象テーブルの列情報がない) | 取り込み前 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| tableName | string | yes | — | 対象テーブル名 |
| activeTab | string | no | "basic" | 現在選択中のタブ種別 |
| onSave | function | yes | — | Save押下時のコールバック(タブ単位で保存) |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 対象外(デスクトップ主体方針) |
| tablet (768–1024px) | 対象外 |
| desktop (>1024px) | タブは横並び、必要に応じて折り返し |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `tablist` / `tab` / `tabpanel`(ロール編集画面7bと同一パターン) |
| Keyboard interaction | 矢印キーでタブ間移動、Tabでタブパネル内に入る |
| Label / aria-label | 各タブに`aria-label`(例: 「検索条件」) |
| Contrast ratio | WCAG AA |
| Screen reader | タブ切替時にパネルの見出しを読み上げ |
| Focus management | タブ切替時、対応するパネルの先頭要素へはフォーカス移動しない(タブ自体にフォーカスを保持するWAI-ARIA Tabsパターンに準拠) |

### Usage Example

```
<ConfigTabPanel
  tableName="書籍"
  activeTab="searchCondition"
  onSave={(tab, values) => saveConfig("書籍", tab, values)}
/>
```

---

## スキーマ取り込みプレビュー(SchemaImportPreview)

| Field | Value |
|---|---|
| Component | SchemaImportPreview |
| Description | JDBC DatabaseMetaData取得結果をチェックボックス付きでプレビューし、選択したテーブルのみ取り込みを確定する |
| Category | input / display |

### States

| State | Description | Trigger |
|---|---|---|
| default | 未取得(接続先選択待ち) | 画面表示時 |
| loading | スキーマ取得中 | 「スキーマ取り込み」押下 |
| success | テーブル一覧プレビュー表示 | 取得完了 |
| error | 接続失敗・取得失敗 | API呼び出し失敗 |
| empty | 接続先未設定 | DB接続先が0件 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| connectionId | string | yes | — | 対象DB接続先ID |
| tables | array | no | [] | 取得済みテーブル一覧(name, primaryKey, isView) |
| onConfirm | function | yes | — | 選択したテーブルの取り込み確定コールバック |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 対象外 |
| tablet (768–1024px) | 対象外 |
| desktop (>1024px) | リストは縦積み、スクロール可能 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | リストは`role="list"`、各行は`role="listitem"`にチェックボックスを内包 |
| Keyboard interaction | Tabで各チェックボックスへ、Spaceでトグル |
| Label / aria-label | 各行のチェックボックスに`aria-label`(テーブル名+PK情報。ビューは「(view, PKなし)」を含める) |
| Contrast ratio | WCAG AA |
| Screen reader | 取得完了時に件数を`aria-live="polite"`でアナウンス |
| Focus management | 取り込み確定後、設定管理画面(10.)の対象テーブルへ遷移しフォーカスをh1へ |

### Usage Example

```
<SchemaImportPreview
  connectionId="conn-1"
  tables={[{ name: "書籍", primaryKey: "id", isView: false }, { name: "貸出履歴ビュー", primaryKey: null, isView: true }]}
  onConfirm={(selected) => importSchema(selected)}
/>
```

---

## 監査ログテーブル(AuditLogTable)

| Field | Value |
|---|---|
| Component | AuditLogTable |
| Description | 監査ログの検索・フィルタ・一覧表示に、エクスポート・保持期間超過削除の操作を付加したテーブル |
| Category | display / input |

### States

| State | Description | Trigger |
|---|---|---|
| default | 初期表示(直近順) | 画面表示時 |
| loading | 検索・エクスポート・削除処理中 | 各操作押下 |
| success | ログ一覧を表示 | 取得完了 |
| error | 取得・エクスポート・削除の失敗 | API呼び出し失敗 |
| empty | 該当ログ0件 | 検索結果なし |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| filters | object | no | {} | 操作種別・利用者・期間の絞り込み条件 |
| onExport | function | yes | — | Export押下時のコールバック(画面/API両対応。FR7.4) |
| onDeleteExpired | function | yes | — | 保持期間超過分の削除コールバック(確認ダイアログ経由。FR7.5) |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 対象外 |
| tablet (768–1024px) | 対象外 |
| desktop (>1024px) | 一覧画面(3.)と同一のテーブルレイアウト |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | テーブルは`table`、ソート可能列は`aria-sort` |
| Keyboard interaction | Tabで検索欄・フィルタ・Export/削除ボタンへ順に移動 |
| Label / aria-label | Export/削除ボタンに明示ラベル(アイコンのみにしない) |
| Contrast ratio | WCAG AA |
| Screen reader | 削除確認ダイアログはフォーカストラップを持つ(設定インポート確認ダイアログ 8. と同様) |
| Focus management | 削除確定後、フォーカスを削除ボタンへ戻す |

### Usage Example

```
<AuditLogTable
  filters={{ actionType: "create", user: null, period: "last30days" }}
  onExport={() => exportAuditLog(filters)}
  onDeleteExpired={() => confirmDeleteExpired()}
/>
```

---

## アカウント編集フォーム(AccountEditForm)

| Field | Value |
|---|---|
| Component | AccountEditForm |
| Description | 管理者によるアカウントの新規作成・編集・無効化を行うフォーム(13b) |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default(新規) | 全フィールド空、Statusフィールド非表示(常に有効で作成) | 「+ New」押下 |
| default(編集) | 既存値を表示、Statusフィールド表示 | 一覧の行選択 |
| loading | 保存処理中 | Save押下 |
| success | 保存完了メッセージ表示、一覧画面へ戻る | 保存成功 |
| error | バリデーションエラー・保存失敗 | 入力不正 / API失敗 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| mode | "create" \| "edit" | yes | — | 新規作成か編集かでStatusフィールドの表示要否を切り替える |
| account | object | no | null | 編集時の既存アカウント情報 |
| businessDataRoles | array | yes | — | 選択可能な業務データロール一覧(FR5.1〜FR5.4のテーブル/カラム権限ロール)。管理者権限とは別のUI要素・別のデータモデルであり、同じ選択肢群に混在させない [R-01フォロー(refined-mockups review)] |
| isAdmin | boolean | no | false | 管理者権限(FR5.5)の付与有無。businessDataRolesとは独立したトグル/ラジオとして表現する [R-01フォロー(refined-mockups review)] |
| onSave | function | yes | — | 保存コールバック。作成時はFR6.4.1のアカウント作成通知メールをトリガーする |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 対象外 |
| tablet (768–1024px) | 対象外 |
| desktop (>1024px) | 編集画面(5.)と同一の1カラムフォームレイアウト |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | フォームは`form`、ロール選択は`group`+`aria-label="割り当てロール"` |
| Keyboard interaction | Tabで各フィールドへ、ラジオ/チェックボックスはSpace/矢印キー |
| Label / aria-label | 全フィールドに可視ラベル、必須項目は`aria-required` |
| Contrast ratio | WCAG AA |
| Screen reader | Statusを「無効化済」に変更する操作は破壊的操作として`aria-describedby`で「無効化するとログインできなくなります」を関連付け |
| Focus management | 保存成功後、一覧画面(13a)の該当行へフォーカスを戻す |

### Usage Example

```
<AccountEditForm
  mode="edit"
  account={{ name: "山田太郎", email: "yamada@example.com", businessDataRoles: ["貸出担当"], isAdmin: false, status: "active" }}
  businessDataRoles={["貸出担当", "閲覧者"]}
  onSave={(values) => saveAccount(values)}
/>
```

---

## ログイン試行制限表示(LoginLockoutNotice)

| Field | Value |
|---|---|
| Component | LoginLockoutNotice |
| Description | ログイン試行回数の上限到達時に、ログイン画面上部へ表示するインラインエラー |
| Category | feedback |

### States

| State | Description | Trigger |
|---|---|---|
| default | 非表示 | 通常時 |
| error | ロック中メッセージを表示(残り時間は表示しない。Q5) | 連続n回のログイン失敗 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| locked | boolean | yes | false | ロック中かどうか |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 対象外 |
| tablet (768–1024px) | 対象外 |
| desktop (>1024px) | ログインフォーム上部にインライン表示(ログイン画面 1. と同一のエラー表示位置) |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `alert`(即時通知) |
| Keyboard interaction | 対象外(表示のみ) |
| Label / aria-label | メッセージテキストそのものがラベル |
| Contrast ratio | WCAG AA、色のみに依存しないアイコン付き |
| Screen reader | `aria-live="assertive"` |
| Focus management | 表示時にフォーカスを移動しない(フォームのフォーカス位置を維持) |

### Usage Example

```
<LoginLockoutNotice locked={true} />
```
