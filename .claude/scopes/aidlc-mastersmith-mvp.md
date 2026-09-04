---
name: mastersmith-mvp
depth: Comprehensive
keywords: []
description: 対象RDBMSテーブル群向け汎用CRUD管理画面を静的設定駆動方式で生成するMasterSmithのMVP構築(compose提案・承認済みcustomスコープ)
skeleton: on
---

# mastersmith-mvp scope

`/aidlc compose` によって合成された custom スコープ。対象タスクは、実行時の
動的スキーマ解釈ではなく初期構築時に確定させた静的設定に基づいて汎用CRUD
管理画面を生成する Web アプリケーション「MasterSmith」の MVP 構築であり、
旧方式(動的スキーマ解釈)からの明示的な方針転換を伴う。

## ARS(Autonomy Risk Score)概要

総合スコアは 64/100(Comprehensive 帯域)。5成分の内訳:

| 成分 | スコア | 判定 |
|---|---|---|
| 意図の曖昧性 (IAE) | 0.55 | MED |
| コード構造不確実性 (CSU) | 0.55 | MED |
| 検証エントロピー (VE) | 0.85 | HIGH |
| リスク/影響範囲 (R) | 0.50 | MED |
| 未解決の前提 (UA) | 0.80 | HIGH |

VE が HIGH なのは、グリーンフィールドで既存テスト・CI・ビルド設定が皆無の
ため。UA が HIGH なのは、タスク文自身が設定保持形式・補助ツール位置づけ・
アクセス制御要否・多言語対応・対象RDBMS種別・認証認可監査ログスコープなど
10件の未決定論点を明示的に列挙しているため。IAE/CSU は、設定項目
(1)〜(9)と3画面仕様は具体的に列挙されている一方、複数コンポーネントに
またがる新規アーキテクチャが必要な MED 域。

## 本スコープの構成方針

機械的なEV(期待値)スクリーンでは34ステージ中24件がEXECUTEとなったが、
Ideation/Inception の重複を精査し、以下の折り畳み・反転を経て
**21 EXECUTE / 12 SKIP** まで絞り込んだ:

- **折り畳み(EXECUTE→SKIP)**: `market-research`(社内向けツールで市場調査
  対象なし)、`team-formation`(複数チーム調整の兆候なし)、`user-stories`
  (ペルソナ2種で対立ジャーニーなし。受け入れ基準は requirements-analysis、
  UXの語りは refined-mockups が担う)。
- **人手による反転(機械既定SKIP→EXECUTE)**: `units-generation` /
  `contract-design` / `delivery-planning`。タスク文自身が「設定スキーマ
  定義→設定ローダー→一覧画面→詳細・編集画面」という依存関係を持つ複数
  ユニットへの分解を明示しており、特に静的設定フォーマット(保持形式)は
  設定生成側とランタイム描画側をつなぐ正式なユニット間契約であるため、
  構造的な決定ステージとして明示的に含めた。
- **運用アウトカム系の折り畳み**: `infrastructure-design` /
  `observability-setup` / `performance-validation`。デプロイ・環境提供系
  ステージ(4.1〜4.3)がすべてSKIPであり(R=0.50は閾値0.5を超えず、かつ
  タスク文のMVP順序自体が編集画面で止まりデプロイ環境に言及がない)、
  これらの成果物を消費する下流ステージが存在しないため。後続intentで
  実運用デプロイのスコープが追加された場合は `compose` を再実行して
  復活させる想定。
- **常時実行(背骨)**: `code-generation` / `build-and-test` に加え、
  `domain-design`(CSU=0.55を解消する load-bearing 設計ステージ)、
  `practices-discovery`(既存規約が皆無のグリーンフィールドのため)、
  `ci-pipeline`(新規リポジトリでCIが皆無のため)。
- `reverse-engineering` はプロジェクトがグリーンフィールドのため、
  ステージのコンパイル済み条件により機械的にSKIP(条件ロック)。

最近傍のストックスコープは `mvp`(グリッド差分4件)だが、2件を超えるため
matched とせず、この custom スコープとして合成・承認された。

## 承認

`/aidlc compose` の提案(mode: custom, ARS 64/100)がゲートで人間により
承認され、本ファイルと `scope-grid.json` の `mastersmith-mvp` エントリが
書き出された。
