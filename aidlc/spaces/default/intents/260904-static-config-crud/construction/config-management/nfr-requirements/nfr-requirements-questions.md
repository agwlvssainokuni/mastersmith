# NFR Requirements Questions: config-management

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、config-management固有に新たな数値目標は追加しない。functional-design(rules.md BR1.1〜BR8.1)で既に確定済みの内容(Caffeineキャッシュ、認証情報の暗号化保持、isAdmin必須+GET /api/menuの例外、設定インポート時の全体拒否、監査ログ)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

config-management Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし、著しい遅延の兆候があれば別途検証)を踏襲。DbConnection・TableConfig・MenuItemの読み取りは常にCaffeineインメモリキャッシュ経由で行い(BR6.1)、リクエストのたびにDBへ問い合わせる実装は行わない(project.md Mandated TC-14)。これが本Unitの主要な性能要件である。

**security-requirements.md**: 本Unitの中核はセキュリティ・アクセス制御である。全操作はisAdminクレーム必須(BR7.1)とし、唯一の例外であるGET /api/menu(契約#22・#23、BR4.3)はX-Active-Roleに対応するroleIdのcanList権限でテーブルノードをフィルタする。DbConnection.credentialRef(業務DB接続の認証情報)は暗号化して保持し、暗号鍵はソースコード・Gitリポジトリにコミットせず環境変数等で管理する(project.md Forbidden)。設定エクスポートも暗号化されたまま出力し復号しない(BR5.1)。

**scalability-requirements.md**: NFR2(1インスタンス=1業務)を踏襲。DbConnection・TableConfig・MenuItemのレコード数は業務規模に比例するが、想定運用規模では問題にならない。

**reliability-requirements.md**: 自宅サーバ1台構成のためSLA/SLO数値目標は設けない。設定インポート(BR5.2)は不正形式・スキーマ不一致・必須項目欠落のいずれかを検出した場合、部分適用を行わず全体を拒否する(全件検証後にerrors配列で返す)ことで、設定の不整合な中間状態を防ぐ。

**observability-requirements.md**: NFR3を踏襲。DbConnection・TableConfig・MenuItemの作成・更新・削除、および設定インポート成功を監査ログイベント(BR8.1、CONFIG_TABLE_*/CONFIG_CONNECTION_*/CONFIG_MENU_*/CONFIG_IMPORTED)として記録する。credentialRefの実値(復号後の認証情報)はログに出力しない。

**tech-stack-decisions.md**: キャッシュ機構としてCaffeine(インメモリキャッシュ、BR6.1)を採用する。

**traceability.json**: upstream_ids = NFR1(OK)、NFR2(OK)、NFR3(OK)。NFR4〜NFR6・NFR8・NFR9はN/A(横断方針または他Unit担当)。NFR7(パスワードハッシュ化)はN/Aとし、代わりにDbConnection.credentialRefの暗号化保持を本Unit固有のセキュリティ要件として明記する。

[Answer]: Looks correct
