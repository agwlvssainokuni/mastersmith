# Scalability Requirements: schema-ingestion

## NFR2.1: 単一インスタンス・単一業務前提

requirements.md NFR2を踏襲する。1インスタンス=1業務であり、マルチテナント運用は行わない。

## NFR2.2: 走査の対象範囲

複数のDbConnectionを扱う場合でも、走査は都度1接続・1スキーマ/データベースに対して実行される設計であり(BR4.1)、追加のスケーリング機構は不要と判断する。
