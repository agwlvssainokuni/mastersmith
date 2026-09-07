# Scalability Requirements: permission

## NFR2.1: 単一インスタンス・単一業務前提

requirements.md NFR2を踏襲する。1インスタンス=1業務であり、マルチテナント運用は行わない。

## NFR2.2: レコード数の想定規模

Role・Group・RoleAssignment・TablePermission・ColumnPermissionのレコード数は、業務のテーブル数・カラム数・ロール数に比例して増加するが、想定運用規模(個人利用中心の単一業務)ではデータ量・アクセス量ともに問題にならない。追加のスケーリング機構(シャーディング等)は不要と判断する。
