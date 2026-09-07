# Performance Requirements: config-management

## NFR1.1: 応答速度の一般方針

requirements.md NFR1を踏襲する。厳密な数値目標は設けない。著しい遅延の兆候(例: 設定取得に数秒以上かかる)が確認された場合は、別途性能検証を行う。

## NFR1.2: キャッシュ経由の読み取り

DbConnection・TableConfig・MenuItemの読み取りは常にCaffeineインメモリキャッシュ経由で行い(BR6.1)、リクエストのたびにDBへ問い合わせる実装は行わない(project.md Mandated TC-14「静的設定駆動」との両立)。契約#2(dynamic-data-access → config-management)経由の参照もキャッシュヒット時のみキャッシュから返し、ミス時のみDBへ問い合わせる。これが本Unitの主要な性能要件である。
