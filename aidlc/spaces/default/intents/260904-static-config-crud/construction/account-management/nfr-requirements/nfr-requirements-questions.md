# NFR Requirements Questions: account-management

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、account-management固有に新たな数値目標は追加しない。functional-design(rules.md BR1.1〜BR5.1)で既に確定済みの内容(ページネーション、isAdmin必須、契約#4/#21経由でのauth・permissionへの委譲、監査ログ)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

account-management Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし、著しい遅延の兆候があれば別途検証)を踏襲。アカウント一覧取得は既定page=0・size=20のページネーションを行い(BR2.1)、一覧応答のペイロードサイズを制限する。

**security-requirements.md**: 全操作はisAdminクレーム必須(BR5.1)。emailの一意性検証・パスワード管理はAccountの唯一の所有者であるauthに委譲し、本Unit自身では重複チェック・パスワード保持を行わない(BR1.2)。

**scalability-requirements.md**: NFR2(1インスタンス=1業務)を踏襲。AccountViewのレコード数は登録利用者数に比例するが、想定運用規模では問題にならない。

**reliability-requirements.md**: 自宅サーバ1台構成のためSLA/SLO数値目標は設けない。アカウント作成時、契約#21での初期ロール割り当てが失敗しても契約#4で作成済みのAccount自体はロールバックしない(BR1.3、部分失敗を許容する明示的な設計判断であり、400応答にaccountIdを含めて管理者がPUT編集で再設定できるようにする)。アカウント無効化(BR4.1)によるリフレッシュトークンの即時失効はauthの責務であり、auth UnitのnfR-requirementsで既に繰延べ事項(R-01)として記録済みの未実装ギャップに依存していることを明記する。

**observability-requirements.md**: NFR3を踏襲。アカウント作成・編集・無効化を監査ログイベント(BR1.5/BR3.2/BR4.2、ACCOUNT_CREATED/ACCOUNT_UPDATED/ACCOUNT_DISABLED)として記録する。AccountCreatedEvent(BR1.4)の通知送信失敗はログ記録のみでリトライしない(契約#9 Q12の耐障害性方針)。

**tech-stack-decisions.md**: 本Unit固有の技術選定はなく、プロジェクト全体の標準(Java 25/Spring Boot/Gradle)を踏襲する旨のみ記載する。

**traceability.json**: upstream_ids = NFR1, NFR2, NFR3(OK)。NFR4〜NFR9はN/A(横断方針または他Unit担当。特にNFR7のパスワードハッシュ化・NFR9の初期管理者アカウント自動作成はauth Unitの責務)。

[Answer]: Looks correct
