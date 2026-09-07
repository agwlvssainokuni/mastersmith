# Reliability Requirements: permission

自宅サーバ1台構成(team.md Deployment)のため、SLA/SLO形式の数値目標は設けない。

## NFR-FAILSAFE.1: フェイルセーフな権限判定

security-requirements.md NFR-FAILSAFE.1と同一の設計により、permission自身の障害時は呼び出し元(dynamic-data-access)側でデフォルト拒否として扱われる(BR4.1)。可用性の低下が誤って権限昇格に倒れることのない、フェイルセーフな障害時挙動とする。

## バックアップ・リカバリ

内部H2データストア全体のバックアップ方針は本Unit固有の要件ではなく、システム全体のデプロイ・運用方針(Infrastructure Design/Deployment Pipelineステージ)で確定する。
