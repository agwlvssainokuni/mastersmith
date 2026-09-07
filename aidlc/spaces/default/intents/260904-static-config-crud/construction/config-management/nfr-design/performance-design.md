# Performance Design: config-management

## 応答速度の一般方針

requirements.md NFR1(厳密な数値目標なし)を踏襲する。

## キャッシュアーキテクチャ

サービスコンポーネントは、DbConnection・TableConfig・MenuItemの読み取りをCaffeineインメモリキャッシュ経由で行う(BR6.1、NFR1.2)。TTL(expireAfterWrite)はapplication.ymlで設定可能とし、未設定の場合は無期限(明示的クリアのみが更新契機)とする。契約#2(dynamic-data-access → config-management)経由の参照はキャッシュヒット時のみキャッシュから返し、ミス時のみリポジトリへ問い合わせてキャッシュに格納する。

## キャッシュ更新契機

設定の作成・更新・削除・インポートの都度、リポジトリへの書き込みに続けて該当キャッシュエントリを明示的に無効化する(BR6.2)。インポート成功時はキャッシュ全体をクリアする(BR5.3)。POST /api/admin/config/cache/clearによる手動クリアも提供する。
