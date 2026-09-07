# NFR Design Questions: frontend-core

軽量版方針で進める。frontend-coreはUI Unit(kind: ui)のため、本ステージで作成する成果物はperformance-design.md・security-design.md・logical-components.md・traceability.jsonの4件のみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

frontend-core Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: 一覧画面はバックエンドのページネーションをそのまま利用する(NFR1.2)。

**security-design.md**: recordIdはクライアント側でデコードしない(NFR-DATA.1)。パスワード忘れ申請はアカウント存在有無を非開示(NFR-DATA.2)。X-Active-Roleによるロール切替はクライアント側で完結するが認可境界ではない(NFR-AUTHZ.1)。React標準エスケープ(NFR-INJECTION.1)。**既知の繰延べ事項(Major、NFR-AUTHN.1)**: トークンリフレッシュ時のリフレッシュトークンローテーションへの追従漏れを、本nfr-design成果物でも明示的に記録する(修正はスコープ外)。トークン管理の責務はAPIクライアントコンポーネントに帰属させ、将来の修正時の実装帰属先を明確にする。

**logical-components.md**: frontend-coreは以下3つの論理コンポーネントで構成する。画面コンポーネント(ログイン、トップ、一覧、詳細、編集、FKポップアップ検索、アカウント自己サービス系、ロール切替、ログアウトの各画面)、共通UIコンポーネント(FkReferencePicker、LoginLockoutNotice等のmake-you-chic-uiベース部品)、APIクライアント(バックエンドREST呼び出しの共通化、アクセストークン・リフレッシュトークンの保持と401時の自動リフレッシュ、既知のNFR-AUTHN.1ギャップの実装帰属先)。

**traceability.json**: nfr-requirementsで確定した各NFR項目を、上記の設計解へマッピングする。NFR3は、frontend-admin Unitと同様の判定見直しによりN/Aとする(既にnfr-requirements段階でN/A判定済み、継続)。

[Answer]: Looks correct
