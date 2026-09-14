# Reliability Requirements — permission-engine (U3)

## NFR4.1: データ整合性(RBAC設定の更新)

`assignPermission`によるPrimaryPermission/AuxiliaryPermissionの更新はupsert単位で内部設定DBのトランザクション内で行い、部分的な書き込み(片方のフィールドだけ反映される等)を発生させない。

**R-07との関係(既知の未解消事項、レビュー指摘R-01対応)**: `security-requirements.md` NFR2.2が記載する既知の未解消事項(R-07: 複数エントリからなる初回RBACインポートでブートストラップ状態が1件目のコミット直後に終了し、2件目以降が再び昇格チェックにかかりうる)の是正候補は、トランザクション粒度を本項の「upsert単位」から「インポート実行単位(バッチ)」へ広げる変更を伴う可能性がある。その場合、本項(NFR4.1)は次回のFunctional Design/NFR Requirements見直し時に、バッチ単位のトランザクション境界へ改訂される想定であることをここに明記する。現時点の「upsert単位」記述は、R-07修正前の現行設計を追認するものであり、R-07修正の設計自由度を制約する意図はない。

## NFR4.2: 監査ログイベントの配信信頼性(Q5由来)

PermissionChangedイベントの発行は、他ユニット(config-engine等)と同じfire-and-forget方式を踏襲する(Q5=A)。イベント配信の信頼性保証(再送・At-Least-Once保証等)の詳細化はNFR設計(3.3)に委ねる(`project.md`の既存学習事項と整合)。

## NFR4.3: 楽観ロックの非対象

permission-engine自身のエンティティ(Role/PrimaryPermission/AuxiliaryPermission/Group関連)には、config-engineのような楽観ロック対象列の概念はない。同時更新は稀(管理者によるRBAC設定インポートのみ)であり、後勝ち(last-write-wins)を許容する。

## NFR4.4: 可用性

permission-engineは他の全ユニットから同期的に呼び出される中核コンポーネントであるため、アプリケーション本体(単一WAR)の可用性にそのまま従属する。ユニット単体での独立した可用性目標(SLA/SLO)は設定せず、アプリケーション全体の可用性目標に含める(運用フェーズが本MVPスコープ外のため、具体的な数値目標は設定しない)。

## NFR4.5: バックアップ・リカバリ

RBAC設定データは内部設定DB(組込みDB、業務データ用RDBMSとは別接続)に保持される。バックアップ・リカバリ方針は内部設定DB全体(config-engine/user-management/audit-logging等と共通)のものに従い、本ユニット固有の追加要件はない。
