# NFR Requirements Questions — menu-navigation (U6)

`inception/requirements-analysis/requirements.md`のNFR1〜NFR8、および`construction/menu-navigation/functional-design/`(entities.md/rules.md/functional-spec.md)に基づき、menu-navigationの非機能要件を定量化するための質問。`GET /api/menu`はログイン後トップ画面・サイドバーの初期表示に直結する高頻度パスである一方、`POST/PUT/DELETE /api/menu-items`(Contract Design追補)は管理者による低頻度の設定操作であり、性質が異なる。以下は、既存のNFR1〜NFR8だけでは本ユニット固有の目標値が決まらない事項のみを問う。

## Q1: `GET /api/menu`の応答時間目標

NFR1は「一覧・詳細画面とも応答時間3秒以内(95パーセンタイル)」を定めています。`GET /api/menu`はトップ画面・サイドバーの初期表示に直結する高頻度パスです。この目標をそのまま適用しますか。

- A. NFR1と同じ3秒以内(95パーセンタイル)をそのまま適用する。トップ画面・サイドバーの初期表示体感を左右する高頻度パスであり、一覧/詳細画面と同水準の目標が妥当
- B. より厳しい目標(例: 1秒以内)を設定する(初期表示は特に体感速度が重要なため)
- X. Other (please specify)

[Answer]: A

## Q2: `POST/PUT/DELETE /api/menu-items`の応答時間目標

Contract Design追補で新設した`/api/menu-items`のCRUD APIは、業務メニュー設定画面から管理者が低頻度に実行する設定操作です。schema-introspectorの管理操作(`POST /api/config/schema-introspection`)はNFR1を緩和した30秒以内の個別目標を設定しています。`/api/menu-items`も同様の考え方でよいですか。

- A. schema-introspectorと同様、管理操作用に緩和した目標(例: 5秒以内)を設定する。単純なCRUD操作でありスキーマ読み込みほど重くはないため、schema-introspectorの30秒より厳しいが一覧/詳細画面のNFR1(3秒)よりは緩い中間的な目標とする
- B. NFR1と同じ3秒以内(95パーセンタイル)をそのまま適用する(単純なCRUD操作でありschema-introspectorほどの緩和は不要)
- X. Other (please specify)

[Answer]: A

## Q3: MenuItem件数・階層深さの想定規模

`GET /api/menu`はフォルダ項目の可視性を配下リーフから再帰的に導出します(rules.md BR6.4)。性能検証・タイムアウト設計の基準として、MenuItemの想定最大件数・階層深さはどの程度を想定しますか。

- A. NFR3(想定利用規模: 数十名程度の業務担当者向け)に対応する規模として、MenuItem総数は数十〜百件程度、階層深さは3〜4階層程度を主な想定とする。この規模であれば再帰的なフィルタリングも実用上問題ない
- B. 数千件規模のMenuItem・より深い階層(5階層以上)も想定し、明示的なキャッシュ・非再帰アルゴリズムの設計を必須とする
- X. Other (please specify)

[Answer]: A

## Q4: 可観測性(ログ・メトリクス)の最低要件

NFR5(可観測性)により構造化ログ・メトリクスのOTELエクスポートは全ユニット共通で求められています。本ユニット固有で最低限記録すべき情報は何ですか。

- A. `GET /api/menu`の応答時間・エラー率をMicrometer計装で記録する(他ユニットと同様のパターン)。`/api/menu-items`のCRUD操作は、作成・更新・削除の実行を構造化ログ(INFO、実行者activeRoleId・対象menuItemIdを含む)として記録し、失敗時はERRORログに理由を含める
- B. Aに加え、`/api/menu-items`のCRUD操作もAuditLogging(監査ログ)の購読対象イベントとして新規に発行する(現時点でAuditLoggingが購読する3イベントには含まれないため、新規イベントクラスの追加が必要になる)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
