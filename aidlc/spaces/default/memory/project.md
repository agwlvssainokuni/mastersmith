# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project needs stable, durable guidance beyond the
> team practice (for example, package-specific release checks or an additional
> regression suite for a legacy component).

## Way of Working

- コミットはこまめに行う。ファイル変更のまとまりごと(例: `aidlc-state.md`/`audit.md` 更新時、Step/Item完了時)に区切ってコミットする。
- コミットのタイミングはAIが自発的に判断し提案する。ユーザーからの明示的な指示(「コミットして」等)を待たない。
- コミットを実行する前に、必ずユーザーの承認を得る。承認なしにコミットを実行しない。
- コミットメッセージは日本語で記述する。
- ユーザーが提供する参考資料は `reference/` ディレクトリに置く。このディレクトリは `.gitignore` でGit管理対象外にする。
- ドキュメント(要件定義書、設計書などの成果物やコードコメント等)を作成する際、`reference/` 配下のファイルパスを書かない(`reference/` はGit管理外のため、リポジトリをcloneした他の読者の手元には存在せず、パス参照は成立しない)。`reference/` 配下のファイルの内容は積極的に読み込んで理解し、必要な内容(結論・要点)はドキュメントに直接書き込んでよい。
(決定 2026-09-10)

## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

## Change Control

<!-- Project-specific. Mode: strict or relaxed. Strict here holds for every intent and cannot be changed from chat. -->

## Deployment

<!-- Project-specific specialisation. -->

## Code Style

- 生成する成果物(プログラムのソースファイル)には、先頭に必ずApache License 2.0の標準ヘッダーコメントを挿入する。年は `2026` 固定、著作権者名は `agwlvssainokuni` 固定(リポジトリのGitユーザー名)。コメント記法は言語のコメント構文に合わせて変換する。

  ```
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
  ```

- ドキュメントや回答の文章中でファイル・ディレクトリのパスに言及する際は、絶対パスではなくプロジェクトルート(`mastersmith`)からの相対パスで記述する(例: `aidlc/spaces/default/memory/project.md`)。ツール呼び出しの引数(Read/Write/Edit/Bash等)では引き続き絶対パスを使用してよい。
(決定 2026-09-10)

## Tech Stack

<!-- Technology choices locked for this project. -->

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

- DECIDED: MasterSmithはMasterMeister(動的スキーマ探索方式)の後継・置き換えではなく、カスタマイズ性(表示名/表示順/書式/編集部品/バリデーション)重視の別アプリとして新規に立ち上げる。MasterMeisterはEOLにしない (Stage intent-capture, 2026-09-10) (learned 2026-09-10) <!-- cid:260910-master-mgmt-app:intent-capture:8ae3a77ca57f74c74435bcb80c9c9209e4a8b1631ff920dcca047a19c22269fc -->
- DECIDED: 利用者は社内の業務担当者。汎用DBアクセスツールはエンジニア寄りで使いにくく、業務特化に見えるマスタ管理ツールへのニーズがある (Stage intent-capture, 2026-09-10) (learned 2026-09-10) <!-- cid:260910-master-mgmt-app:intent-capture:f23f02a182d7e6f202f4ab7c66b637e691a73fef5f48b1c20f83c2aad854a1c9 -->
- DECIDED: 成功の定義は、単一のアプリ本体を設定の入れ替えだけで複数業務(ECショップ、ポイント管理システム、蔵書管理など)のマスタ管理に転用できること (Stage intent-capture, 2026-09-10) (learned 2026-09-10) <!-- cid:260910-master-mgmt-app:intent-capture:e21e04fc4df6e663f233a9246d2384467405b05bb1ccf3810e9bb90ababffd25 -->
- DECIDED: 当初想定のMVP範囲(一覧/詳細編集/メニュー画面)から、ユーザ管理・監査ログ・詳細な権限制御(RBAC: ロール/主権限FULL-READ-NONE-指定なし/階層継承/補助権限CREATE-DELETE)を追加でMVPスコープに含める。複数テーブル合成画面は引き続き対象外 (Stage scope-definition, 2026-09-10) (learned 2026-09-10) <!-- cid:260910-master-mgmt-app:scope-definition:abadce739116e455be20bbd8da6a3b76c8365d42c4a68d1a7f8bb0c7367d505c -->
- DECIDED: ラフモックアップ段階で、ユーザーメニューへの表示設定(テーマ選択: ライト/ダーク、フォントサイズ選択: 大/中/小)を新規スコープとして追加でMVPに含める。ユーザー単位で設定を保持し全画面に適用する。また、権限モデルで確定していた「複数ロールを持つユーザは操作時にロールを選択する」要件を、ヘッダーのロール選択UIとして具体化した (Stage rough-mockups, 2026-09-11) (learned 2026-09-10) <!-- cid:260910-master-mgmt-app:rough-mockups:35312cfb74625099afc3ead159bf3363a41458760511102700ac8b8636805482 -->
## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

- NEVER 権限の昇格(自分自身への昇格を含む)を、権限管理者による明示的な操作を経ずに許可する(インタビューQ13: D) (affirmed 2026-09-10)

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

- ALWAYS 権限の判定は画面表示の出し分け(クライアント側UI非表示)だけに依存せず、必ずサーバー側(API/ドメイン層)で実効権限(ロール階層継承後の権限)を再検証する(インタビューQ13: A) (affirmed 2026-09-10)
- ALWAYS 監査ログは改ざん・削除ができないようにする。アプリケーションからのUPDATE/DELETE経路を持たない追記専用(append-only)とし、少なくとも操作者・操作対象・操作種別・日時・変更前後の値を記録する(インタビューQ13: B) (affirmed 2026-09-10)
- ALWAYS パスワード等の認証情報はハッシュ化して保存し、平文でログ・監査ログ・エラーメッセージに出力しない(インタビューQ13: C) (affirmed 2026-09-10)
- ALWAYS 共通エンジン層(一覧/編集画面、権限判定、監査ログ記録、テーマ/フォントサイズ等の表示設定)には特定業務固有のテーブル名・カラム名・業務ルールをハードコードしない。業務固有の設定層(表示名・表示順・書式・編集部品・バリデーション定義)は、コードを変更せずに差し替え可能なデータとしてエンジン層から読み込む(インタビューQ10: A) (affirmed 2026-09-10)
- ALWAYS 設定定義自体の誤り(必須プロパティ欠落等)は起動時・設定読込時に検知しfail fastする。利用者(業務担当者)の入力データ検証エラーは、開発者向けのスタックトレースではなくフィールド単位のエラーメッセージとして返す(インタビューQ11: A) (affirmed 2026-09-10)

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- aidlc engine review-brief summary がツール内部エラー(aidlc-review-brief.ts does not export main(argv))で失敗した場合は、コンパクトな決定ブリーフの自動生成をスキップし、統合サマリーを手動で作成してユーザーに提示してよい (learned 2026-09-10) <!-- cid:260910-master-mgmt-app:approval-handoff:6e52038d846ed3058b78672ea08cc94d83d08b55f68c1667a4a2f6e320d8f5af -->
