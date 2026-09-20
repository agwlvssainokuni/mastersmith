# Observability Design — user-management (U4)

`nfr-requirements/observability-requirements.md`の各要件を、設計に落とし込む。要件のNFR5(HTTPリクエストのレイテンシ・エラー率、ヘルスチェック、構造化ログ、OTELへのエクスポート)は、アプリケーション全体の共通基盤(Spring Bootのメトリクスと観測、OTELへのエクスポート)に従い、以下は本ユニット固有の設計である。

## NFR5.1: メトリクス

メトリクスの記録には、Micrometerを用いる(Spring Bootの標準。OTELへのエクスポートは、共通基盤に従う)。**名前の正は、Micrometerのドット区切りの名前(下表の左側)とする**。括弧内の名前は、要件が付けたPrometheus形式の名前(カウンタの`_total`・タイマーの`_seconds`を付けた形)であり、エクスポートの形式がPrometheusの場合の名前にあたる。OTLPで出力する場合は、ドット区切りのまま(`_total`は付かない)になる。エクスポートの形式(レジストリ)は、共通基盤の設計(CI Pipeline)で確定するため、アラートの条件は、使う形式の名前で書く。本設計でのアラートの暫定値は、Micrometerの名前で記述する。

| メトリクス | 種類 | 対象 | 出典 |
|---|---|---|---|
| `user.invitation.mail.failed`(`user_invitation_mail_failed_total`) | カウンタ | 招待メール送信の失敗(タイムアウト・接続エラー・件名の拒否) | Q5=A、BR4.16 |
| `user.invitation.accept.not_found`(`user_invitation_accept_not_found_total`) | カウンタ | 招待受諾で404を返した回数 | Q5=A、BR4.2 |
| `user.role.escalation.denied`(`user_role_escalation_denied_total`) | カウンタ | 自分自身のroleIds変更の拒否 | Q5=A、BR4.12 |
| `user.password.hash.duration`(`user_password_hash_duration_seconds`) | タイマー(ヒストグラム) | ハッシュ計算・検証の所要時間(NFR1.2の確認用) | [assumption] |
| `user.password.hash.rejected`(`user_password_hash_rejected_total`) | カウンタ | 同時計算の待機2秒超で503とした回数(NFR1.3) | [assumption] |
| `user.invitation.subject.rejected`(`user_invitation_subject_rejected_total`) | カウンタ | テンプレート起因で、件名を拒否した回数(改行・200文字超・`<title>`の欠落)。SMTPの不調のカウンタとは、別に数える | [assumption] |
| `user.event.publish.failed`(`user_event_publish_failed_total`) | カウンタ | コミット後のUserChangedEventの発行に失敗した回数(reliability-design.md NFR4.3) | [assumption] |

- **ラベルは付けない**(メールアドレス・氏名・招待トークン・userIdを含めない)。件数の内訳が必要になった場合は、ログで確認する。
- HTTPサーバーのメトリクスのURIラベルは、招待受諾APIについて、ルートのテンプレート(`/api/users/invitations/{token}/accept`)にする(security-design.md NFR2.10)。

**アラートの暫定値**(運用フェーズが本MVPスコープ外のため、暫定値として置く。運用する時点で、運用の担当者が実測に基づいて見直す):

| アラート | 条件 | 意味 |
|---|---|---|
| 招待メール送信の失敗 | 5分間に3回以上 | SMTPの不調 |
| 招待受諾の404 | 5分間に10回以上 | トークンの推測、または誤ったリンクの多発 |
| 権限昇格防止による拒否 | 5分間に3回以上 | 不正な操作、または利用者の誤操作 |

## NFR5.2: ログ

- **形式**: 構造化ログ(JSON)。すべてのログに、リクエストIDと、操作した管理者のuserId(取得できる場合)を、MDC(ログの文脈情報)として付ける。
- **禁止**: パスワード・招待トークン・passwordHash・メールアドレス・氏名・件名の実際の値は、ログに出さない(userIdを用いる)。
- **出す内容**:
  - 招待メール送信の失敗: 操作した管理者のuserId、リクエストID、失敗の分類。招待の対象のuserIdは、巻き戻されるため相関に使わない。
  - 権限昇格の防止による拒否: 操作者のuserIdと、対象のuserId。
  - ログイン成功時のハッシュ更新の失敗: userIdのみ(警告)。
  - 初期管理者の自動作成: 「作成した」「既に存在したため何もしなかった」の事実のみ。
- **リクエストログ**: パスの記録には、マッチしたルートのテンプレートを使う(security-design.md NFR2.10)。

## NFR5.3: 分散トレーシング

- 記録するスパン: `/api/users`系の各エンドポイント、C11の各メソッド、招待メール送信、ハッシュ計算(`PasswordHasher`)。呼び出し元からのトレースコンテキストを継承する。
- **招待受諾APIのスパン**: スパン名と、URLのパス・全体を表す属性に、ルートのテンプレートを入れる(Spring Bootの観測の規約`ServerRequestObservationConvention`を差し替える)。トークンの実際の値を含めない。
- **招待メール送信のスパン**: メールアドレス・氏名・件名の実際の値を、属性に含めない。

## NFR5.4: ヘルスチェック

user-management専用のヘルスチェックの部品は、設けない。内部設定DBへの接続の確認は、共通基盤が提供する、データソースのヘルスチェック(Spring Boot Actuatorの`DataSourceHealthIndicator`)が、アプリケーション全体のヘルスチェックの一部として行い、user-managementはその結果に含まれる。SMTPの疎通は、健全性の判定に含めない(SMTPの不調でアプリ全体をunhealthyにしないため)。SMTPの不調は、`user.invitation.mail.failed`で検知する。

## NFR5.5: ダッシュボード

NFR5.1のメトリクスを、運用フェーズ(本MVPスコープ外)で構築されるダッシュボードの候補指標として記録する。本ステージでは、具体的なダッシュボードの設計は行わない。

## 相関IDの伝播

リクエストIDは、共通基盤が生成し、MDCとトレースのコンテキストに乗せる。user-managementは、独自の相関IDの仕組みを持たない。招待メール送信は専用のスレッドプールで実行されるため、MDCとトレースのコンテキストを、送信のタスクへ引き継ぐ(実装はCode Generation)。

## 根拠

- 要件: `nfr-requirements/observability-requirements.md`(NFR5.1〜NFR5.5)、`inception/requirements-analysis/requirements.md`のNFR5・FR14
- 機能設計: `functional-design/functional-spec.md`、`rules.md`(BR4.2・BR4.12・BR4.16)
- 契約: `inception/contract-design/contract-summary.md`のC5・C11
