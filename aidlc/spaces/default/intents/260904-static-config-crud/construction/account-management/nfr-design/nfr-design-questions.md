# NFR Design Questions: account-management

軽量版方針で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

account-management Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: 一覧取得は既定page=0・size=20のページネーション(NFR1.2)。本Unit自身はデータを保持しないため、キャッシュ設計は行わない(auth・permission側の応答をそのまま合成する)。

**security-design.md**: isAdmin必須(NFR-AUTHZ.1)。email重複チェック・パスワード管理はauth(契約#4)へ委譲(NFR-DATA.1)。アカウント無効化はauth(契約#4)への委譲呼び出しのみ(NFR-DATA.2)。

**scalability-design.md**: 単一インスタンス構成(NFR2.1)。本Unitは永続エンティティを持たないためデータ量に起因するスケーラビリティ課題はない。

**reliability-design.md**: 初期ロール割当失敗時はAccount作成自体をロールバックしない(NFR-CONSISTENCY.1)。アカウント無効化時のリフレッシュトークン即時失効はauth側の既知の未解消ギャップに依存(NFR-FAILSAFE.1、auth Unit側で既に記録済み)。通知送信失敗はリトライしない(NFR-FAILSAFE.2)。

**observability-design.md**: アカウント作成・編集・無効化の監査ログイベント(NFR3.1、ACCOUNT_CREATED/UPDATED/DISABLED)。機微情報は保持しないためログにも出力されない(NFR3.2)。

**logical-components.md**: account-managementは以下2つの論理コンポーネントで構成する(永続エンティティを持たないためリポジトリを持たない)。RESTコントローラ(アカウント作成・一覧・編集・無効化エンドポイントの受付、isAdmin認可検証)、サービス(契約#4〈auth〉・契約#21〈permission〉への委譲呼び出しの合成によるAccountView構成、初期ロール割当の部分失敗許容ロジック)。

**traceability.json**: nfr-requirementsで確定した各NFRx.y項目を、上記の設計解へマッピングする。

[Answer]: Looks correct
