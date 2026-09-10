# Practices Discovery エビデンス(ドラフト)

> **ステータス: ドラフト(未承認)**
> 本プロジェクトはグリーンフィールド(既存コードベースなし)であり、
> reverse-engineering ステージはスコープ上SKIPされているため、リポジトリ内コードの
> 静的解析による証跡は存在しない。本ドラフトは代わりに、フレームワーク既定値と
> 本プロジェクトで既に確定済みの決定事項を調査した結果をエビデンスとして記録する。
> チームの意図・追認は未確認であり、本ステージ後続のインタビューステップで確認する。

## 調査対象

### 1. `aidlc/spaces/default/memory/org.md`(フレームワーク既定値)

グリーンフィールドプロジェクトの出発点として、以下5セクションを確認した。

- **Way of Working**: トランクベース開発、短命フィーチャーブランチ、Constructionの
  worktreeベース/マージ先は `main`、Squash-mergeでのBolt統合。
- **Walking Skeleton**: スコープファイルの `skeleton: on/off` 宣言に応じたBolt 1の扱い、
  Bolt 1完了後のラダープロンプト(自律継続 or 毎Boltゲート)。
- **Testing Posture**: 既定 Methodology は test-after、Ordering は「各テスト対象レイヤー
  実装後にそのレイヤーのテストを作成・実行する」。スコープ種別ごとのカバレッジ/回帰
  フロアの追加ルール。
- **Deployment**: マージ時staging自動デプロイ、Production手動承認ゲート。
- **Code Style**: フォーマッタ/リンタはプロジェクトルート設定に委譲、言語慣用の命名規約。

これらはあくまでフレームワークの既定(デフォルト)であり、本プロジェクトのチームが
まだ明示的に確認・追認した事実ではない。org.md自身も「Way of Working」「Testing
Posture」等の各セクションで、Methodology/Orderingは practices-discovery で追認されて
初めて `team.md` に記録される旨を明記している。

### 2. `aidlc/spaces/default/memory/project.md`(既確定のプロジェクト固有事項)

以下は本プロジェクトで既に決定済みの事実であり、再確認の対象ではなく前提として
そのまま踏襲する。

- **Way of Working**: こまめなコミット(状態更新・Step/Item完了単位)、コミット提案は
  AIが自発的に行いユーザー承認を得てから実行、コミットメッセージは日本語、
  `reference/` はGit管理外資料置き場、ドキュメントから `reference/` 配下への
  ファイルパス直接参照はしない(内容は読み込んで要点をドキュメントへ直接記載する)。
  (決定 2026-09-10)
- **Code Style**: 生成ソースファイル先頭にApache License 2.0標準ヘッダー
  (年 `2026`、著作権者 `agwlvssainokuni` 固定)を挿入する。ドキュメント中のパス表記は
  プロジェクトルート(`mastersmith`)からの相対パスとする。(決定 2026-09-10)
- **Decided(既存決定事項、`## Decided` セクション)**: MasterSmithの位置付け
  (MasterMeisterの後継ではなく別アプリ)、想定利用者(社内業務担当者)、成功定義
  (単一アプリ+設定入替による複数業務への転用)、MVPスコープ(ユーザ管理・監査ログ・
  RBAC含む)、表示設定(テーマ/フォントサイズ)とロール選択UIの追加、など。
  いずれもideationフェーズの各ステージ(intent-capture, scope-definition,
  rough-mockups)で既に確認済み。
- **Corrections**: `aidlc engine review-brief summary` のツール内部エラー時の代替手順
  (learned 2026-09-10)。これは運用上の学習事項であり、本ステージの対象である
  team-practices/discovered-rules とは別種の記録である。

`project.md` の `## Walking Skeleton`・`## Testing Posture`・`## Change Control`・
`## Deployment`・`## Tech Stack`・`## Scope Overrides`・`## Forbidden`・`## Mandated` は
現時点でいずれも空欄であり、これらの領域についてはプロジェクト固有の特化ルールが
まだ存在しない。

### 3. スコープ・ワークフロー状況(`aidlc-state.md`)

- スコープ: `config-driven-admin-mvp`、Depth: Comprehensive、Test Strategy: Comprehensive、
  Change Control: relaxed(ユーザー設定)。
- 操作・運用系ステージ(4.1〜4.7、deployment-pipeline等)はすべてSKIP対象であり、
  本プロジェクトの現段階ではデプロイ実行そのものより、方針としてのDeployment
  プラクティスを合意しておくことに主眼がある。

## エビデンスの限界

- コードベース・既存CI設定・既存ブランチ運用の実地調査は存在しない(グリーンフィールド
  かつreverse-engineeringステージSKIPのため)。
- したがって team-practices.md の内容はorg.md既定値の転記であり、「発見された事実」
  ではなく「確認待ちの提案」である。
- discovered-rules.md の Mandated/Forbidden は、人間が明示的に述べた制約のみを計上する
  方針上、インタビュー未実施の現時点では実質空である。

## 次のステップ(本ステージの後続、インタビューステップ)

- team-practices.md の5セクション(Way of Working / Walking Skeleton / Testing Posture /
  Deployment / Code Style)をユーザーに提示し、org.md既定を追認するか、修正するかを確認する。
- 特に Testing Posture の Methodology(test-after妥当か、TDD/BDD等を希望するか)と
  Walking Skeletonの `skeleton: on/off` および Construction Autonomy Mode の希望を確認する。
- 人間から明示的なALWAYS/NEVER制約が述べられた場合、discovered-rules.mdへ反映する。
- 確認・承認された内容を最終版として整理し、`aidlc-state.ts practices-promote` 等の
  書き込み経路を通じて `team.md` / `project.md` へ昇格する(本ドラフト自体を直接
  memory配下へコピーする作業ではない)。
