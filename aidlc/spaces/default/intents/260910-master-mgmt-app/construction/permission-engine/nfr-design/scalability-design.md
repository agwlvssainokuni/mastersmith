# Scalability Design — permission-engine (U3)

## データパーティショニング

Role/PrimaryPermission/AuxiliaryPermission/Group関連テーブルは、想定規模(ロール数十件、設定数百〜数千行、NFR3.1)では単一テーブル・パーティショニングなしで十分。テナント分離等の水平分割は本MVPスコープでは不要。

## インデックス設計(NFR3.2実装)

- `primary_permission`テーブル: `(role_id, scope_type, scope_ref)`に一意複合インデックス
- `auxiliary_permission`テーブル: `(role_id, scope_type, scope_ref)`に一意複合インデックス
- `group_membership`テーブル: `(group_id, user_id)`に一意複合インデックス、加えて`user_id`単体にも検索用インデックス(BR3.3の「Userの所属Group一覧」検索のため)
- `group_role`テーブル: `(group_id, role_id)`に一意複合インデックス

## キャッシュ層(NFR3.4実装)

`performance-design.md`のCaffeineキャッシュがDB問い合わせの大部分を吸収する。キャッシュサイズ上限は、想定同時アクティブユーザー数(NFR1: 最大50)×想定同時アクセス列数(数十)を目安とした上限(例: 数千エントリ)を設定し、無制限成長を防ぐ(具体的な数値はCode Generationで確定)。

## 同時実行・ロード分散

`resolveEffectivePermission`はステートレスな読み取りであり、アプリケーション本体の水平スケール(複数インスタンス)にそのまま追従する。ただし、Caffeineキャッシュはインスタンスローカルであるため、複数インスタンス構成下では、あるインスタンスでの`assignPermission`成功による`invalidateAll()`が他インスタンスのキャッシュには伝播しない(インスタンスごとに独立してTTL経過を待つ)。

**この制約はスケーラビリティ上の論点であると同時にセキュリティ上の制約でもある**(詳細・運用条件は`security-design.md`のNFR2.8実装節を参照。本MVPスコープでは単一インスタンス運用を前提条件とすることでこの制約を許容する)。分散キャッシュ無効化の仕組み(Pub/Sub等)は本MVPスコープでは導入しない。

## 将来の成長への対応

想定規模を超える成長(ユーザー数・テーブル数の大幅増)が生じた場合、キャッシュTTL調整、インデックス見直し、必要に応じた分散キャッシュ無効化機構の追加で対応する(NFR3の「線形にリソースを追加」との整合)。
