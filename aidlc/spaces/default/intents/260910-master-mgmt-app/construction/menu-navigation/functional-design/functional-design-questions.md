# Functional Design Questions — menu-navigation (U6)

`inception/units-generation/unit-of-work.md`(U6定義)・`inception/domain-design/components.md`(MenuNavigationコンポーネント定義)・`inception/contract-design/contract-summary.md`(C3: menu-navigation REST API、C12: menu-navigation→config-import-export内部インタフェース)・`inception/refined-mockups/mockups.md`(トップ画面・サイドバー)に基づき、menu-navigationユニットの機能設計(エンティティ・業務ルール・振る舞い仕様)を確定するための質問。

Domain Design・Contract Designで既に確定済みの事項(`MenuItem`の属性形状、C3 `GET /api/menu`のレスポンス形状、権限判定はpermission-engineへ委譲、トップ画面カードが業務メニューの第1階層グループ単位で表示されること)は再確認しない。permission-engine(U3、実装済み)は`canAccessScreen(activeRoleId, screenKey)`を提供しており、screenKeyが予約キー(`"user-management"` / `"audit-log"` / `"config-import-export"`)以外の場合はtableConfigIdとして扱う仕様(`permission-engine/functional-design/rules.md` BR3.10)。また、schema-introspector(U2、実装済み)は自身の画面(業務メニュー設定画面)が設定管理画面と共通であるとして、予約screenKey`"config-import-export"`をそのまま用いている(`schema-introspector/functional-design/rules.md` BR2.8)。

## Q1: 管理メニュー4項目とscreenKeyの対応関係

トップ画面・サイドバーの管理メニューには「業務メニュー設定」「ユーザ管理」「監査ログ管理」「設定管理」の4項目が表示されます(`mockups.md`)。schema-introspectorは既に、自身の画面(業務メニュー設定に相当)を予約screenKey`"config-import-export"`で権限判定する設計を確定済みです。menu-navigationのメニュー項目一覧では、この4項目それぞれにどのscreenKeyを割り当てますか。

- A. 「業務メニュー設定」「設定管理」の2項目はいずれも同一の予約screenKey`"config-import-export"`を用いる(schema-introspectorの既存設計を踏襲し、2つのメニュー項目が同じ権限スコープを共有する)。「ユーザ管理」は`"user-management"`、「監査ログ管理」は`"audit-log"`を用いる
- B. 「業務メニュー設定」と「設定管理」は別々の権限スコープとして扱うべきであり、新規の予約screenKeyを追加する(既存のschema-introspector側の設計を見直すフォローアップが必要になる)
- X. Other (please specify)

[Answer]: A

## Q2: 業務メニュー(MenuItem階層)の作成・編集手段

`components.md`の`MenuItem`エンティティにはCRUD用のAPIがContract Design(C3は参照専用のGET、C12は`config-import-export`向けの内部インタフェース)に見当たりません。業務メニューの階層構造(グループ・サブグループ・リーフ項目)は、本Boltではどのように作成・編集しますか。

- A. 本Boltでは、`config-import-export`(U9、未実装)のJSON import機能(C12 `importMenuStructure`)経由でのみ投入・更新できることとし、menu-navigation自体は専用のCRUD UI/APIを持たない(`GET /api/menu`による参照のみ)。config-import-export実装までの間、開発・検証用にDBへ直接投入する暫定手段(テストフィクスチャ等)を用いる
- B. menu-navigation自身に、業務メニュー設定画面から呼び出す簡易なCRUD API(`POST/PUT/DELETE /api/menu-items`等)を本Boltで追加する
- X. Other (please specify)

[Answer]: B

## Q3: フォルダ(中間階層)項目の表示可否判定

`targetTableConfigId`を持たない中間階層のMenuItem(グループ・サブグループ、リーフのテーブル項目を束ねるだけの「フォルダ」)には、permission-engineの`PrimaryPermission`が直接紐づくscopeRef(tableConfigId)が存在しません。フォルダ項目自体の表示可否はどう判定しますか。

- A. フォルダ項目は個別の権限判定を行わない。配下(子孫)に1件でも表示可能なリーフ項目(`canAccessScreen`がtrueを返すテーブル項目)があれば、そのフォルダも再帰的に表示する。配下が全て非表示ならフォルダ自体も非表示にする
- B. フォルダ項目にも仮想的なscreenKey(例: フォルダのmenuItemIdをそのままscopeRefとする)を割り当て、permission-engine側にフォルダ用の`PrimaryPermission`エントリを別途用意させる
- X. Other (please specify)

[Answer]: A

## Q4: `GET /api/menu`のactiveRoleId解決方法

C3契約の`GET /api/menu`は`bearerAuth`のみを要求しており、リクエストパラメータにactiveRoleIdはありません。schema-introspector・audit-loggingは、authentication-service(U5、未実装)への暫定対応として`ActiveRoleResolver`(`X-Active-Role-Id`ヘッダー読み取りの暫定実装)を共有利用しています。menu-navigationも同じ暫定実装を再利用しますか。

- A. schema-introspector・audit-loggingと同じ`ActiveRoleResolver`拡張点(`com.mastersmith.schema.security`パッケージの既存実装)をそのまま再利用する
- B. menu-navigation独自の一時的なactiveRoleId解決ロジックを別途実装する
- X. Other (please specify)

[Answer]: A

## Q5: 空メニュー判定(FR7.3)の範囲

FR7.3は「権限のあるメニューが1件もない場合」に案内メッセージを表示するとしています。`GET /api/menu`が返す`businessMenu`・`adminMenu`のどちらの組み合わせで「0件」と判定しますか。

- A. `businessMenu`・`adminMenu`の両方が空配列の場合のみ「利用可能なメニューがありません」とする(どちらか一方にでも表示可能な項目があれば通常表示)。判定・表示はフロントエンド側(frontend-ui、未実装)の責務とし、menu-navigation側は権限フィルタ後の結果(空配列を含む)をそのまま返すのみとする
- B. menu-navigation側のAPIレスポンスに、両方空である旨を示す明示的なフラグ(例: `isEmpty: true`)を追加する
- X. Other (please specify)

[Answer]: A

## Q6: 兄弟項目の並び順

同一階層内のMenuItem(兄弟項目)は`order`属性で並び替えられます(`components.md`)。並び順の解決規則はどうしますか。

- A. `order`昇順でソートする。同一`order`値が重複した場合の同点順序は未規定でよい(実用上ほぼ発生しないため、安定ソートに委ねる)
- B. `order`は各階層内で一意である制約を設け、重複があれば起動時・設定投入時にfail fastでエラーとする
- X. Other (please specify)

[Answer]: A

## Q7: 参照先TableConfigが存在しない場合の扱い

`MenuItem.targetTableConfigId`が指す`TableConfig`(config-engine管理)が存在しない、または削除済みの場合、`GET /api/menu`はどう振る舞いますか。

- A. 実行時に該当MenuItemを結果から除外する(fail fastにはしない。設定の不整合はconfig-import-export側のインポート時検証で防ぐ方針とし、menu-navigation側は防御的に無視する)
- B. 起動時に全MenuItemの`targetTableConfigId`参照整合性を検証し、不整合があればfail fastでアプリ起動を停止する
- X. Other (please specify)

[Answer]: A

## Q8: 管理メニュー4項目の表示順・可変性

管理メニュー(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)は業務メニューと異なりシステム固定の項目です。これらもMenuItemエンティティとして永続化し、config-import-exportのJSON importで構成変更可能にしますか、それともアプリケーションコードに固定順でハードコードしますか。

- A. アプリケーションコードに固定の配列としてハードコードする(表示名・順序ともに変更不可。`project.md`の「共通エンジン層には業務固有の情報をハードコードしない」Mandatedは、これらがアプリ自身の管理機能であり特定業務のテーブル名・カラム名ではないため抵触しない)
- B. 業務メニューと同様にMenuItemエンティティとして永続化し、config-import-export経由で表示名・順序を変更可能にする
- X. Other (please specify)

[Answer]: A

## Q9: トップ画面カードクリック時の遷移

`mockups.md`のトップ画面は業務メニューを第1階層グループ単位でCard表示しますが、カードクリック後の遷移先はサイドバー(`mockups.md` 4.)側で具体化されているのみです。menu-navigation(バックエンド)が提供する情報として、この遷移に追加で必要なものはありますか。

- A. 追加で必要な情報はない。`GET /api/menu`が返す階層構造(`children`を含む`MenuItem`ツリー)だけで、フロントエンド側がカードクリック時にサイドバーの該当グループを展開・選択状態にできる(バックエンド側での追加対応不要)
- B. カードクリック時に「最初に表示すべきリーフ項目」を明示するため、各グループMenuItemに「既定の遷移先leafMenuItemId」のような追加属性が必要
- X. Other (please specify)

[Answer]: A

## Q10 (フォローアップ): MenuItem CRUD APIの認可・バリデーション方針

Q2で「menu-navigation自身にMenuItemのCRUD API(POST/PUT/DELETE)を追加する」ことになりましたが、Contract Design(C3)には`GET /api/menu`しか定義がありません。追加するCRUD APIの認可・バリデーション方針はどうしますか。

- A. `POST/PUT/DELETE /api/menu-items`を新規定義し、Q1で確定したscreenKey`"config-import-export"`で`canAccessScreen`をサーバー側で再検証する。`targetTableConfigId`はconfig-engine側の存在確認(400)を行う。`order`は同階層内での一意性を要求しない(Q6に従う)。この内容を本Boltでcontract-summary.md(C3)の追補として確定させる
- B. 深く考えず、認可・バリデーションの詳細はcode-generationステージに先送りする(今はCRUD APIを追加するという方針だけを確定させる)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
