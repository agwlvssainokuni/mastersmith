# Initiative Brief — MasterSmith(マスタ管理アプリ)

## Intent and Problem Statement

前身ツール「MasterMeister」(実行時DBスキーマ動的探索方式)の実運用を通じて、動的スキーマ探索そのものの必要性は薄く、業務に合わせたカスタマイズ性(表示名/表示順/書式/編集部品/バリデーション)を高めることの方が価値が高いと判明した。また、汎用のDBアクセスツールはエンジニア寄りすぎて業務担当者には使いにくいという課題もある(`intent-statement.md`)。

MasterSmithはMasterMeisterの後継・置き換えではなく、カスタマイズ性を重視した別アプリとして新規に立ち上げる。MasterMeisterはEOLにせず、並行稼働する。

**Target Customer**: 社内の業務担当者(`intent-statement.md`)。意思決定者は依頼者本人のみで、他に合議すべき相手はいない(`stakeholder-map.md`)。

**Success Metrics**: アプリ本体は単一だが、設定の入れ替えだけで複数業務(例: ECショップ、ポイント管理システム、蔵書管理)のマスタデータ管理に転用できることを成功の目安とする(`intent-statement.md`)。

## Market Validation Summary

今回のワークフローでは市場調査(Market Research)ステージを見送っている。理由は、依頼者単独の意思決定・社内単一利用者層という前提であり、外部市場への展開を想定していないためである。承認・ハンドオフ確認(Q6)でも、このステージ省略の妥当性を再確認済み。

## Feasibility and Risk Highlights

`feasibility-assessment.md`より、総合評価は「技術的実現可能性は高い」。統合対象は既存RDBMSのみで外部認証基盤や他システム連携は不要、技術スタック(Java 25 + Spring Boot + Gradle / TypeScript + Vite + React)は確立された成熟したスタック。

| リスク | 影響 | 可能性 | 承認・ハンドオフでの受容確認 |
|---|---|---|---|
| 複数RDBMS(PostgreSQL/MySQL/MariaDB)の方言差異吸収 | 中 | 中 | 追加の緩和策なしで受容(Q2) |
| 実行環境が未定 | 低 | 中 | 追加の緩和策なしで受容(Q2)。OTELエクスポート・構造化ログは今回スコープ内のため将来投入は妨げられない |
| 利用規模拡大(数十名程度) | 低 | 低 | 追加の緩和策なしで受容(Q2) |

`constraint-register.md`の技術・組織・規制制約(対象RDBMS複数対応、技術スタック確定、予算・スケジュールに厳密な制約なし、規制対象データなし)も含め、Inceptionフェーズへの持ち越しに問題なしと確認済み(Q3)。

## Scope Boundary

`scope-document.md`および`intent-backlog.md`のIn Scope(設定基盤〈ハイブリッド方式・複数RDBMS対応〉、一覧/詳細編集画面、メニュー・ナビゲーション、ユーザ管理、監査ログ、RBAC権限制御、多言語対応、CIパイプライン〈実行可能WAR〉、OTEL対応)を承認(Q1)。

Out of Scope(複数テーブル合成画面、実環境デプロイ・環境構築・監視基盤構築・性能検証、MasterMeisterとの連携・データ移行)は現時点でも対象外のまま維持(Q5)。

Intent Backlog(Must Have 10件、Should Have 2件)の構築順序は、依存関係に基づく提案順序に委任し、依頼者からの個別優先指定はなし(Q7)。設定基盤(スキーマ定義・設定ローダー)から着手し、複数RDBMS対応リスクを設計段階で早期に検証する方向性が妥当(Q2, Q7)。

## Concept Visuals

`wireframes.md`のワイヤーフレーム(トップ画面のCard形式メニュー、ヘッダーのロール選択UI〈R-01対応〉、ユーザーメニュー内のテーマ/フォントサイズ表示設定、サイドバー、一覧画面、詳細・編集画面)は、想定していたビジョンと相違ないことを確認済み(Q4)。対応デバイスはデスクトップ+タブレット、アクセシビリティは一般的な配慮(キーボード操作可能・ラベル付与)で対応する。

## Team Plan

今回のワークフローではチーム編成(Team Formation)ステージを見送っている。Construction(構築)フェーズはAIによる単独実行を前提として計画する(Q6, Q8)。人間の開発者体制は想定しない。

## Go/No-Go Recommendation

以上の確認結果に基づき、**Go(Inceptionフェーズへ進める)**と判断する(Q9)。スコープ・リスク・制約・モックアップいずれについても見直しの要望はなく、Ideationフェーズの成果物一式(`intent-statement.md`、`stakeholder-map.md`、`scope-document.md`、`intent-backlog.md`、`feasibility-assessment.md`、`constraint-register.md`、`wireframes.md`)がInceptionフェーズへの入力として確定した。
