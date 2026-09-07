# Reliability Requirements: dynamic-data-access

自宅サーバ1台構成のため、SLA/SLO数値目標(稼働率%等)は設けない(requirements.md NFR1、team.md Deployment)。

## NFR-CONSISTENCY.1: 後勝ち更新(競合制御なし)

更新画面は、対象RDBMSのバージョン列・最終更新日時列の存在を前提とした競合検出を行わず、PUT時点の主キー一致のみでUPDATE文を実行する後勝ち方式とする(BR3.3)。これは単一利用者中心・自宅サーバ1台という想定運用規模を前提とした明示的な簡素化のトレードオフであり、同一レコードへの同時編集の頻度が低いことを前提としている。

## NFR-CONSISTENCY.2: 外部キー制約違反の扱い

新規作成・更新時の外部キー制約違反はDB側の制約エラーとして検出し、400として応答する(BR3.4、アプリケーション側での事前検証は行わず対象RDBMSの制約に委ねる)。

## NFR-FAILSAFE.1: permission呼び出し失敗時の扱い

一覧・詳細・新規作成・更新のいずれの操作も契約#3(permission)への問い合わせを伴う(BR4.1)。X-Active-Roleがアクセストークンのrolesクレームに含まれない場合は403を返す。
