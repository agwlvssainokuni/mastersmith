# NFR Design Questions — audit-logging (U7)

`construction/audit-logging/nfr-requirements/`の確定要件(インデックス設計・成長率・記録失敗の扱い・OTEL計装範囲・503フォールバックなし)を、具体的な実装アーキテクチャに落とし込むための質問。

## Q1: イベントリスナーの実行方式と例外の遮断

BR7.7(記録失敗時は発行元の処理へ一切影響を与えない)を実現する具体的な実装方式はどうしますか。Springの`@EventListener`は既定では発行元と同一スレッド・同期実行であり、リスナー内で捕捉されない例外は発行元の呼び出しスタックへ伝播しうる点に注意が必要です。

- A. 同期の`@EventListener`とし、リスナーメソッド内でイベントのマッピング処理(BR7.2〜BR7.4)からAuditLogEntryのDB書き込みまでの全体を`try-catch`で囲み、いかなる例外も発行元へ伝播させない(捕捉した例外はBR7.7の構造化ログへ記録する)。同期実行のシンプルさを優先し、`@Async`は導入しない
- B. `@Async @EventListener`とし、専用のスレッドプールで非同期実行することで、発行元のスレッドとは構造的に分離する。例外は非同期実行スレッド内で発生するため発行元へは伝播しない
- X. Other (please specify)

[Answer]: A

## Q2: インデックスの定義方式

NFR1.2(occurredAt降順インデックス+targetType複合インデックスの必須化)を、どの方式で実装しますか。

- A. スキーマ移行ツール(Flyway等、具体的な選定はCode Generationで確定)のバージョン管理された移行スクリプトで、明示的にインデックスを定義する
- B. JPAエンティティの`@Index`アノテーションに任せ、Hibernateのスキーマ自動生成(DDL auto)でインデックスを作成させる
- X. Other (please specify)

[Answer]: A

## Q3: ページングの実装方式(オフセット vs キーセット)

C6契約は`page`(整数、1始まり)をオフセット相当のパラメータとして定義しています。テーブルが無期限に増加する(NFR3.2)前提で、ページング実装をどうしますか。

- A. C6契約の`page`パラメータをそのままオフセットとして扱い、単純な`LIMIT`/`OFFSET`によるページング実装とする。深いページ番号(大きいoffset)ではやや性能が劣化しうるが、NFR3.3の再検討トリガー(8万行到達)に達するまでの規模では許容範囲と判断する
- B. 外部契約上の`page`番号は維持しつつ、内部実装ではキーセット(occurredAt + auditLogEntryIdによるカーソル)方式に変換し、大規模データでも安定した性能を確保する
- X. Other (please specify)

[Answer]: A

## Q4: クエリパラメータの入力検証(セキュリティ)

`GET /api/audit-log`の`page`・`pageSize`・`targetType`クエリパラメータの検証方針はどうしますか。

- A. `page`は1以上、`pageSize`は許容範囲(例: 1〜100、既定20)、`targetType`はfunctional-design entities.mdが定義する既知の値集合(`ConfigEngine`/`PermissionEngine`/`DataImportExport`)のいずれかであることを検証し、範囲外・不正な値は400 Bad Request(RFC 9457)で拒否する
- B. 範囲外の値は例外とせず、既定値へ暗黙的に丸める(サイレントクランプ)
- X. Other (please specify)

[Answer]: A

## Q5: ログの相関ID伝播

NFR5.1(HTTPエンドポイントのOTEL計装)に関連し、`GET /api/audit-log`呼び出しやBR7.7の失敗時ログにおける相関ID(トレースID)の伝播方式はどうしますか。

- A. 呼び出し元・OTEL計装基盤(具体的なライブラリ選定はCode Generation/CI Pipelineで確定)が管理するトレースコンテキストにそのまま乗せる。audit-logging自身が独自の相関ID生成・伝播の仕組みを持つ必要はない(他ユニットと同一方針)
- X. Other (please specify)

[Answer]: A

## Q6: 論理コンポーネント境界(Infrastructure Designへの橋渡し)

本プロジェクトは単一の実行可能WAR(単一JVMプロセス)として動作し、AWS等のクラウドインフラは対象外(Operationフェーズの全ステージはSKIP、team.md確定事項)です。`logical-components.md`が扱う「サービス境界・障害ドメイン・影響範囲(blast radius)」をどう位置づけますか。

- A. audit-loggingは単一WARプロセス内の論理モジュールであり、独立したデプロイ単位ではない。障害ドメインは自身のDB書き込み処理(内部設定DB、embedded)に限定され、Q1の例外遮断設計によりJVMプロセス全体には波及しない。クラウドインフラ・Infrastructure Design(3.4)は本プロジェクトの対象外であるため、logical-components.mdはプロセス内の論理的な責務分離・障害ドメインの説明に留め、AWSサービスの選定は行わない
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
