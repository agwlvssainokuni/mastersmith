# NFR Design Questions: frontend-admin

軽量版方針で進める。frontend-adminはUI Unit(kind: ui)のため、本ステージで作成する成果物はperformance-design.md・security-design.md・logical-components.md・traceability.jsonの4件のみとする(scalability/reliability/observability-design.mdは対象外)。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

frontend-admin Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: 監査ログ・アカウント一覧・テーブル一覧はバックエンドのページネーションをそのまま利用し、フロントエンド側で全件取得後にクライアント側ページングする実装は行わない(NFR1.2)。

**security-design.md**: 全画面はAppShellレベルのルートガード(isAdminクレームの有無でサイドナビ項目・ルーティングを出し分け)で管理者ゲーティングを実装する(NFR-AUTHZ.1)。実際の認可判定はバックエンドの403応答に一元化し、フロントエンド側でクレームの内容を独自に検証するロジックは持たない(NFR-AUTHZ.2)。業務DB由来の識別子文字列・利用者入力値の表示はReact/TSXの標準エスケープに従う(NFR-INJECTION.1)。

**logical-components.md**: frontend-adminは以下3つの論理コンポーネントで構成する。画面コンポーネント(ロール・グループ管理、監査ログ、アカウント管理、設定管理、スキーマ取り込み、エクスポート/インポート、メニュー管理の各ページ)、共通UIコンポーネント(make-you-chic-uiベースの共通部品: ConfirmDialog、ConfigTabPanel、AuditLogTable、SchemaImportPreview、MenuTree等)、APIクライアント(バックエンドREST呼び出しの共通化、403応答時の共通エラー表示への切り替え)。

**traceability.json**: nfr-requirementsで確定した各NFR項目(NFR1.1・NFR1.2、NFR-AUTHZ.1・NFR-AUTHZ.2・NFR-INJECTION.1)を、上記の設計解へマッピングする。NFR3は、姉妹Unitであるfrontend-core Unitの同ステージレビューで指摘済みの判定誤り(認可エラー表示切替を可観測性の根拠にするのは論理的に誤り)を踏まえ、本UnitではN/Aとして判定し直す(nfr-requirements/traceability.jsonのOK判定は上流の既存Major指摘として残存するが、nfr-design段階では正確な判定を採用する)。

[Answer]: Looks correct
