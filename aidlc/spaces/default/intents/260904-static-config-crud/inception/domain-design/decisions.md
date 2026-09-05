# Architecture Decision Records: MasterSmith Domain Design

## ADR-001: 7つの論理コンポーネントへの分割

### Context

requirements.mdのFR群(スキーマ取り込み・設定管理・業務データCRUD・FK参照・権限管理・認証/アカウント管理・監査ログ)を、どの粒度の建て付けに分割するかを決める必要があった。開発者一人体制であり、過度な分割は保守負荷を増やすが、静的設定駆動という設計思想の核心(「設定の保持」と「設定を使った実行」の分離)を薄めない粒度が必要だった。

### Decision

以下7つの論理コンポーネントに分割する(domain-design-questions.md Q1〜Q3、Q6の回答による): SchemaIngestionComponent、ConfigManagementComponent、DynamicDataAccessComponent、PermissionComponent、AccountComponent、NotificationComponent、AuditLogComponent。

### Consequences

**Positive**
- 変更理由・変更頻度が異なる関心事(RDBMS差異吸収 / 設定CRUD / 実行時問い合わせ / 認可 / 認証・アカウント / 通知 / 監査)を明確に分離できる。
- 静的設定駆動の核心である「設定の保持(ConfigManagement)」と「設定を使った実行(DynamicDataAccess)」の分離が、コンポーネント境界として明示される。
- 監査ログ・通知メールが横断的関心事として独立しているため、将来の再利用(他のコンポーネントからの利用)が容易。

**Negative**
- コンポーネント数が多く、開発者一人体制では境界を跨ぐたびにコンテキストスイッチのコストがかかる。
- 各コンポーネント間の契約(インタフェース)を明示的に維持する手間が増える。

**Neutral**
- Units Generation以降でのデプロイ単位(モノリス内モジュールか、複数モジュールか)は本ステージでは決めない。

### Alternatives Rejected

**Option B — 3コンポーネントへの統合(設定系・実行系・アカウント系)**
- 説明: 設定管理とスキーマ取り込みを1つに、業務データCRUD・FK参照・権限を1つに、認証・アカウント・通知を1つにまとめる。
- Pros: 初期実装の見通しが立てやすく、コンポーネント間の契約を維持する手間が減る。
- Cons: スキーマ取り込みという技術的関心事(RDBMS差異吸収)と設定CRUD画面という関心事が混在し、監査ログ・通知メールの独立した再利用性も下がる。静的設定駆動の核心である「保持と実行の分離」も見えにくくなる。

## ADR-002: 管理者ゲーティングはアクセストークンのクレームで判定する

### Context

管理者専用画面(設定管理・スキーマ取り込み・監査ログ・アカウント管理)へのアクセス制御(FR5.5/FR5.6)をどう実現するか。素朴には、各コンポーネントがAccountComponentへ「このアカウントは管理者か」を都度問い合わせる設計が考えられるが、その場合PermissionComponent・ConfigManagementComponent・AuditLogComponentがいずれもAccountComponentを呼び出す一方、AccountComponent自身のアカウント管理画面(13a/13b)も管理者専用であるため、AccountComponentが自分自身の管理者判定のために他コンポーネント経由で循環する設計になりかねない。

### Decision

ログイン時にAccountComponentが発行するアクセストークンに`isAdmin`クレームを含め、管理者専用画面を持つ各コンポーネント(ConfigManagement・SchemaIngestion経由の画面・AuditLog・AccountComponent自身)は、トークンに含まれるクレームだけで判定する(domain-design-questions.md Q4)。実行時にAccountComponentへ問い合わせるコンポーネント間呼び出しは行わない。

### Consequences

**Positive**
- コンポーネント間の循環呼び出しを避けられる。
- 判定のたびにAccountComponentへ問い合わせる必要がなく、応答が速い。
- 認可判定ロジックが、認証基盤(トークン発行・検証)という一般的な横断的関心事に自然に収まる。

**Negative**
- トークン発行後に管理者権限が変更された場合、トークンの有効期限が切れるかリフレッシュされるまで、その反映が遅延する可能性がある(Contract Design以降でトークン失効・強制再認証の要否を検討する)。

**Neutral**
- 具体的なトークン形式(JWT等)や失効の扱いは、Contract Design以降で確定する。

### Alternatives Rejected

**Option B — 都度AccountComponentへ問い合わせる**
- 説明: 各画面・各コンポーネントが操作のたびにAccountComponentへ「このアカウントは管理者か」を問い合わせる。
- Pros: 権限変更が即座に反映される。
- Cons: AccountComponent自身の管理者専用画面(アカウント管理)がAccountComponentへの問い合わせを必要とする、実質的な自己参照(または他コンポーネント経由の循環)が生じる。応答性も落ちる。

## ADR-003: アカウントライフサイクル通知はイベント駆動の疎結合連携とする

### Context

アカウント作成・登録完了・情報変更・パスワード変更・パスワード忘れ・メールアドレス変更の各契機で送信するメール通知(FR6.4、NotificationComponent)を、AccountComponentからどう呼び出すか。直接のメソッド呼び出し(同期依存)にすると、AccountComponentがNotificationComponentのインタフェースに直接依存することになる。

### Decision

AccountComponentは各契機でドメインイベント(例: アカウント作成イベント)を発行し、NotificationComponentがSpringの`ApplicationEventPublisher`/`@EventListener`機構でこれを購読する(domain-design-questions.md Q5)。AccountComponentはNotificationComponentへの直接依存(参照)を持たない。

### Consequences

**Positive**
- AccountComponentとNotificationComponentが疎結合になり、メールテンプレート・送信手段の変更がAccountComponentに影響しない。
- 将来、同じイベントを別の目的(例: 分析・監査)で購読する拡張が容易。

**Negative**
- イベントの発行と購読が非同期にすれ違う設計にした場合、メール送信の失敗がAccountComponent側から見えにくくなる可能性がある(具体的な同期/非同期の選択はContract Design以降で確定する)。

**Neutral**
- イベントの命名・ペイロード定義はContract Design以降で確定する。

### Alternatives Rejected

**Option B — AccountComponentがNotificationComponentを直接呼び出す**
- 説明: メール送信が必要な箇所でNotificationComponentのインタフェースを直接呼び出す。
- Pros: 呼び出し関係が単純で追いやすい。
- Cons: AccountComponentがメール送信の実装詳細(テンプレートエンジンの種類等)に直接依存することになり、疎結合の利点(独立した変更容易性)が失われる。

## ADR-004: DynamicDataAccessComponentは永続エンティティを持たない

### Context

業務データの動的CRUD(FR3)を担うDynamicDataAccessComponentが、対象テーブルごとの型付けエンティティ(例: 書籍・会員等、業務ドメインごとのクラス)を持つべきかを検討した。

### Decision

DynamicDataAccessComponentは永続エンティティを持たない。業務データは実行時にConfigManagementComponentの設定(TableConfig)に基づいて動的に発見・操作される対象RDBMSのスキーマであり、本アプリケーションのコード資産として静的に型付けされたエンティティ・モデルクラスを持たせない。

### Consequences

**Positive**
- 「あたかも各業務向けに個別開発したかのように見える」という成功指標([intent-statement.md#Success Metrics])を、業務ドメインごとにコードを書き分けることなく実現できる。
- 新しい業務ドメイン(4つ目以降)を追加する際、設定変更のみで対応でき、コード修正・再デプロイが不要という測定可能な代理指標([requirements.md#Success Metrics])とも整合する。

**Negative**
- コンパイル時の型安全性が働かない領域が生まれる(動的SQL構成・実行時の型変換に伴うリスクはFunctional Design以降で個別に手当てする)。

**Neutral**
- ConfigManagementComponentのTableConfigエンティティは、業務データそのものではなく「業務データの構造を記述する設定」であり、両者は別物である。

### Alternatives Rejected

**Option B — テーブルごとに型付けエンティティを生成する**
- 説明: スキーマ取り込み時にテーブルごとのエンティティクラスをコード生成する。
- Pros: コンパイル時の型安全性が得られる。
- Cons: 新しい業務ドメイン追加のたびにコード生成・再デプロイが必要になり、「設定変更のみで対応できる」という本プロジェクトの核心的な成功指標と矛盾する。
