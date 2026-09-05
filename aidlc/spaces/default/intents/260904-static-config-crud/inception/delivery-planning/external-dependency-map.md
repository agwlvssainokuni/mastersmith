# External Dependency Map: MasterSmith MVP

## 概要

開発者一人・AI駆動体制のため、チーム外部との調整を要する依存はほとんどないが、delivery-planning-questions.md Q6で2件を特定した。

## 依存一覧

| # | 依存 | 所有者 | 見込み所要期間 | ブロックするBolt | 遅延時の対応 |
|---|---|---|---|---|---|
| 1 | 3種RDBMS(PostgreSQL・MySQL・MariaDB)の動作確認用インスタンス。schema-ingestion(U1)の実装、および前倒しで洗い出す特性テスト(RDBMS間の型差異・複合主キー構成・主キーなしテーブル・ビューの扱い)には、実際に接続確認できる3種のインスタンスが必要 | 開発者自身(自宅環境でのセットアップ) | Bolt 1着手前(短時間、Dockerコンテナ等での用意を想定) | Bolt 1(Walking Skeleton) | 3種のうち用意が遅れたRDBMSがあれば、用意できた種別から先に特性テスト・実装を進め、残りは追って検証する。3種すべての検証完了はBolt 1のDefinition of Doneの一部であるため、完了までWalking Skeletonの承認ゲートは開かない |
| 2 | 未公開の自作ライブラリ2件(`make-you-chic-ui`、`java-mustache-processor`)の具体的な取り込み方式(gitサブモジュール、ローカルMavenリポジトリ、モノレポ内への直接配置等)。practices-discovery(evidence.md)から持ち越された未確定事項で、ドメイン設計以降で確定する予定だったが、Contract Design完了時点でも未確定のまま残っている | 開発者自身 | Bolt 1着手前(`make-you-chic-ui`)、Bolt 7着手前(`java-mustache-processor`) | Bolt 1(frontend-core、`make-you-chic-ui`)、Bolt 7(notification、`java-mustache-processor`) | 各Bolt開始前に取り込み方式を確定する。方式決定自体は本プロジェクトの技術スタック選定の一部であり、外部の承認や第三者の作業を待つものではないため、遅延リスクは低いが、Bolt着手のチェックリスト項目として明示的に確認する |

## その他の外部要因

上記以外に、外部API・外部チームからのハンドオフ・承認待ちといった、チームの外側で発生する遅延要因は現時点で存在しない(実行環境は自宅サーバ1台であり、ステージング/本番の分離もなく、外部関係者による承認プロセスも前提としていない)。
