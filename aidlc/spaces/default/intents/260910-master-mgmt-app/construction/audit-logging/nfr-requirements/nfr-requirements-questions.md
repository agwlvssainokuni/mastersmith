# NFR Requirements Questions — audit-logging (U7)

`inception/requirements-analysis/requirements.md`のNFR1〜NFR8、および`construction/audit-logging/functional-design/`(entities.md/rules.md/functional-spec.md)に基づき、audit-loggingの非機能要件を定量化するための質問。audit-loggingは無期限保持(FR8.3、削除・アーカイブ機能なし)であるため、テーブルが永続的に増加し続ける前提での容量計画・応答時間維持が主な論点になる。

## Q1: 監査ログ閲覧API(GET /api/audit-log)のインデックス設計方針

NFR1は画面全体で応答時間3秒以内(95パーセンタイル)としているが、AuditLogEntryは無期限保持(FR8.3)のため件数が増加し続ける。既定の並び順(rules.md BR7.9: occurredAt降順)・targetTypeフィルタでの応答時間をどう維持しますか。

- A. `occurredAt`への降順インデックス、および`targetType`との複合インデックスを必須とし、ページング検索の応答時間がテーブル全体の件数に比例して劣化しないよう設計する。NFR1の3秒/p95予算をそのまま適用する
- B. 特別なインデックス設計は行わず、テーブルスキャンに依存する(MVP規模の運用期間内であれば許容範囲と判断する)
- X. Other (please specify)

[Answer]: A

## Q2: 監査ログテーブルの想定規模・成長率(容量計画の根拠)

NFR3は利用規模を数十名程度としているが、AuditLogEntryは無期限保持のため増加し続ける。本Boltが購読する3イベント(ConfigChangedEvent・PermissionChangedEvent・ImportExecutedEvent)由来の記録量として、どの程度の成長率を想定しますか。

- A. 数十名の業務担当者による日常的な設定変更・権限変更・CSVインポート操作を前提に、年間数万〜数十万件程度の増加を想定する。将来record-edit-engine(業務データの作成・更新・削除)が本ユニットへの発行を開始すると増加率は大きく上がる見込みだが、それは当該ユニットのBolt側で扱う将来課題であり、本Boltの容量計画の前提には含めない
- B. より大規模(年間数百万件以上)を想定し、テーブルパーティショニング等の追加戦略を今から検討する
- X. Other (please specify)

[Answer]: A

## Q3: 記録失敗(rules.md BR7.7)の可観測性

イベント購読後の内部設定DBへの書き込みが失敗した場合、BR7.7により構造化ログ(ERRORレベル)へ記録するのみとしているが、この失敗自体を監視可能にする必要がありますか。

- A. 監査記録の書き込み失敗をメトリクス(カウンタ)として記録し、一定頻度以上発生した場合にアラート可能にする(記録漏れの継続的発生を検知する)
- B. 通常の構造化ログ記録のみとし、専用のメトリクス・アラートは設けない(MVPスコープでは過剰と判断する)
- X. Other (please specify)

[Answer]: B

## Q4: OTELトレース/メトリクスの対象範囲(FR14.1、NFR5)

audit-loggingにおけるOTEL計装の対象範囲はどこまでとしますか。

- A. `GET /api/audit-log`のHTTPリクエストレイテンシ・エラー率に加え、イベント受信からAuditLogEntry書き込み完了までの内部処理時間もメトリクスとして計装する
- B. 他の実装済みユニット(config-engine・permission-engine・data-import-export)と同水準で、HTTPエンドポイントのレイテンシ・エラー率のみを計装し、イベント購読の内部処理時間は対象外とする
- X. Other (please specify)

[Answer]: B

## Q5: BR7.7のエラーログに含める情報の範囲(セキュリティ)

BR7.7の失敗時ログには、診断のため購読したイベントの内容(target・actor・occurredAt等)を含めたいが、これらの値に機微情報が含まれる可能性はありますか。

- A. 現行3イベントのactor値(システム識別子・activeRoleId・ユーザーID)・target値(スキーマ/テーブル名相当の文字列・ロールID・テーブルID)はいずれも認証情報・個人情報を含まないため、失敗時ログには購読したイベントの内容をそのまま含めてよい(project.md Mandatedが禁じるのはパスワード等の認証情報の平文出力であり、本ケースには該当しない)
- B. 念のためactor値相当のフィールドはログから除外し、イベント種別・occurredAtのみを記録する
- X. Other (please specify)

[Answer]: A

## Q6: 内部設定DB接続断時の閲覧API(GET /api/audit-log)の挙動

監査ログ閲覧APIの呼び出し時に、内部設定DBが利用不可の場合、どう扱いますか。

- A. 503 Service Unavailable(RFC 9457形式のProblemDetails)を返す。専用のフォールバック(キャッシュ等)は設けず、他の内部設定DB依存エンドポイントと同様の一般的な障害処理に従う
- B. 直近にキャッシュした結果を返す等、専用のフォールバックを設ける
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
