<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-04T16:06:51Z — 初稿の権限定義画面(R-01対応)は「利用者ごとにテーブル×操作を直接設定する」という単純な構造で描いたが、ユーザーから実際にはロールベース(ロール→テーブル権限+カラム権限、ロール→ユーザ/グループへの割り当て、複数ロール保有時の切り替え)というより本格的なモデルであることが判明し、画面構成を作り直した。ラフ案の段階でも、権限のように後工程への影響が大きい機能は簡略化しすぎず早めに実モデルを確認すべきだった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-04T15:52:08Z — 初稿のトップ画面ワイヤーフレームで、実行インスタンスが業務ごとに分かれる方式(feasibility Q4)を見落とし、複数業務ドメイン(家電EC/ポイント管理/蔵書管理)を1つのトップ画面に並べて描いてしまった。ユーザー指摘により、単一インスタンス内のテーブル・メニューのCard一覧に修正した。以降のステージでは「業務(テーブル)」という表現の曖昧さに注意する。
- 2026-09-04T15:52:08Z — 製品リードのレビュー指摘(R-01〜R-04)をすべて反映した: 権限定義画面(R-01)の追加、ログイン/詳細画面への画面状態表追加(R-02)、管理者操作先送り判断の出典明示(R-03)、アカウント関連画面へのアクセシビリティ注記追加(R-04)。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-04T16:20:00Z — 承認ゲートでユーザーが正式にRequest Changesを選択し、レビュー指摘R-01(設定エクスポート/インポート画面の欠落)・R-02(ロール一覧の割り当て導線欠落)・R-03(グループ管理画面の欠落)への対応を求めた。すべて反映し、グループ管理画面(7e)・設定エクスポート/インポート画面(8.)を追加した。
- 2026-09-04T16:30:00Z — 2回目の承認ゲートでも再度Request Changesとなり、Minor指摘2件(グループ管理画面のメンバー編集がインライン操作か別画面遷移か曖昧、設定インポートの確認ダイアログが未記載)への対応を求められた。グループのメンバー編集はアコーディオン形式のインライン操作に統一し、確認ダイアログのASCII表現を追加した。
- 2026-09-04T17:19:00Z — レビュー記録時に技術的なエラー(レビュー時点のバイト列不一致)が発生し、同一内容でレビューを再実行する必要があった。学びとして、レビュー確定直前は対象ファイルへの編集を一切挟まないよう徹底する。
- 2026-09-04T17:19:00Z — 3回目の承認ゲートでも再度Request Changesとなり、Minor指摘2件(ログインエラーメッセージがメール限定の文言でIDログインと不整合、グループの削除・改名導線が先送り宣言なく欠落)への対応を求められた。エラーメッセージを「ログインIDまたはパスワード」に統一し、グループ削除・改名は`[assumption]`タグ付きで先送りを明記した。
- 2026-09-04T17:27:00Z — 4回目の承認ゲートでも再度Request Changesとなり、Minor指摘1件(主キーなしテーブルの一覧画面で編集・削除導線を非表示にするルールが未反映)への対応を求められた。一覧画面のText fallbackと画面状態表に、主キーなしテーブルの場合は新規作成・編集・削除の導線を出さない旨を追記した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-04T15:32:19Z — アカウント作成(管理者操作側)の詳細な画面設計はRefined Mockups以降で行う必要がある。今回はメール駆動の利用者側フローのみラフに示した。
