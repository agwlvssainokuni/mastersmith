# NFR Design Questions: schema-ingestion

軽量版方針で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

schema-ingestion Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: NFR1.1・NFR1.2を踏襲。スキーマ走査(JDBC DatabaseMetaData呼び出し)の並列化・最適化は行わない。取り込み対象テーブルの絞り込み(プレビュー画面での選択)により、走査結果の後段処理の負荷を抑える設計とする。

**security-design.md**: 全操作(接続テスト・スキーマ一覧・プレビュー)はコントローラ層でisAdminクレームを検証する(NFR-AUTHZ.1)。業務DB接続情報(credentialRef)は呼び出し元(config-management)が復号済みの値として渡し、本Unit自身は復号ロジック・暗号鍵を持たない(NFR-DATA.1)。接続失敗・走査失敗時のエラーメッセージには、認証情報(パスワード等)の値そのものを含めない(NFR-DATA.2)。

**scalability-design.md**: 単一インスタンス構成(NFR2)。走査対象は個人利用中心の想定業務規模(数十〜数百テーブル程度)を前提とする。

**reliability-design.md**: 本Unitはステートレスであり、走査結果を永続化しない(NFR-RESILIENCE.1)。業務DBへの接続失敗は例外として呼び出し元(config-management)へ伝播し、REST境界では500として応答する(BR6.1)。リトライは行わない。

**observability-design.md**: 接続テスト・走査失敗時は、対象DbConnectionの識別情報(name等、認証情報は含めない)と例外内容を構造化ログとして出力する。

**logical-components.md**: schema-ingestionは以下2つの論理コンポーネントで構成する(ステートレスのためリポジトリを持たない)。RESTコントローラ(接続テスト・スキーマ一覧・プレビューエンドポイントの受付、isAdmin認可検証)、サービス(JDBC DatabaseMetaDataを用いたスキーマ走査ロジック。driverType〈PostgreSQL/MySQL/MariaDB〉に応じた方言差異〈複合主キーのKEY_SEQ順序、主キーなしテーブルの扱い、ビューの読み取り専用扱い、型正規化〉を吸収する設計とし、team.md Testing Postureの前倒し特性テスト対象と1対1で対応させる)。

**traceability.json**: nfr-requirementsで確定した各NFRx.y項目を、上記の設計解へマッピングする。

[Answer]: Looks correct
