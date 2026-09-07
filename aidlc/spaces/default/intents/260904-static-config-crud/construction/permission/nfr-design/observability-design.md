# Observability Design: permission

## 構造化ログ

内部呼び出しAPI(契約#3・#20・#21・#22)経由の権限確認自体は高頻度なプロセス内呼び出しであり、個別の監査ログ・アクセスログ出力対象としない。例外発生時(reliability-design.md参照)はSpring標準の例外ログ(ERRORレベル、スタックトレース含む)がそのまま出力される。

拒否(403)判定が発生した場合、Spring標準の例外ログとは別に、判定理由(テーブル単位のデフォルト拒否か、明示的な権限不足か)を区別できるDEBUGレベルの構造化ログを出力する(nfr-requirements/observability-requirements.md「権限判定の可観測性」節)。既定運用ではDEBUGレベルは無効化されており、通常運用時のログ量増加を避けつつ、調査時にログレベルを一時的に引き上げることで判定理由を追跡できる設計とする。

## 監査ログ対象範囲

管理系エンドポイント経由のCRUD操作(ロール・グループの作成/変更/削除、権限設定、ロール割当)は、契約summary.mdの監査ログイベント契約(#5〜#8)における発行元(publishers)一覧`[config-management, dynamic-data-access, auth, account-management]`にpermissionが含まれていないため、監査ログイベント(AuditableActionOccurredEvent)の発行対象としない。permission用の`actionType`語彙も同契約には定義されていない。これはfunctional-design段階で確定済みの範囲(permission自身のBR定義に監査ログ発行〈BR8.1相当〉が含まれていない)と一致する。将来的にpermissionを発行元に追加する場合は、audit-log側の合意(共有契約の所有権例外)を得たうえで、Contract Designの変更としてcontract-summary.mdを先に更新する必要がある。

## メトリクス・分散トレーシング

Spring Boot Actuator + Micrometerの標準メトリクス、およびSpring Boot標準のOpenTelemetry自動計装(NFR3)に委ね、本Unit固有の追加実装は行わない。

## アラート・ダッシュボード

自宅サーバ1台での個人利用のため、専用のアラートルール・ダッシュボードは設計しない。
