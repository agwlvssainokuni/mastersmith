# Security Design — permission-engine (U3)

## 認証・認可アーキテクチャ(Q2由来)

permission-engineは認証(トークン検証・セッション管理)を一切行わない。呼び出し元(list-engine/record-edit-engine等)がC14(SessionContextApi、authentication-service提供)経由で取得した`activeRoleId`を引数として受け取り、これを信頼する。

**多層防御**: `activeRoleId`が実在する(削除されていない)Roleであるかどうかの存在検証のみを`resolveEffectivePermission`/`canAccessScreen`の入口で行う。存在しない場合は`level = NONE`・`canCreate = canDelete = false`を返す(BR3.6の安全側デフォルトと同じ扱い)。

**信頼境界の明示(レビュー指摘R-03対応)**: permission-engineは、渡された`activeRoleId`が呼び出し元ユーザーへ実際に割り当てられている(選択可能ロール一覧に含まれる)ことの再検証は行わない。これはC14(SessionContextApi)の契約範囲(セッションに紐づく正当な`activeRoleId`を返すこと)を信頼する設計上の前提であり、同一プロセス内呼び出し(ネットワーク境界なし)であることを根拠とする。呼び出し元(list-engine/record-edit-engine等)の実装不具合により誤った`activeRoleId`が渡された場合、permission-engine側では検出できない。この信頼境界はC14契約の保証範囲であり、C14自身の実装(authentication-service)がセッション⇔ロールの対応を正しく検証する責務を負う。

## サーバー側実効権限再検証の実装(NFR2.1)

`resolveEffectivePermission`は、entities.md/rules.mdで定義したBR3.4〜BR3.6のアルゴリズムをそのままJavaメソッドとして実装する。全コンシューマーからの呼び出しは同一プロセス内のメソッド呼び出しであり、追加のネットワーク境界・認証ヘッダーは介在しない。

## 権限昇格の防止(NFR2.2、BR3.8実装)

`assignPermission`の実装は、Step 1でブートストラップ状態(BR3.13、PrimaryPermission行数=0)を判定し、Step 2で(ブートストラップでない場合)操作者の実効権限を`resolveEffectivePermission`相当のロジックで算出し比較する2段階の実装とする。

**既知の未解消事項(R-07)**: 複数エントリからなる初回RBACインポートで、1件目のコミット直後にブートストラップ状態が終了し2件目以降が再び昇格チェックにかかりうる問題は、Functional Design段階のレビューで既知として承認ゲートに提示済みであり、本設計段階では暫定的に「upsert単位」のトランザクション粒度を維持したまま実装する(`nfr-requirements/reliability-requirements.md` NFR4.1参照)。次回のFunctional Design見直し時にバッチ単位のトランザクション境界への変更を検討する。

## キャッシュのセキュリティ設計(NFR2.8実装、レビュー指摘R-01/R-02対応)

`performance-design.md`のキャッシュ無効化設計(assignPermission成功時の`invalidateAll()`によるキャッシュ全体クリア)により、単一インスタンス構成下ではスコープ階層(BR3.4/BR3.5)を跨ぐフォールバック解決済みエントリを含め、stale-authorization windowを実質的にゼロへ近づける。TTLは多層防御としてのみ機能する(無効化ロジック自体の不具合への保険)。

**既知のセキュリティ上の制約(複数インスタンス構成、R-02対応)**: Caffeineキャッシュはインスタンスローカルであり、`invalidateAll()`は当該JVMインスタンス内のキャッシュのみに作用する。アプリケーション本体が複数インスタンスで水平スケールされる構成の場合、`assignPermission`を受け付けたインスタンス以外のインスタンスのキャッシュは無効化されず、TTL経過までの間、降格前の実効権限を返し続ける可能性がある。

この制約により、**NFR2.8(RBAC降格の即時反映)は単一インスタンス運用構成であることを前提条件とする**。複数インスタンス構成での運用が必要になった場合は、分散キャッシュ無効化機構(Pub/Sub方式のキャッシュ無効化通知等)の追加設計をNFR設計の見直し事項として要する。本MVPスコープ(想定利用規模: 数十名、NFR3)では単一インスタンス運用を前提とし、この制約を許容する。

## 暗号化(Q3由来)

RBAC設定データ(Role/PrimaryPermission/AuxiliaryPermission/Group関連)自体には認証情報・個人情報を含まないため、アプリケーション層での追加のフィールド暗号化は行わない。内部設定DB全体のディスク暗号化等のインフラ層の方針に従う(config-engine等、他ユニットと共通)。

## 入力検証・インジェクション対策(Q4由来)

- JPA/Spring Data経由のパラメータ化クエリを徹底し、`scopeRef`等の文字列を生SQL文字列結合に用いない。
- `scopeRef`のアプリケーション層検証は文字列長上限(255文字)のみとし、内容の形式検証(config-engineとの整合性チェック)は行わない(BR3.14)。
- `roleId`/`groupId`等の識別子はUUID形式等、生成時点で安全な文字集合に限定される前提とする(具体的な生成方式はCode Generationで確定)。

## 障害時のフォールバック(Q5由来)

内部設定DBへの接続障害等が発生した場合、`resolveEffectivePermission`/`canAccessScreen`/`assignPermission`は例外をそのまま呼び出し元へ伝播させる。安全側のデフォルト拒否(NONE相当)への黙示的な変換は行わない。理由: 可用性障害を権限問題と誤認させ、利用者・運用担当者のトラブルシュートを妨げるため。サーキットブレーカー等の耐障害パターンは、内部設定DBが同一プロセス内の埋め込みDBであり外部サービス呼び出しの障害モード(ネットワーク分断・タイムアウト連鎖等)を持たないため導入しない。

## 監査ログとの連携(NFR2.6)

`assignPermission`成功時、`PermissionChanged`サマリイベントをSpringのアプリケーションイベント機構(`ApplicationEventPublisher`)経由でfire-and-forget発行する(config-engineの`ConfigChangedEvent`と同じパターン)。
