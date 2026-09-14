# Logical Components — permission-engine (U3)

## コンポーネント一覧

| コンポーネント | 責務 | 障害ドメイン |
|---|---|---|
| `PermissionEngineApi`実装(C10提供側) | `resolveEffectivePermission`/`canAccessScreen`/`assignPermission`の公開窓口 | アプリケーション本体プロセスと同一(埋め込み) |
| スコープ階層解決ロジック | BR3.4/BR3.5のカラム→テーブル→スキーマ探索 | 同上 |
| ブートストラップ判定ロジック | BR3.13、PrimaryPermission行数=0の判定 | 同上 |
| 権限昇格チェックロジック | BR3.8、操作者の実効権限との比較 | 同上 |
| Caffeineキャッシュ層 | resolveEffectivePermission結果のキャッシュ・無効化 | プロセスローカル(インスタンス単位) |
| RBACデータアクセス層(Repository) | Role/PrimaryPermission/AuxiliaryPermission/Group/GroupMembership/GroupRoleのCRUD | 内部設定DB(組込みDB) |
| PermissionChangedイベント発行 | Springアプリケーションイベントのfire-and-forget発行 | 同一プロセス内(疎結合) |

## サービス境界

permission-engineは単一プロセス(単一WAR)内に組み込まれるサービスであり、独立したデプロイ単位・独立したネットワーク境界を持たない。全コンシューマー(user-management, menu-navigation, audit-logging, list-engine, record-edit-engine, config-import-export)からの呼び出しは同一JVM内のメソッド呼び出しである。

## 障害ドメイン・被害範囲(blast radius)

- **内部設定DB障害**: config-engine/user-management/audit-logging等、内部設定DBを共有する全ユニットに影響が及ぶ(permission-engine固有の障害ドメインではない)。
- **Caffeineキャッシュのメモリ使用**: プロセス全体のヒープメモリを共有するため、キャッシュサイズ上限(scalability-design.md参照)を超えないよう設計する。
- **`PermissionEscalationException`の頻発**: audit-loggingへのイベント発行やメトリクス記録には影響するが、他ユニットの機能自体をブロックしない(拒否は当該操作のみに限定される)。

## 共有リソース

- 内部設定DB(組込みDB、業務データ用RDBMSとは別接続)を、config-engine/user-management/audit-logging/menu-navigation/config-import-exportと共有する。
- Caffeineキャッシュ・Micrometerメトリクスレジストリはアプリケーション本体のプロセス内リソースを共有する(専有リソースの追加確保は不要)。
