# NFR Design Questions: dynamic-data-access

軽量版方針で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

dynamic-data-access Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: 一覧取得はページネーション(page/size、NFR1.2)。動的クエリ実行コンポーネントはNamedParameterJdbcTemplateを用い、プリペアドステートメントのキャッシュ(JDBCドライバ標準機能)以上のキャッシュ層は設けない。

**security-design.md**: テーブル/カラム単位の権限制御は契約#3(permission)への都度問い合わせで実施(NFR-AUTHZ.1・NFR-AUTHZ.2)。動的SQL識別子(テーブル名・カラム名・sortカラム)はTableConfig由来の既知の識別子集合のみを使用し、値は必ずプレースホルダでバインド(NFR-INJECTION.1)。recordId検証失敗はすべて404に統一(NFR-SIDECHANNEL.1)。**既知の繰延べ事項(Critical、NFR-SIDECHANNEL.2)**: BR5.1(FKポップアップ検索)の識別子検証欠如は、nfr-requirements段階から継続して未解消であり、本nfr-design成果物でも隠蔽せず明示的に記録する(修正は本ステージのスコープ外)。

**scalability-design.md**: 単一インスタンス構成(NFR2.1)。

**reliability-design.md**: 更新は後勝ち方式(楽観的ロックなし、NFR-CONSISTENCY.1)。外部キー制約違反はDB制約に委ねる(NFR-CONSISTENCY.2)。permission呼び出し失敗時の扱い(NFR-FAILSAFE.1)。

**observability-design.md**: 業務データ作成・更新の監査ログイベント(NFR3.1、DATA_RECORD_CREATED/UPDATED)。サイドチャネル防止(クライアント応答の404統一)とサーバ側ログでの詳細区別の両立(NFR3.2)。

**logical-components.md**: dynamic-data-accessは以下3つの論理コンポーネントで構成する。RESTコントローラ(一覧・詳細・新規作成・更新・FKポップアップ検索エンドポインドの受付、X-Active-Roleヘッダー必須検証)、サービス(検索条件組み立て・ソート検証・FK表示名解決・recordId生成検証・入力バリデーション・permission Unitへの契約#3プロセス内呼び出しによるテーブル/カラム権限確認・FKポップアップ検索ロジック〈既知のNFR-SIDECHANNEL.2ギャップを含む〉・監査イベントトリガー)、動的クエリ実行(NamedParameterJdbcTemplateによる動的WHERE句・SELECT・ORDER BY句の組み立てと実行、NFR-INJECTION.1の実装責務を担う)。

**traceability.json**: nfr-requirementsで確定した各NFRx.y項目を、上記の設計解へマッピングする。

[Answer]: Looks correct
