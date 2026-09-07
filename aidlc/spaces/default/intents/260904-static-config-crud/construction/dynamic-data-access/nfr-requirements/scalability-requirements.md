# Scalability Requirements: dynamic-data-access

## NFR2.1: 単一インスタンス・単一業務前提

requirements.md NFR2(1インスタンス=1業務、マルチテナンシーなし)を踏襲する。RecordViewが扱う業務データの件数はテーブルごとに異なるが、一覧取得のページネーション(BR1.2)により一覧応答は制限される。自宅サーバ1台・個人利用中心という想定運用規模では、業務データの総件数が性能上の課題になることはない。
