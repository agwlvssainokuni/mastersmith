# Performance Requirements — audit-logging (U7)

## NFR1.1: 監査ログ閲覧API(`GET /api/audit-log`)の応答時間

NFR1(画面全体の応答時間3秒以内、95パーセンタイル、最大同時アクセス50ユーザー)をそのまま適用する。監査ログ閲覧画面は他の一覧画面と異なり列単位の権限判定を行わない(BR7.10でscreenKey単位の1回のみ判定)ため、`GET /api/audit-log`単体の応答時間予算は画面全体予算とほぼ等しく、**3秒以内(95パーセンタイル)** を目標とする。

## NFR1.2: インデックス設計による応答時間の維持(Q1=A)

AuditLogEntryは無期限保持(FR8.3)のため件数が増加し続ける。件数増加によってNFR1.1の応答時間目標が劣化しないよう、以下のインデックスを必須とする。

- `occurredAt`への降順インデックス(rules.md BR7.9の既定並び順に対応)
- `targetType`との複合インデックス(`targetType`でのフィルタ、rules.md BR7.9)

これにより、ページング検索(`page`・`pageSize`)の応答時間はテーブル全体の件数に比例して劣化せず、インデックスを用いた範囲スキャンで完結する設計とする。

## NFR1.3: イベント購読処理(BR7.2〜BR7.4)の処理時間

イベント購読からAuditLogEntry書き込みまでの内部処理は、発行元(config-engine・permission-engine・data-import-export)からfire-and-forgetで発行される(BR7.7)ため、発行元の応答時間には影響しない。本ユニット側の処理時間について個別の数値目標は設けない(N/A、Q4=Bによりメトリクスとしても計装対象外とする)。
