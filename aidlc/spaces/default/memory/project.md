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

- 質問への回答が確定した時点(成果物生成前)、成果物を作成した時点、内容確認(サマリー確認や承認)を行った時点のそれぞれでこまめにコミットする(いずれもユーザー承認を得てから実行する既存ルールに従う)。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:refined-mockups:e640c85be4e3fec0833ea657dad40d98ae07b868a164de634d21d2e2bcb687b7 -->
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
- 回答の中に新しい認証方式(例: JWTのアクセストークン/リフレッシュトークン)への言及があった場合、既存のセッション用語(タイムアウト等)との対応関係を明示的にフォローアップ質問で確認してから要件化する。 (learned 2026-09-11) <!-- cid:260910-master-mgmt-app:requirements-analysis:5e33c4b5d710a31835a8ee8d75051380ebb06940cdc13784de25c5e9e509e5e5 -->
- 機能(実装すること)と非機能(達成すべき水準)の両方の性質を持つ項目は、FR/NFR両方に相互参照付きで記載する(例: OTELエクスポート対応)。 (learned 2026-09-11) <!-- cid:260910-master-mgmt-app:requirements-analysis:93085cba9c3ae9992b82258886b7ffa4b05a63b3067f7d83300ab86aba29e34c -->
- ideationのintent-backlogに明記のない項目でも、requirements-guide.mdの完全性チェックリスト(データexport/import等)に基づき能動的に質問し、回答があればスコープに含めてよい。 (learned 2026-09-11) <!-- cid:260910-master-mgmt-app:requirements-analysis:7142481501cad408324b9dbffc63248dd847205d96524309c90c5a451b0d177c -->
- リファインドモックアップステージでuser-storiesがSKIP対象の場合、ラフモックアップ(wireframes.md/user-flow.md)と要件定義書(requirements.md)から直接モックアップを設計してよい。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:refined-mockups:accadee9ee046699483cff2ff33b602e35679a3cb791de18b740343019c6d4f6 -->
- ラフモックアップが対象としていない画面(ログイン等の認証系画面、ユーザ管理・監査ログ等の管理系画面)がある場合、リファインドモックアップステージの冒頭でインタビューにより設計対象範囲を確認してから着手する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:refined-mockups:f933887ec0267246d3f26af1dae1bd84e3fed51af19d234e1a70722118713154 -->
- 本プロジェクトのアクセシビリティは、WCAG準拠レベルを明示的な達成目標とはせず、キーボード操作・ラベル付与・コントラスト確保等の個別項目チェックリストで十分とする。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:refined-mockups:89466355da28eb13f3bc82c6e2e4ddf5b309388605b3439294cddc775bfe91e4 -->
- デザインシステム(make-you-chic-ui)の実際のコンポーネント一覧が未確認の場合、モックアップ・インタラクション仕様上は想定コンポーネント名で記載し、実装フェーズで実際のライブラリ内容に読み替える前提とする。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:refined-mockups:a366c255f3e6dc9a2d7e533c8577e80f109294d322b8c2035bebb65f194a73fc -->
- user-storiesステージがSKIP対象の場合、Domain Design以降のtraceability.jsonはrequirements.mdの全FRを対象に作成する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:domain-design:4ed0dd2ced21d55c95f40e9ce22c390e3d6256908a3811f2707daa68e21a4580 -->
- コンポーネントカタログ(components.md)の対象外と判断したFR(ビルド・運用系の関心事等)は、traceability.jsonで"Deferred"ステータスとし、targetに対応する後続ステージ名を明記する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:domain-design:5ae9c08444135690884415bf28f87dad264386d6c7da5833e479ef774066b9a7 -->
- 実行時コンポーネントではなくビルド成果物に相当するFR(翻訳リソース等)は、traceability.jsonで"N/A"ステータスとし、正当化コメントをtargetに明記する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:domain-design:83024204133a7f31c1e687c049bcd4b8e1a416db8f06f8afa52d992e6adfaa0f -->
- 監査ログ(AuditLogging)のような横断的に呼び出される記録コンポーネントは、明示的なdepends_onではなくイベント駆動(ドメインイベント発行・購読)による疎結合を優先する。イベント配信の信頼性保証の詳細化は後続のNFR設計に委ねる。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:domain-design:8afa2d8676c0fa484fafd6c8904544776182cd8128681dfce95c507fc3241de8 -->
- Domain Designのコンポーネントカタログにsync/eventが混在した循環依存が記録されている場合、Unit依存DAGではevent依存(疎結合な非同期発行)をDAGエッジから除外し、sync依存のみをエッジとすることで循環を解消してよい。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:units-generation:3a0a0081fea9212b6adb6886ee5e896a79d9e35d7cde990d1ef1b51a237cda22 -->
- 確定済み技術スタック(単一WAR、フロントエンド同梱等)がDomain Designのコンポーネントカタログに現れない実装単位(フロントエンドUI、ビルド/パッケージング等)を要求する場合、Units GenerationでUnitとして追加してよい。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:units-generation:81a3472745c4e324a1bbc648fda35976ffc521936fc369e4c38896d77623117c -->
- user-storiesステージがSKIP対象の場合、Units Generationのunit-of-work-story-map.mdおよびtraceability.jsonもUS IDではなくrequirements.mdの全FRを対象に作成する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:units-generation:4571d1eeee8fbd8bbe8fbf47bc4bcf87c361c3920ec4badcabc3e034d85b9302 -->
- Units Generationのユニット境界戦略は、AIの推奨案(粗粒度グルーピング)よりもユーザーが明示的に選択した方針(コンポーネント単位1:1等)を優先する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:units-generation:36bbce2a161da66df6622a91e7a4744745c7e81212339f97901a2a6bc1419f54 -->
- 同一ユニットが複数の異なる境界(REST向けと内部インタフェース向け等)を持つ場合、性質の異なる境界として別契約に分けて管理する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:contract-design:a5c526e50e144f8366a886c0c648222ed3bc496b8ca19fb77a4650442b0977bb -->
- HTTP APIのエラー形式はRFC 7807ではなくRFC 9457(2023年7月、RFC 7807を正式にobsolete)を採用する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:contract-design:b57cea4bf337a9ae74f5dc88f7183fece0f42f1e768e74295fc6ca8a3c5e84bd -->
- ユーザーが明示的に指定した場合、URLパスへのバージョン番号予約(`/api/v1/...`)も含め一切のバージョニングを省略してよい。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:contract-design:11cdfca4ac48189098db06fb4395eac84a920b71fbb9877bc3ae05006a19da80 -->
- 内部向けAPIであっても簡易な取り決めで済ませず、標準のOpenAPI形式で正式に仕様化する。 (learned 2026-09-13) <!-- cid:260910-master-mgmt-app:contract-design:292d7ebffb165bd856b1b757b206696f31008b15d6443394d2718b0111894f2e -->
