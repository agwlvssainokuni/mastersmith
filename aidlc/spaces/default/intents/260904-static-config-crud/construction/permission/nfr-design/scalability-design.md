# Scalability Design: permission

## スケーリングアーキテクチャ

単一インスタンス構成(NFR2)。水平スケーリング・データパーティショニングは設計しない。

## レコード数の想定規模

TablePermission・ColumnPermissionのレコード数はロール数×テーブル数(ColumnPermissionはさらに×カラム数)に比例するが、自宅サーバ1台・個人利用中心という想定運用規模では、性能上の課題になることはない(performance-design.mdのインデックス設計で十分対応可能)。

## 容量閾値

明示的な容量閾値・オートスケーリングルールは設けない。
