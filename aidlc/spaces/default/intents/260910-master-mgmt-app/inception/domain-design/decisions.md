# Architecture Decision Records — Domain Design(MasterSmith)

`components.md` の Rationale 表は各コンポーネントの一言サマリーであり、本ファイルは各決定の文脈・帰結・却下した代替案を含む恒久的な決定記録である。番号は連番(ADR-001〜)とする。

## ADR-001: 共通エンジン層を ListEngine と RecordEditEngine に分割する

### Status
Accepted

### Date
2026-09-13

### Context
FR5(一覧画面)・FR6(詳細・編集画面)は、いずれも「設定駆動で任意の業務テーブルに対応する」という成功定義(`intent-capture`)の中核を担う共通エンジン層である。両者を1コンポーネントにまとめるか分割するかは、今後の実装・保守のしやすさに直結する。

### Decision
一覧表示(検索・ページング・ソート)を担う `ListEngine` と、詳細・編集(フォームレンダリング・バリデーション・保存・楽観ロック)を担う `RecordEditEngine` を別コンポーネントとする(`domain-design-questions.md` Q1回答B)。

### Consequences

#### Positive
- 一覧側の検索・ソート最適化と、編集側のバリデーション・楽観ロックのロジックが互いに影響せず独立してテスト・変更できる
- 将来、一覧のみ高速化(キャッシュ導入等)する場合もRecordEditEngineに影響しない

#### Negative
- 表示設定(ConfigEngine)への依存が両コンポーネントに重複する
- 一覧から編集画面への遷移など、2コンポーネント間の連携を明示的に設計する必要がある

#### Neutral
- 将来的にバリデーション処理がさらに複雑化した場合、`ValidationEngine`への再分割(Q1 Option C)を検討する余地を残す

### Alternatives Rejected

#### Alternative 1: 単一コンポーネント `RecordEngine`(Q1 Option A)
- Description: 一覧と詳細編集を1つのコンポーネントにまとめる
- Pros: 初期実装がシンプル、コンポーネント間連携の設計が不要
- Cons: 責務が混在し、一覧固有の最適化と編集固有のバリデーション強化が今後衝突するリスクがある

#### Alternative 2: ValidationEngineへのさらなる分割(Q1 Option C)
- Description: バリデーション処理を独立コンポーネントに切り出す
- Pros: バリデーションルールの再利用性が高まる
- Cons: 現時点ではバリデーションの複雑さがそこまで高くなく、過剰な分割(premature abstraction)になるリスクがある

---

## ADR-002: 設定基盤を ConfigEngine と SchemaIntrospector に分割する

### Status
Accepted

### Date
2026-09-13

### Context
FR1(設定基盤)は、恒常的な設定の保持・検証(FR1.1〜FR1.3)と、DBメタデータからの一過性の初期ドラフト生成(FR1.4)という、実行頻度・変更理由の異なる2つの関心事を含む。

### Decision
恒常的な設定の読込・保持・DB方言吸収・fail fast検証を担う `ConfigEngine` と、DBメタデータ読み取り・初期ドラフト生成を担う `SchemaIntrospector` を別コンポーネントとする(`domain-design-questions.md` Q2回答B)。

### Consequences

#### Positive
- SchemaIntrospectorは初回セットアップ時にのみ実行される性質を持ち、ConfigEngineの恒常的な読込・検証ロジックと分離することで責務が明確になる
- SchemaIntrospectorの実装(DB方言ごとのメタデータ取得)を、ConfigEngineの本体ロジックに影響を与えずに拡張できる

#### Negative
- SchemaIntrospectorはConfigEngineへの一方向依存を持つため、初期ドラフト生成のたびにConfigEngineとの結合点(書込インタフェース)を維持する必要がある

#### Neutral
- 実装フェーズでは、同一デプロイ単位(モノリス)内の別モジュールとして実装される可能性が高い(デプロイトポロジーはUnits Generationで決定)

### Alternatives Rejected

#### Alternative 1: 単一コンポーネント `ConfigEngine`(Q2 Option A)
- Description: 設定読込・DB方言吸収・fail fast検証・初期ドラフト生成をすべて1コンポーネントに内包する
- Pros: コンポーネント数が少なく、依存関係がシンプル
- Cons: 「恒常的な設定管理」と「一過性のメタデータ読み取り」という異なる変更理由が1コンポーネントに混在する

---

## ADR-003: 権限判定(RBAC)を独立コンポーネント PermissionEngine とする

### Status
Accepted

### Date
2026-09-13

### Context
FR3.3により、権限の実効判定はクライアント側UI非表示だけに依存せず、必ずサーバー側で再検証しなければならない。この判定は ListEngine、RecordEditEngine、UserManagement、MenuNavigation、AuditLogging など、ほぼ全コンポーネントから呼び出される横断的関心事である。

### Decision
権限判定ロジックを独立した `PermissionEngine` コンポーネントとし、判定が必要な全コンポーネントがこれに依存する(`domain-design-questions.md` Q3回答A)。

### Consequences

#### Positive
- 権限モデル(ロール階層継承・主権限・補助権限)の変更が、UserManagement等の他コンポーネントの変更を伴わずに完結する
- 全コンポーネントが同一の権限判定ロジックを呼び出すため、判定ロジックの重複・不整合を防げる

#### Negative
- PermissionEngineが単一障害点(SPOF)的な性質を持つため、可用性・パフォーマンス要件(NFR1: 応答時間3秒以内)を満たす設計が今後のNFR設計で重要になる

#### Neutral
- PermissionEngineとConfigEngine(権限判定対象の階層構造を提供)の間に依存が生じるため、両者のインタフェース設計を早期に固める必要がある

### Alternatives Rejected

#### Alternative 1: UserManagementに内包(Q3 Option B)
- Description: 権限判定ロジックをUserManagementの一部として実装する
- Pros: コンポーネント数が減る
- Cons: ユーザー管理(招待・無効化等)の変更のたびに権限判定ロジックが巻き込まれる結合が生じ、FR3.3が求める「全API/ドメイン層での再検証」という横断的性質と整合しない

---

## ADR-004: 認証・セッション管理(AuthenticationService)をユーザ管理(UserManagement)から分離する

### Status
Accepted

### Date
2026-09-13

### Context
FR2(ユーザ管理: 招待・更新・無効化)は管理者操作を契機とした低〜中頻度の変更であるのに対し、FR3(認証・セッション: トークン発行・検証)はリクエストごとに実行される高頻度の処理である。両者は実行特性・非機能要件(NFR1性能要件等)の観点で異なる。

### Decision
ユーザーCRUD・招待・無効化を担う `UserManagement` と、トークン発行・検証・ログイン失敗回数追跡・アカウントロック判定を担う `AuthenticationService` を別コンポーネントとする(`domain-design-questions.md` Q4回答A)。

### Consequences

#### Positive
- AuthenticationServiceは高頻度実行パスとして独立に性能チューニング・キャッシュ戦略を適用できる
- UserManagementの変更(招待フローの変更等)がAuthenticationServiceの実行パスに影響しない

#### Negative
- AuthenticationServiceはUserManagementが保持するユーザー情報(パスワードハッシュ・ロック状態)への依存を持つため、両者間のデータ整合性(無効化時のトークン即時失効等、FR2.3)を明示的に設計する必要がある

#### Neutral
- 実装フェーズで、両コンポーネントが同一のUserエンティティ(UserManagement所有)を参照する形になる

### Alternatives Rejected

#### Alternative 1: 単一コンポーネント `UserManagement`(Q4 Option B)
- Description: 認証機能もUserManagementに内包する
- Pros: コンポーネント数が減り、ユーザー関連機能が1箇所にまとまる
- Cons: 高頻度実行される認証処理と低頻度の管理操作が混在し、将来の性能最適化(NFR1)がしづらくなる

---

## ADR-005: 監査ログ記録をイベント駆動(ドメインイベント購読)とする

### Status
Accepted

### Date
2026-09-13

### Context
FR8(監査ログ)は、RecordEditEngine・UserManagement・ConfigEngine・PermissionEngine等、記録対象の操作を行う多数のコンポーネントから記録される必要がある。この依存関係を明示的な同期呼び出し(depends_on)にするか、非同期のイベント購読にするかで、将来の拡張性・結合度が大きく変わる。

### Decision
監査ログ記録はドメインイベント(例: RecordUpdated, UserInvited, PermissionChanged)の発行・購読によって疎結合に行い、`AuditLogging`は各コンポーネントが発行するイベントを購読して記録する(`domain-design-questions.md` Q5回答B)。

### Consequences

#### Positive
- 新しい記録対象操作を追加する際、発行元コンポーネントはイベントを発行するだけでよく、AuditLoggingの存在を意識した明示的な呼び出しコードを書く必要がない
- 将来的なイベント駆動化(例: 他の横断的関心事の追加)への拡張余地を残す

#### Negative
- イベント配信の信頼性(配信保証、順序保証)をNFR設計で明確にしないと、監査ログの完全性(FR8.1)が損なわれるリスクがある。イベント基盤の技術選定(同一プロセス内の同期的イベントディスパッチか、非同期メッセージングか)は次工程(NFR設計・機能設計)で確定する
- デバッグ時に「誰がいつ記録したか」の呼び出し経路が、直接のdepends_onよりも追いにくくなる

#### Neutral
- `team-practices.md`で「監査ログ完全性テスト(全操作が監査ログへ記録されることの網羅的検証)は今回のMVPスコープでは必須としない」ことが明示的に確認済みであるため、イベント配信の信頼性保証の詳細化は次工程以降に委ねられる

### Alternatives Rejected

#### Alternative 1: 明示的な depends_on(Q5 Option A)
- Description: AuditLoggingを独立コンポーネントとしつつ、記録が必要な各コンポーネントが明示的にAuditLoggingへdepends_onする
- Pros: 呼び出し経路が明示的で追跡しやすく、配信保証の設計が不要(同期呼び出しのため)
- Cons: 記録対象操作が増えるたびに、既存コンポーネントの実装変更(AuditLogging呼び出し追加)が必要になり、変更コストが高い

---

## ADR-006: 設定ファイルIO(ConfigImportExport)と業務データIO(DataImportExport)を分割する

### Status
Accepted

### Date
2026-09-13

### Context
FR11(設定ファイルのJSON export/import)とFR12(業務データのCSV export/import)は、対象データ(設定一式 vs 業務データ行)・形式(JSON vs CSV)・利用画面(設定管理画面 vs 一覧画面)がいずれも異なる。

### Decision
`ConfigImportExport`(FR11対応、ConfigEngine/MenuNavigation/PermissionEngineが保持する設定一式が対象)と `DataImportExport`(FR12対応、ListEngine/RecordEditEngineが扱う業務データ行が対象)を別コンポーネントとする(`domain-design-questions.md` Q8回答A)。

### Consequences

#### Positive
- 設定ファイルの検証ロジック(fail fast、FR1.3)と業務データの行単位バリデーションロジックが独立し、それぞれの変更が他方に影響しない
- 利用画面(設定管理画面 vs 一覧画面ツールバー)に対応する形でコンポーネント境界が一致する

#### Negative
- 「ファイルインポート」という共通パターン(ファイル選択→検証→確認モーダル→反映)のUI/実装が2箇所で類似実装になる可能性がある(実装フェーズで共通部品化を検討)

### Alternatives Rejected

#### Alternative 1: 単一コンポーネント `ImportExportService`(Q8 Option B)
- Description: 対象(設定/業務データ)をパラメータで切り替える単一コンポーネントとする
- Pros: コンポーネント数が減る
- Cons: 設定(JSON、fail fast検証)と業務データ(CSV、行単位バリデーション)は検証ロジック・データモデルが全く異なり、共通化のメリットが薄い一方で分岐ロジックが複雑化する

---

## ADR-007: FR13(CIパイプライン)・FR14(可観測性)をコンポーネントカタログの対象外とする

### Status
Accepted

### Date
2026-09-13

### Context
FR13(GitHub ActionsによるビルドとWAR生成)とFR14(OTELエクスポート・構造化ログ)は、アプリケーションが実行時に呼び出す業務ロジックを持つコンポーネントではなく、ビルド時・横断的な運用上の関心事である。

### Decision
FR13・FR14はDomain Designのコンポーネントカタログ(`components.md`)に含めない。FR13は後続のCI Pipelineステージ、FR14は後続のNFR設計ステージで扱う(`domain-design-questions.md` Q10回答A)。

### Consequences

#### Positive
- コンポーネントカタログが「業務ロジックを持つ論理的building block」に焦点を絞ったものになり、ビルド・運用系の関心事と混同されない

#### Negative
- FR14(可観測性)がどのコンポーネントの構造化ログ出力に組み込まれるかは、次工程(NFR設計)で個別に検討する必要がある

### Alternatives Rejected

#### Alternative 1: Observabilityコンポーネントとしてカタログに含める(Q10 Option B)
- Description: 可観測性(FR14)を横断的関心事として`Observability`コンポーネントでカタログに含める
- Pros: 可観測性の実装責務が明確になる
- Cons: Domain Designは「業務ロジックを持つコンポーネント」に焦点を当てるステージであり、横断的なNFR実現手段(ログ出力基盤等)はNFR設計ステージで扱う方が本ステージの目的(ステージ定義)に整合する

---

## ADR-008: 内部設定DBと業務データ用RDBMSの接続をコンポーネント境界に明示する

### Status
Accepted

### Date
2026-09-13

### Context
`team-practices.md`により、内部設定DB(表示設定・RBAC・ユーザ管理・監査ログ)と業務データ用RDBMS(PostgreSQL/MySQL/MariaDB)は別接続とすることが既に確定している。この物理的なDB分離は複数の候補分割案が存在しない、確定済み制約に基づく単一の反映方法である。

### Decision
ConfigEngine・PermissionEngine・UserManagement・AuthenticationService・MenuNavigation・AuditLoggingは`external_dependencies`に内部設定DB(組込みDB)を記載し、ListEngine・RecordEditEngine・SchemaIntrospector・DataImportExportは業務データ用RDBMSを記載する(`domain-design-questions.md` Q11回答A)。

### Consequences

#### Positive
- コンポーネントの`external_dependencies`を見るだけで、そのコンポーネントがどちらのDB接続を必要とするかが一目で分かり、後続のインフラ設計・接続プール設計の入力になる

#### Negative
- 特になし(確定済み制約の反映であり、新たなトレードオフを生まない)

### Alternatives Rejected
本決定は`team-practices.md`で既に確定済みの制約(内部設定DBと業務データDBの別接続)を反映するものであり、他に検討可能な代替案はない。
