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

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
