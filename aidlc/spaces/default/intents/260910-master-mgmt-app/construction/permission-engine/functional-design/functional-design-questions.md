# Functional Design Questions — permission-engine (U3)

`inception/units-generation/unit-of-work.md`(U3定義)・`inception/domain-design/components.md`(PermissionEngineコンポーネント定義)・`inception/contract-design/contract-summary.md`(C10: PermissionEngineApi)に基づき、permission-engineユニットの機能設計(エンティティ・業務ルール・振る舞い仕様)を確定するための質問。

Domain Design・Contract Designで既に確定済みの事項(`resolveEffectivePermission`/`canAccessScreen`/`assignPermission`のメソッドシグネチャ、Role/PrimaryPermission/AuxiliaryPermissionの3エンティティ、config-engineへの同期依存、audit-loggingへのイベント発行)は再確認しない。ここでは、それらの確定事項からは読み取れない、機能設計として具体化が必要な論点のみを問う。

## Q1: FR4.1「グループ」概念の扱い

FR4.1は「ロールをユーザーまたはグループに割り当てる仕組みを提供しなければならない」と規定していますが、Domain Design(`components.md`)のエンティティ定義にはRole/PrimaryPermission/AuxiliaryPermissionのみが存在し、Group相当のエンティティは定義されていません。また`UserManagement`側のUser エンティティも`roleIds`(ユーザーへの直接のロール付与)のみを持ちます。本MVPでの「グループ」の扱いはどうしますか。

- A. 本MVPスコープでは「グループ」は対象外とする。ロールはユーザーへ直接付与する方式(User.roleIds)のみを実装し、グループへの一括付与は将来拡張として本設計書のOpen Questionsに明記する
- B. 「グループ」もエンティティとして新設し、Group→User(多対多)・Group→Role(多対多)の関連を本ユニットで管理する
- X. Other (please specify)

[Answer]: B. 「グループ」もエンティティとして新設し、Group→User(多対多)・Group→Role(多対多)の関連を本ユニットで管理する

## Q2: ロールの階層構造

`components.md`のRoleエンティティは`parentRoleId`属性を持ち、FR4.3・`team.md`(テスト方針)は「ロール階層継承」に言及しています。ロールの階層構造の制約はどうしますか。

- A. 各ロールは親ロールを最大1つ持てる(木構造)。循環(自分自身や子孫を親に指定すること)は`assignPermission`等のロール編集操作時にバリデーションエラーとして拒否する。階層の深さに上限は設けない
- B. Aと同様の木構造だが、階層の深さに上限(例: 5階層)を設ける
- C. ロールは階層を持たず親子関係もない(フラットな並び)。「階層継承」はスコープ階層(スキーマ→テーブル→カラム)のみを指し、ロール階層は本MVPでは実装しない
- X. Other (please specify)

[Answer]: C. ロールは階層を持たず親子関係もない(フラットな並び)。「階層継承」はスコープ階層(スキーマ→テーブル→カラム)のみを指し、ロール階層は本MVPでは実装しない

## Q2 Follow-up: Domain Design(`components.md`)の`parentRoleId`属性との矛盾解消

Domain Design(`components.md`)のRoleエンティティはすでに`parentRoleId`属性(親ロール参照)を明示しており、`team.md`のテスト方針も「ロール階層継承」を明示的に挙げています。Q2の「階層なし(フラット)」はこれと矛盾するため、あらためて確認しました。あわせて、利用者は自分に割り当てられた複数ロールから一つを選択し、そのロールの権限のもとで操作する(FR4.2、ヘッダーのロール選択UIとして既にモック定義済み)という既存設計との整合も確認しました。

- A. `parentRoleId`を保持(階層あり)。実効権限の解決は「選択された単一のアクティブロールについて、スコープ階層を探しても見つからなければ親ロールへ遡る」方式とし、FR4.2の単一ロール選択とは両立させる
- B. `parentRoleId`を削除(フラットに変更)。Domain Designへの逆向きの追補として明示的に記録する。実効権限の解決はスコープ階層(カラム→テーブル→スキーマ)のみで行う
- X. Other (please specify)

[Answer]: B. `parentRoleId`を削除(フラットに変更)。Domain Designへの逆向きの追補として明示的に記録する。実効権限の解決はスコープ階層(カラム→テーブル→スキーマ)のみで行う

## Q3: 主権限(FULL/READ/NONE/指定なし)の解決順序 — Q2 Follow-upにより前提が変わったため補足回答

FR4.3は「スキーマ・テーブル・カラムの各階層」でのスコープ継承(指定なしは上位スコープを継承)を規定しています。当初、Q2でロール階層(親ロール継承)も採用する前提でスコープ階層との組み合わせ順序を問う設問として本Q3を用意していたが、Q2 Follow-upでロール階層自体を廃止したため、本設問はSupersededとする。実効権限の解決はスコープ階層(カラム→テーブル→スキーマ)のみで行う(ロールを跨いだ遡及探索は行わない)。

[Answer]: Superseded — Q2 Follow-upの回答によりロール階層が廃止されたため、選択肢A/Bはいずれも不採用。解決はスコープ階層(カラム→テーブル→スキーマ)のみで行う(`rules.md` BR3.4参照)。

## Q4: 全階層が「指定なし」の場合のデフォルト値

Q3(改訂後)の解決順序で、対象ロール自身がどのスコープ階層(カラム/テーブル/スキーマ)にも明示設定を持たなかった場合、実効権限(`level`)は何になりますか。

- A. `NONE`(安全側のデフォルト。明示的な許可がない限りアクセス不可)
- B. `READ`(閲覧のみはデフォルトで許可する)
- X. Other (please specify)

[Answer]: A. `NONE`(安全側のデフォルト。明示的な許可がない限りアクセス不可)

## Q5: 補助権限(CREATE/DELETE)の解決方式

FR4.4は補助権限(CREATE/DELETE、許可/禁止/指定なし)を「スキーマ・テーブル単位」で割り当てるとしています(カラム単位は対象外)。解決順序・デフォルトはどうしますか(Q2 Follow-upによりロール階層は廃止されたため、選択肢Aの「親ロールに遡って」の部分は不採用とし、テーブル→スキーマのスコープ階層のみで解決する)。

- A. 主権限と同じ考え方を適用する: テーブル→スキーマの順にロール自身の明示設定を探し、なければ親ロールに遡って同じ探索を繰り返す(Q3のBと同じ入れ子順序)。すべて「指定なし」の場合のデフォルトは`禁止`(安全側)
- B. 補助権限は主権限の`level`が`FULL`の場合にのみ意味を持ち、`FULL`未満のロールには常に`禁止`を返す(補助権限自体の階層継承は行わない)
- X. Other (please specify)

[Answer]: A(ただしQ2 Follow-upによりロール階層は廃止されたため「親ロールに遡って」の部分は適用しない)。テーブル→スキーマの順にロール自身の明示設定(createAllowed/deleteAllowedがnullでない)を探し、見つからなければデフォルトは`禁止`(安全側、`rules.md` BR3.5/BR3.6参照)

## Q6: `assignPermission`の権限昇格防止の判定基準

C10の`assignPermission`は「権限管理者による明示的操作でのみ呼び出し可能。権限昇格(自分自身への昇格含む)を防止する」と定義されています(`PermissionEscalationException`)。「昇格」の具体的な判定基準はどうしますか。またpermission-engine自身にはREST API契約がなく(C10は内部Javaインタフェースのみ)、RBAC設定の書き込み経路はconfig-import-export(C7、設定一式JSONインポート)のみです。この関係はどう整理しますか。

- A. 「昇格」とは、操作者(実行中のリクエストのアクティブロール経由で判定される実効権限)が、割り当てようとしている対象ロール・対象スコープの権限レベルにおいて、自分自身が現に持つ実効権限以上のレベルを付与しようとする操作を指す(例: 自身がTABLE Xに対しREADしか持たない場合、TABLE Xに対しFULLを付与する操作は昇格として拒否)。`assignPermission`はconfig-import-export(C7の`/api/config/import`)がRBAC設定を内部設定DBへ反映する際に、インポートされた各権限設定エントリごとに呼び出す唯一の書き込み経路とする(専用のRBAC管理画面はMVPスコープ外、`unit-of-work.md`のU9定義どおり)
- B. config-import-export経由の一括インポートはこの検証の対象外とする(バルクインポートを実行できること自体が管理者権限の証跡とみなし、`assignPermission`の昇格チェックは将来追加される個別のRBAC管理画面用のAPIのためにインタフェースとしてのみ用意しておく)
- X. Other (please specify)

[Answer]: A. 「昇格」とは、操作者(実行中のリクエストのアクティブロール経由で判定される実効権限)が、割り当てようとしている対象ロール・対象スコープの権限レベルにおいて、自分自身が現に持つ実効権限以上のレベルを付与しようとする操作を指す(例: 自身がTABLE Xに対しREADしか持たない場合、TABLE Xに対しFULLを付与する操作は昇格として拒否)。`assignPermission`はconfig-import-export(C7の`/api/config/import`)がRBAC設定を内部設定DBへ反映する際に、インポートされた各権限設定エントリごとに呼び出す唯一の書き込み経路とする(専用のRBAC管理画面はMVPスコープ外、`unit-of-work.md`のU9定義どおり)

## Q7: `canAccessScreen`の対象画面(screenKey)一覧

C10の`canAccessScreen(activeRoleId, screenKey)`は「画面(ユーザ管理・監査ログ閲覧・メニュー項目)へのアクセス可否判定」とのみ記載され、具体的な`screenKey`の値・判定基準は未定義です。

- A. `screenKey`は固定の管理系画面キー(`user-management`, `audit-log`, `config-import-export`)のみを対象とし、判定はその画面に対応する主権限(スキーマレベル、例: 内部設定DBを表す予約スキーマ名)の`level != NONE`で行う。業務メニュー項目(一覧/編集画面)の表示可否はmenu-navigationが`resolveEffectivePermission`をテーブル単位で直接呼び出して判定し、`canAccessScreen`は使わない
- B. `canAccessScreen`は管理系画面・業務メニュー項目の両方を含む汎用的な画面キー体系とし、menu-navigationも業務メニュー項目の表示可否判定に`canAccessScreen`を使う
- X. Other (please specify)

[Answer]: B. `canAccessScreen`は管理系画面・業務メニュー項目の両方を含む汎用的な画面キー体系とし、menu-navigationも業務メニュー項目の表示可否判定に`canAccessScreen`を使う

## Q8: `PermissionChanged`イベントの発行粒度・内容

`components.md`はPermissionEngineからAuditLoggingへの「権限変更(ロール・主権限・補助権限の割当変更)のドメインイベント発行」を定義していますが、粒度・内容は未確定です。

- A. `assignPermission`の呼び出し(config-import-exportからの一括インポート中の1エントリ処理)ごとに1件のイベント(操作者・対象ロール・対象スコープ種別/参照・変更前後のレベル・日時)を発行する(`project.md` Mandatedの「変更前後の値」を正確に記録する)
- B. config-import-exportの1回のインポート実行につき1件のサマリイベント(実行者・変更件数・日時)のみを発行し、個々の変更前後の値は記録しない
- X. Other (please specify)

[Answer]: B. config-import-exportの1回のインポート実行につき1件のサマリイベント(実行者・変更件数・日時)のみを発行し、個々の変更前後の値は記録しない

## Consolidated Summary Confirmation

Q1(グループ新設)・Q2 Follow-up(ロール階層廃止)を受けて、`entities.md`(Role/PrimaryPermission/AuxiliaryPermission/Group/GroupMembership/GroupRoleの6エンティティ、Domain Designからの逸脱を明記)・`rules.md`(BR3.1〜BR3.12)・`functional-spec.md`(W1〜W4のワークフロー、Domain Design/Contract Designへの追補事項、Assumptions & Open Questions)を作成した。Domain Design(`components.md`のRole.parentRoleId削除・Group追加)およびContract Design(C10への`getGroupDerivedRoleIds`追加)への追補が、Code Generation着手前に必要な状態で残っている。

- Looks correct
- Request changes

[Answer]: Looks correct
