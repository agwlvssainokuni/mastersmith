# NFR Requirements Questions: dynamic-data-access

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、dynamic-data-access固有に新たな数値目標は追加しない。functional-design(rules.md BR1.1〜BR6.1)で既に確定済みの内容(動的SQL識別子の安全性、テーブル/カラム単位の権限制御、recordIdのサイドチャネル対策)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

dynamic-data-access Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし、著しい遅延の兆候があれば別途検証)を踏襲。一覧取得はページネーション(page・size、BR1.2)により応答サイズを制限する。

**security-requirements.md**: 本Unitの中核はセキュリティ・アクセス制御そのものである。テーブル単位(BR4.1)・カラム単位(BR4.2)の権限制御を契約#3(permission)経由で行う。動的SQL識別子(テーブル名・カラム名・sortカラム)はTableConfig由来の既知の識別子集合のみを使用し、値は必ずプレースホルダでバインドする(BR1.1、BR1.2のR-10フォロー)。recordIdの検証失敗・非表示カラム到達・該当行なしはすべて404として区別不能に統一し、サイドチャネルを防ぐ(BR2.1)。**重要: BR5.1(FKポップアップ検索)の絞り込み条件は、BR1.1・BR1.2と同様の識別子検証(TableConfig由来の既知の識別子集合であることの確認)が明記されておらず、functional-designステージ終了ゲートで既に記録済みのCritical繰延べ事項(R-11、R-10と同種の脆弱性クラス)である。本ステージではこの既知のギャップを隠蔽せず、security-requirements.mdに明示的な繰延べ事項として記録する(修正はこのステージのスコープ外)。

**scalability-requirements.md**: NFR2(1インスタンス=1業務)を踏襲。RecordViewが扱う業務データの件数はテーブルごとに異なるが、ページネーションにより一覧応答は制限される。

**reliability-requirements.md**: 自宅サーバ1台構成のためSLA/SLO数値目標は設けない。更新は楽観的ロック相当の競合検出を行わない後勝ち方式(BR3.3)であり、これは単一利用者中心の運用規模を前提とした明示的な簡素化のトレードオフである。

**observability-requirements.md**: NFR3を踏襲。業務データの作成・更新成功を監査ログイベント(BR6.1、DATA_RECORD_CREATED/UPDATED)として記録する。削除操作は本Unitに存在しないためDATA_RECORD_DELETEDは発行しない。

**tech-stack-decisions.md**: NamedParameterJdbcTemplate(動的WHERE句をプレースホルダバインドで安全に組み立てるための技術選定、BR1.1)を記載する。

**traceability.json**: upstream_ids = NFR1, NFR2, NFR3(OK)。NFR4〜NFR6・NFR8・NFR9はN/A。BR5.1のR-11既知ギャップは、traceability.jsonのcoverageではなく、security-requirements.mdの本文とfunctional-designステージ終了ゲートの記録に委ねる(NFR自体は新規のNFR項目ではなく既存BRの実装ギャップであるため)。

[Answer]: Looks correct
