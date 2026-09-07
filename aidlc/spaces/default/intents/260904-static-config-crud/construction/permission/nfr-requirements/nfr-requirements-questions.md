# NFR Requirements Questions: permission

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、permission固有に新たな数値目標や論点を追加する必要はない。functional-design(rules.md BR1.1〜BR6.1)で既に確定済みの権限モデル(テーブル単位・カラム単位アクセス制御、デフォルト拒否/デフォルト許可の非対称な既定方針)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

permission Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし)を踏襲。権限確認(契約#3)はdynamic-data-accessの一覧・詳細・作成・更新の各操作ごとに同期呼び出しされるため、応答が遅延すると業務データ操作全体に波及する。プロセス内呼び出し(同一JVM内)であることを性能上の前提として明記する。

**security-requirements.md**: 本Unitの中核はセキュリティモデルそのものである。テーブル単位権限(BR5.1、未設定時デフォルト拒否=セキュアバイデフォルト)とカラム単位権限(BR5.2、未設定時デフォルト許可)の非対称な既定方針、および管理者専用機能(isAdminクレーム)とは独立したモデルである点(BR6.1)を明記する。

**scalability-requirements.md**: NFR2(1インスタンス=1業務)を踏襲。ロール・グループ・権限設定のレコード数は業務規模に比例するが、想定運用規模(個人利用中心)では問題にならない。

**reliability-requirements.md**: 自宅サーバ1台構成のためSLA/SLO数値目標は設けない。dynamic-data-accessからの権限確認呼び出し(契約#3)が失敗した場合の扱いは、呼び出し元(dynamic-data-access)側でデフォルト拒否として扱われる設計(BR4.1参照)であり、permission自身の障害が誤って許可判定に倒れることはない(フェイルセーフ)。

**observability-requirements.md**: NFR3(Twelve-Factor App準拠、OpenTelemetry対応、構造化ログ)を踏襲。追加の可観測性要件は設けない。

**tech-stack-decisions.md**: プロジェクト全体で確定済みの技術スタック(Java 25/Spring Boot/Gradle、内部H2データストア)をpermission固有の文脈で記載する。本Unit固有の追加の技術選定はない。

**traceability.json**: upstream_ids = NFR1, NFR2, NFR3, NFR5(`ms_`接頭辞をRole/Group/RoleAssignment/TablePermission/ColumnPermission/GroupMembershipテーブルへ適用)。NFR4・NFR6〜NFR9は本Unitの担当範囲外(N/A)として明記する。

[Answer]: Looks correct
