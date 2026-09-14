# Reliability Design — permission-engine (U3)

## 障害伝播方針(Q5由来)

`resolveEffectivePermission`/`canAccessScreen`/`assignPermission`は、内部設定DBへのアクセス障害時に例外(未チェック例外)をそのまま呼び出し元へ伝播させる。サーキットブレーカー・リトライ等の耐障害パターンは導入しない(同一プロセス内の埋め込みDBアクセスであり、ネットワーク分断等の一過性障害モードを想定する必要がないため)。

## トランザクション境界(NFR4.1実装)

`assignPermission`によるPrimaryPermission/AuxiliaryPermissionのupsertは、Spring管理の単一トランザクション(`@Transactional`)内で行う。**既知の制約(R-07)**: 本MVPの初回実装では、config-import-exportが複数エントリを1回ずつ`assignPermission`呼び出しする方式のため、トランザクション境界は「エントリ単位(upsert単位)」のままとする。将来的にR-07(ブートストラップの再デッドロック)を修正する際は、config-import-export側でインポート実行全体を1トランザクションにまとめ、`assignPermission`をそのトランザクション内で複数回呼び出す方式への変更を検討する(`nfr-requirements/reliability-requirements.md` NFR4.1参照)。

## ヘルスチェック

permission-engineは独立したヘルスチェックエンドポイントを持たず、アプリケーション全体のヘルスチェック(内部設定DBへの疎通確認)に相乗りする。

## バックアップ・リカバリ

内部設定DB全体のバックアップ・リカバリ方針(config-engine等と共通)に従う。permission-engine固有の追加設計はない。

## データ整合性

Role削除時にPrimaryPermission/AuxiliaryPermission/GroupRoleの関連行が孤立しないよう、外部キー制約(ON DELETE CASCADE、またはアプリケーション層での関連削除)を設ける。具体的な削除APIの提供有無(現時点のC10にはRole削除メソッドは定義されていない)はCode Generation時にAPI設計として確定する。
