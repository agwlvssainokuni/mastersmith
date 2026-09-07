# Observability Requirements: schema-ingestion

## NFR3.1: Twelve-Factor App準拠・構造化ログ

requirements.md NFR3を踏襲する。プロジェクト全体の横断要件であり、本Unit固有の追加要件はない。

## 接続テスト・走査失敗時のログ

接続テスト・プレビュー実行の失敗は構造化ログに記録することが望ましいが、認証情報等の機微情報はログに出力しない(security-requirements.md NFR-DATA.2参照)。専用のダッシュボード・アラート設計は、本プロジェクトの運用規模に照らして本ステージでは規定しない。
