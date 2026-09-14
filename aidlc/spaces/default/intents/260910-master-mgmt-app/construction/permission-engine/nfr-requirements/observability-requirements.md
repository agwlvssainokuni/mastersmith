# Observability Requirements — permission-engine (U3)

## NFR5.1: メトリクス

- `permission_escalation_denied_total`(カウンタ): `PermissionEscalationException`の発生回数(Q4=A)。ラベル: 該当なし(個人を特定しうる情報は含めない)。異常な頻発(短時間での連続発生等)を検知するアラートルールの具体的な閾値はCI Pipeline/運用設計時に確定する。

  **既知の相互作用(レビュー指摘R-02対応)**: `security-requirements.md` NFR2.2が記載するR-07(未解消)が発生している間は、複数エントリからなる正規の初回RBACインポート自体が本カウンタのバースト(連続発生)を引き起こし、不正な昇格試行の検知シグナルと区別できない。R-07が解消するまでの暫定運用として、(a) 初回導入直後の一定時間はアラート評価を抑制する、または(b) 本カウンタとは別に「ブートストラップ期間中の発生」を識別できるラベル・別カウンタを設ける、のいずれかの回避策を運用手順またはCode Generationで具体化する。アラート閾値をバースト許容のため緩めることは、本来の攻撃検知感度を下げるため避ける。
- `permission_check_duration_seconds`(ヒストグラム): `resolveEffectivePermission`の応答時間分布。NFR1.1(50ms以内、95パーセンタイル)の充足状況をモニタリングする。
- `permission_cache_hit_ratio`(ゲージまたはカウンタ比): Q2のTTLキャッシュのヒット率。キャッシュ効果の可視化用。

## NFR5.2: ログ

- `PermissionEscalationException`発生時は、対象ロール・スコープ種別・要求レベルを構造化ログ(JSON)に記録する。個人を特定しうる値(パスワード等)は記録しない(project.md Mandated)。
- `assignPermission`成功時は、変更内容(ロール・スコープ・変更前後のレベル)を構造化ログに記録する(監査イベントとは別に、運用トラブルシュート用途)。

## NFR5.3: 分散トレーシング

NFR5(OTEL基盤へのエクスポート)に従い、`resolveEffectivePermission`/`canAccessScreen`/`assignPermission`の呼び出しをスパンとして記録する。呼び出し元(list-engine等)からの分散トレースコンテキストを継承する。

## NFR5.4: ヘルスチェック

permission-engineは内部設定DBへの読み取り専用ヘルスチェックに参加する(アプリケーション全体のヘルスチェックの一部。ユニット単体の独立したヘルスチェックエンドポイントは設けない)。

## NFR5.5: ダッシュボード

`permission_escalation_denied_total`・`permission_check_duration_seconds`は、運用フェーズ(本MVPスコープ外)で構築されるダッシュボードの候補指標として記録しておく。本ステージでは具体的なダッシュボード設計は行わない。
