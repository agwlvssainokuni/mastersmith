# Observability Requirements — user-management (U4)

要件のNFR5に従い、HTTPリクエストのレイテンシ・エラー率、ヘルスチェック、構造化ログ(JSON、リクエストID付与)をOTEL基盤へエクスポートできること。本ユニットにもそのまま適用し、以下は本ユニット固有の追加事項である。

## NFR5.1: メトリクス

Q5=Aにより、次の3つをカウンタとして記録し、OTELへエクスポートする。異常な頻発時にアラートを出せるようにする。

アラートの閾値は、次の暫定値を置く([assumption]。運用フェーズが本MVPスコープ外で、確定する担当ステージがないため、暫定値として置く)。実際に運用する時点で、運用の担当者が実測に基づいて見直す。

- 招待メール送信の失敗: 5分間に3回以上
- 招待受諾の404: 5分間に10回以上
- 権限昇格防止による拒否: 5分間に3回以上

- `user_invitation_mail_failed_total`(カウンタ): 招待メール送信の失敗回数(SMTPの不調のサイン、rules.md BR4.16)。
- `user_invitation_accept_not_found_total`(カウンタ): 招待受諾で「未知・使用済み・取消済みのトークン」として404を返した回数(トークンの推測や、誤ったリンクの多発のサイン、BR4.2)。
- `user_role_escalation_denied_total`(カウンタ): 権限昇格の防止(自分自身のroleIds変更)により拒否した回数(BR4.12)。

いずれも、ラベルに個人を特定しうる値(メールアドレス・氏名・招待トークン)を含めない。HTTPサーバーのメトリクスのURIラベルは、招待受諾APIについてルートのテンプレート(`/api/users/invitations/{token}/accept`)を用い、トークンの実際の値を含めない(security-requirements.md NFR2.10)。

[assumption] Q5で確認した上記3つに加え、NFR1.2・NFR1.3の充足を確認するための派生指標として、次の2つを設ける。要件に明示がないため、必要ならNFR Designで見直す。

- `user_password_hash_duration_seconds`(ヒストグラム): ハッシュ計算・検証の所要時間(NFR1.2の「p95で300ms以下」の目標の確認用)。
- `user_password_hash_rejected_total`(カウンタ): 同時実行の上限により、2秒待っても順番が来ず503とした回数(NFR1.3)。

## NFR5.2: ログ

- 招待メール送信の失敗時は、操作した管理者のuserId・リクエストID・失敗の分類(タイムアウト・接続エラー・件名の拒否等)を構造化ログ(JSON)に記録する。招待の対象のuserIdは、失敗時にUserが巻き戻される(rules.md BR4.16)ため、相関の手がかりに使わない。メールアドレス・氏名・招待トークン・パスワードは記録しない(NFR2.2・NFR2.6、project.md Mandated)。
- 権限昇格の防止による拒否時は、操作者のuserIdと対象のuserIdを構造化ログに記録する。
- 初期管理者の自動作成(W5)は、作成した場合と、既に存在するため何もしなかった場合を、それぞれ構造化ログに記録する(パスワードは記録しない)。

## NFR5.3: 分散トレーシング

NFR5(OTEL基盤へのエクスポート)に従い、`/api/users`系の各エンドポイントと、C11の各メソッド、招待メール送信を、スパンとして記録する。呼び出し元からの分散トレースコンテキストを継承する。

招待受諾API(パスに招待トークンを含む)のスパンは、スパン名とスパン属性(URLのパスや全体を表す属性など)にルートのテンプレート(`/api/users/invitations/{token}/accept`)を用い、トークンの実際の値を含めない(security-requirements.md NFR2.10)。招待メール送信のスパンには、メールアドレス・氏名・件名の実際の値を含めない。

## NFR5.4: ヘルスチェック

user-managementは、内部設定DBへの読み取り専用ヘルスチェックに参加する(アプリケーション全体のヘルスチェックの一部。ユニット単体の独立したヘルスチェックエンドポイントは設けない)。SMTPサーバーの疎通は、アプリケーション全体の健全性の判定に含めない(SMTPの不調でアプリ全体をunhealthyにしないため)。SMTPの不調は、NFR5.1の招待メール送信失敗のカウンタで検知する。

## NFR5.5: ダッシュボード

NFR5.1のメトリクスは、運用フェーズ(本MVPスコープ外)で構築されるダッシュボードの候補指標として記録しておく。本ステージでは具体的なダッシュボード設計は行わない。

## 根拠

- 要件: `inception/requirements-analysis/requirements.md` のNFR5(可観測性)・FR14
- 機能設計: `construction/user-management/functional-design/functional-spec.md`、`rules.md`(BR4.2・BR4.12・BR4.16)
- 契約: `inception/contract-design/contract-summary.md` のC5・C11
- 質問回答: `nfr-requirements-questions.md` Q5
