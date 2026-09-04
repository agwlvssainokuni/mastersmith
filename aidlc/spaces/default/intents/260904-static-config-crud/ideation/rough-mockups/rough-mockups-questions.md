# Rough Mockups & Concept Visualization — Questions

Grounded in `intent-statement.md`・`scope-document.md`・`intent-backlog.md`。UI初期構想(手書き相当のラフ案)を確認する。

## Q1. 主要な画面遷移の入口はどこですか?(最初にログインした後、何が表示されますか)

A. ログイン後、業務(テーブル)一覧を兼ねたメニュー画面(ダッシュボード的な位置づけ)が表示される
B. ログイン後、いきなり最初のテーブルの一覧画面が表示される
C. Not yet defined
X. Other (please specify)

[Answer]: A(具体化)— トップ画面に業務(テーブル)の一覧をCard形式で表示する。
**Timestamp:** 2026-09-04T15:32:19Z
**Mode:** chat

## Q2. 中核となるハッピーパスの流れはどのようなイメージですか?

A. ログイン → メニューでテーブルを選択 → 一覧画面(検索・絞り込み) → 行を選んで詳細画面 → 編集画面で更新
B. ログイン → メニュー → 一覧画面から直接インライン編集(詳細画面を経由しない)
C. Not yet defined
X. Other (please specify)

[Answer]: A — ログイン → トップ(業務カード一覧) → カードを選択 → 一覧画面(検索・絞り込み) → 行を選んで詳細画面 → 編集画面で更新。
**Timestamp:** 2026-09-04T15:32:19Z
**Mode:** chat

## Q3. 情報の階層構造(メニューの見せ方)はどのようなイメージですか?

A. サイドナビゲーション(左側に業務・テーブルの一覧、常時表示)
B. トップナビゲーション(上部にメニュー、業務ごとにタブ切り替え)
C. Not yet defined
X. Other (please specify)

[Answer]: A(具体化)— サイドナビを残す(縮小可能)。make-you-chic-uiのAppShell(サイドバー+トップバー+コンテンツ)をベースとする。
**Timestamp:** 2026-09-04T15:32:19Z
**Mode:** chat

## Q4. 対応するデバイス・画面サイズはどこまで想定していますか?

A. デスクトップ利用が主。レスポンシブ対応(タブレット・モバイル)は必須ではない
B. デスクトップ・タブレット・モバイルすべてで一定水準の操作性を確保したい
C. Not yet defined
X. Other (please specify)

[Answer]: A — デスクトップ利用が主。レスポンシブ対応は必須ではない。
**Timestamp:** 2026-09-04T15:32:19Z
**Mode:** chat

## Q5. アクセシビリティについて、特に意識したい水準はありますか?

A. WCAG 2.1 AA相当を基本方針とする(make-you-chic-uiのデザイン原則に準拠)
B. 個人利用が中心のため、特別な水準は設けない(基本的な配慮のみ)
C. Not yet defined
X. Other (please specify)

[Answer]: A — WCAG 2.1 AA相当を基本方針とする。
**Timestamp:** 2026-09-04T15:32:19Z
**Mode:** chat

## Q6. 画面の見た目・テーマについて、業務ドメインごとの作り分け(make-you-chic-uiの4軸テーマ機能)をこのラフ案の段階でどう扱いますか?

A. 各業務(家電EC・ポイント管理・蔵書管理)ごとに異なるブランドカラー・テーマを設定し、「個別開発したように見える」ことを早い段階から意識したワイヤーフレームにする
B. ラフ案の段階ではレイアウト・構造に集中し、テーマの作り分けはRefined Mockups以降で検討する
C. Not yet defined
X. Other (please specify)

[Answer]: B — ラフ案の段階ではレイアウト・構造に集中し、テーマの作り分けはRefined Mockups以降で検討する。
**Timestamp:** 2026-09-04T15:32:19Z
**Mode:** chat

## Requested Changes Feedback

**What should change:** トップ画面のCard一覧が、実行インスタンスを業務ごとに分ける方式(feasibility Q4)と矛盾していた(複数業務ドメインを1画面に並べていた)。単一インスタンス内のテーブル・メニューのCard一覧に修正する。あわせて製品リードのレビュー指摘(R-01: 権限定義画面の欠落、R-02: ログイン/詳細画面の状態表欠落、R-03: 先送り判断の出典不明、R-04: アカウント関連画面のアクセシビリティ注記欠落)をすべて反映する。

## Q7. (follow-up) 権限の設定方法について、具体的なモデルを確認します。

[Answer]: 権限は「ロール」単位で定義する。テーブル単位の権限は一覧(検索)・詳細・作成・編集・削除の5操作。カラム単位の権限は更新可(デフォルト)・表示のみ・非表示の3状態。ロールをユーザまたはグループに割り当てる。複数のロールが割り当てられた利用者は、作業時にロールを選択(切り替え)できる。
**Timestamp:** 2026-09-04T16:06:51Z
**Mode:** chat

## Requested Changes Feedback (2)

**What should change:** 承認ゲートでのレビュー指摘反映。R-01: 設定エクスポート/インポート画面(Must-have)の追加。R-02: ロール一覧画面に「割り当て」への導線を追加。R-03: グループ管理画面の追加。

## Requested Changes Feedback (3)

**What should change:** 承認ゲートでの2回目のレビュー指摘反映。R-01: グループ管理画面のメンバー編集をインライン操作(アコーディオン展開)に統一。R-02: 設定インポートの確認ダイアログをASCIIで追加。

## Requested Changes Feedback (4)

**What should change:** 承認ゲートでの3回目のレビュー指摘反映。R-01: ログインエラーメッセージの文言をEmail/ID双方に対応する表現に統一。R-02: グループの削除・改名導線が省略されている旨を明記。

## Requested Changes Feedback (5)

**What should change:** 承認ゲートでの4回目のレビュー指摘反映。R-01: 主キーなしテーブルの一覧画面で編集・削除導線を非表示にするルールを明記。

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
