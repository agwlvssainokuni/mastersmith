# Security Requirements — permission-engine (U3)

## NFR2.1: サーバー側での実効権限再検証(FR3.3由来)

`resolveEffectivePermission`は、画面表示の出し分け(クライアント側UI非表示)に依存しない、サーバー側での必須の実効権限再検証窓口である(BR3.7)。全コンシューマー(user-management, menu-navigation, audit-logging, list-engine, record-edit-engine, config-import-export)は、権限を要する操作の直前に必ず本メソッドを呼び出す。

## NFR2.2: 権限昇格の防止(FR3.4由来)

`assignPermission`は、操作者自身の現在の実効権限を上回るレベルの付与を`PermissionEscalationException`で拒否する(BR3.8)。唯一の書き込み経路はconfig-import-export(C7)であり(BR3.9)、専用のRBAC管理画面は本MVPスコープでは提供しない。

ブートストラップ状態(RBAC設定が1件も存在しない起動直後)に限り、この昇格チェックを適用しない(BR3.13)。**既知の未解消事項**: アーキテクチャレビュー(iteration 2, NOT-READY, R-07 Critical)により、複数エントリからなる初回RBACインポートの2件目以降が、1件目のコミット直後にブートストラップ状態が終了することで再び昇格チェックにかかりうる問題が指摘されている。次回のFunctional Design見直し時に、ブートストラップ判定を「インポート実行単位」で行う設計への変更を検討する(承認ゲートで人間に提示済み)。

## NFR2.3: 権限昇格試行の監視(Q4由来)

`PermissionEscalationException`の発生は、メトリクス(カウンタ)として記録し、異常な頻発時にアラート可能な形で公開する(不正な昇格試行または実装/設定ミスの検知シグナルとするため)。詳細な指標名・アラート閾値は`observability-requirements.md`参照。

**明確化(Q4 Follow-up)**: FR4.2のロール選択(利用者が自分に割り当てられた複数ロールから一つを選ぶ操作)は常に許可される操作であり、この監視対象には含まれない。監視対象はあくまで`assignPermission`呼び出し時の昇格拒否のみ。

## NFR2.4: 入力値の扱い(scopeRef、BR3.14由来)

`scopeRef`は不透明な文字列として扱い、config-engine側の実在チェックにはかけない(BR3.14)。ただし、SQLインジェクション等のシステム境界での基本的な入力サニタイズ(bind変数化)は、内部設定DBへのクエリ実装において通常のセキュアコーディング作法として適用する(construction phaseガードレール「Validate and sanitize all inputs at system boundaries」に従う)。

## NFR2.5: 認証情報の非対象

本ユニットはパスワード等の認証情報を一切保持・処理しない(user-management/authentication-serviceの責務)。該当するMandated事項(ハッシュ化保存等)は本ユニットの対象外。

## NFR2.6: 監査ログとの連携(FR8由来)

権限変更(assignPermission成功時)はPermissionChangedイベントとしてaudit-loggingへ発行する(BR3.11)。イベント自体の内容(実行者・変更件数・日時のサマリ)はproject.mdのMandated(改ざん不可・追記専用)をaudit-logging側の実装で満たす前提とし、本ユニットはイベント発行元としての責務のみを負う。

## NFR2.8: TTLキャッシュによる実効権限の一時的な不整合(降格反映遅延、レビュー指摘R-03対応)

`scalability-requirements.md` NFR3.4(Q2=B、短いTTLのプロセス内キャッシュ)は、性能・スケーラビリティ上のトレードオフとして導入するが、これは同時にセキュリティ上のトレードオフでもある。権限**降格**(例: FULL→NONE、権限管理者による緊急剥奪)が発生した場合、キャッシュのTTLが経過するまでの間、失効前の(より強い)実効権限がキャッシュから返され続ける「stale-authorization window」が生じる。

この挙動は、project.md Mandatedの「権限の判定は...必ずサーバー側で実効権限を再検証する」の趣旨(常に最新の実効権限で判定する)からは、TTL分だけ逸脱する。以下の対応を確定する。

- `assignPermission`が成功した時点で、変更対象の(scopeType, scopeRef)に関連するキャッシュエントリを能動的に無効化(invalidate)する。TTL経過待ちに依存しない即時反映をCode Generationで実装する。
- 上記の即時無効化を実装した上でも、無効化ロジック自体の不具合等に備えた保険としてTTLは維持する(多層防御)。
- TTLの具体的な秒数は、この即時無効化を前提に、性能上の利益(DB問い合わせ削減)が主目的である短い値(数秒〜数十秒)に留める(`scalability-requirements.md` NFR3.4)。

## NFR2.7: 依存関係の脆弱性スキャン・SAST(横断方針)

`team.md`の既定(SAST・シークレットスキャンをCIブロッキングチェックとして導入、依存関係脆弱性スキャンは対象外)を本ユニットにもそのまま適用する。ユニット固有の追加要件はない。
