# Observability Design — permission-engine (U3)

## メトリクス実装

- `permission_escalation_denied_total`(Micrometerカウンタ、OTEL経由でエクスポート): `PermissionEscalationException`発生時にインクリメント。ラベルなし(個人特定情報を含まない)。
- `permission_check_duration_seconds`(Micrometerタイマー/ヒストグラム): `resolveEffectivePermission`呼び出しをラップして計測。
- `permission_cache_hit_ratio`: Caffeineの組み込み統計機能(`CacheStats`)をMicrometer経由で公開する(Caffeineは`recordStats()`有効化でヒット率等を標準提供)。

## ログ実装

- 構造化ログ(JSON)出力はSpring Boot標準のロギング基盤(Logback + JSON encoder)を用いる。
- `PermissionEscalationException`発生時: 対象ロールID・スコープ種別・要求レベルをログフィールドとして記録。
- `assignPermission`成功時: 変更内容(ロールID・スコープ種別・スコープ参照・変更前後のレベル)を記録。

## 分散トレーシング(Q6由来)

permission-engine自身は新たな相関ID(トレースID)を生成・伝播する仕組みを持たない。呼び出し元から伝播されるOTELトレースコンテキスト(Spring Boot 3.x + Micrometer Tracingが提供するコンテキスト伝播、同一プロセス内呼び出しのためスレッドコンテキストで自動伝播)にそのまま乗る。`resolveEffectivePermission`等の呼び出しは、Micrometer Observation APIでスパンとして自動計装する(明示的なトレーシングコードの追加は最小限に抑える)。

## アラートルール(暫定、CI Pipeline/運用設計で最終確定)

- `permission_escalation_denied_total`の急増(例: 1分間に10件以上)をアラート候補とするが、**既知の相互作用(R-07)**により初回導入直後の複数エントリRBACインポートがこの閾値を誤って超える可能性がある。運用手順として、初回導入直後の一定時間はアラート評価を抑制する、または別ラベルで区別するかは、Code Generation/CI Pipeline時に具体化する。

## ダッシュボード

`permission_check_duration_seconds`(p95がNFR1.1の50ms目標を満たしているか)・`permission_escalation_denied_total`・`permission_cache_hit_ratio`を、運用フェーズ(本MVPスコープ外)のダッシュボード候補指標として記録する。
