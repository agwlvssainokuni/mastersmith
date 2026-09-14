# Functional Design Questions — audit-logging (U7)

`inception/units-generation/unit-of-work.md`(U7定義)・`inception/domain-design/components.md`(AuditLoggingコンポーネント定義)・`inception/contract-design/contract-summary.md`(C6: audit-logging REST API)に基づき、audit-loggingユニットの機能設計(エンティティ・業務ルール・振る舞い仕様)を確定するための質問。

Domain Design・Contract Designで既に確定済みの事項(`AuditLogEntry`の属性形状、C6 `GET /api/audit-log`のページング・`targetType`フィルタ、`PUT/PATCH/DELETE`を実装しないという契約レベルの不変条件、`permission-engine`への同期依存)は再確認しない。また、`permission-engine`(U3、実装済み)は監査ログ閲覧画面用に予約済みのscreenKey `"audit-log"`(`rules.md` BR3.10・BR3.15)を既に実装済みであり、これもそのまま利用する前提とする。ここでは、それらの確定事項からは読み取れない、機能設計として具体化が必要な論点のみを問う。

なお、config-engine(U1)・permission-engine(U3)・data-import-export(U8)は実装済みで、それぞれ`ConfigChangedEvent`・`PermissionChangedEvent`・`ImportExecutedEvent`というドメインイベントを既にSpringの`ApplicationEventPublisher`経由でfire-and-forget発行するコードを持っています。audit-loggingはこれらの**実際に存在するイベントクラスの形状**を購読対象として設計する必要があります(各ユニットの機能設計時のJavadoc・rules.mdが購読側=audit-loggingの実装を前提にしていたため)。

## Q1: 本Boltで購読するイベント種別の範囲

user-management(U4)・config-import-export(U9)・record-edit-engine(U11)はまだ未実装であり、これらが発行する予定のドメインイベント(`UserInvited`等)はまだ存在しません。本Bolt(audit-loggingユニット)では、どの範囲のイベント購読を実装しますか。

- A. 現時点で実際に存在する3種類のイベント(`ConfigChangedEvent`・`PermissionChangedEvent`・`ImportExecutedEvent`)のみを購読する実装とする。将来user-management・config-import-export・record-edit-engineが実装される際に、それぞれのBoltで新しいイベントクラスの発行コードと、audit-logging側への対応する購読メソッドの追加を行う(各ユニットのJavadocが既に前提としている拡張パターン)
- B. 将来発行される可能性のあるイベントも見越して、共通の親インタフェース(例: `AuditableEvent`)をこのBoltで新規定義し、既存3ユニット(config-engine・permission-engine・data-import-export)の既存イベントクラスを遡って修正し、そのインタフェースを実装させる
- X. Other (please specify)

[Answer]:

## Q2: targetType/targetIdへのマッピング方法(イベントごとに形状が異なる)

`components.md`の`AuditLogEntry`は`targetType`・`targetId`を別フィールドとして持ちますが、実際の3イベントはそれぞれ異なる形状です。

- `ConfigChangedEvent(operation, target, actor, occurredAt)`: `target`は`"schema.table"`や`"importConfigSet:3 tables"`のような自由記述の単一文字列で、型とIDが分離されていません
- `PermissionChangedEvent(targetRoleId, scopeType, scopeRef, actor, occurredAt)`: `targetRoleId`(変更対象ロール)・`scopeType`・`scopeRef`(変更対象スコープ)の3つの識別子があり、どれを`targetId`とすべきか自明ではありません
- `ImportExecutedEvent(tableConfigId, actor, successCount, errorCount, committed, occurredAt)`: `tableConfigId`が明確な対象IDです

`targetType`/`targetId`への変換方針はどうしますか。

- A. `targetType`はイベントの発行元ユニット名の固定文字列(`"ConfigEngine"`・`"PermissionEngine"`・`"DataImportExport"`)とし、`targetId`はConfigChangedEventなら`target`文字列をそのまま、PermissionChangedEventなら`targetRoleId`をそのまま、ImportExecutedEventなら`tableConfigId`をそのまま格納する(イベントごとに固定のマッピング規則を決め打ちする、最も単純な方式)
- B. `targetType`はより意味のある単位(ConfigChangedEventなら`operation`の対象種別から推定した`"TableConfig"`等、PermissionChangedEventなら`scopeType`の値、ImportExecutedEventなら`"TableConfig"`)とし、`targetId`は各イベントの主たる識別子を格納する
- C. `targetType`・`targetId`の意味論をイベント間で無理に揃えず、全イベント共通で`targetType`="イベントクラス名の単純名"(`"ConfigChangedEvent"`等)とし、`targetId`はイベント固有の識別情報を文字列化したもの(PermissionChangedEventなら`scopeType + ":" + scopeRef`のように複数フィールドを連結)とする
- X. Other (please specify)

[Answer]:

## Q3: operationType値の決定方法(イベントによってoperation相当のフィールドの有無が異なる)

`AuditLogEntry.operationType`は操作種別を表しますが、`ConfigChangedEvent`だけが`operation`(`ConfigChangeOperation`列挙: `DRAFT_IMPORTED`/`CONFIG_SET_IMPORTED`/`TRANSLATION_UPSERTED`)を持ち、`PermissionChangedEvent`・`ImportExecutedEvent`には対応する列挙値がありません。各イベントの`operationType`はどう決めますか。

- A. `ConfigChangedEvent`はその`operation`列挙値の文字列表現(`"DRAFT_IMPORTED"`等)をそのまま使う。`PermissionChangedEvent`は固定文字列`"PERMISSION_CHANGED"`とする。`ImportExecutedEvent`は`committed`の値に応じて`"IMPORT_COMMITTED"`/`"IMPORT_ROLLED_BACK"`のいずれかとする(イベントの実態を最も反映する方式)
- B. 全イベント共通で、イベントクラスの単純名をそのまま`operationType`として使う(`"ConfigChangedEvent"`・`"PermissionChangedEvent"`・`"ImportExecutedEvent"`)。個々の操作種別の粒度はUIから`targetType`・`targetId`と合わせて解釈させる
- X. Other (please specify)

[Answer]:

## Q4: actorフィールドの意味論の不一致(PermissionChangedEvent.actorは実際にはactiveRoleId)

`AuditLogEntry.actorUserId`という名前は操作者のユーザーIDを意味しますが、実装済みの3イベントを確認すると次の不一致があります。

- `ConfigChangedEvent.actor`: schema-introspector等システム操作は`"system"`固定。利用者操作(`importConfigSet`等)についても現状`"system"`が使われており、実際のユーザーIDはまだ伝播されていません(`ConfigChangedEvent`のJavadoc・`config-engine`の`code-summary.md`に既知のギャップとして明記済み)
- `PermissionChangedEvent.actor`: Javadocによれば「変更操作を実行した利用者の**activeRoleId**」であり、ユーザーIDではなくロールIDです
- `ImportExecutedEvent.actor`: 「インポートを実行した利用者の**ユーザーID**」であり、これのみ本来の意味どおりです

audit-loggingは、この意味論が不揃いな`actor`値を`AuditLogEntry.actorUserId`へどう格納しますか。

- A. 各イベントの`actor`値をそのまま`actorUserId`へ格納する(値の意味論がイベントによって「システム識別子」「ロールID」「ユーザーID」と異なることを許容し、この制約を`rules.md`・`functional-spec.md`の既知の制約として明記する。ユーザーID伝播の是正は将来のContract Design追補待ちとして引き継ぐ)
- B. `ConfigChangedEvent`・`PermissionChangedEvent`由来のエントリについては、`actorUserId`を`null`にし、代わりに新設する`actorRaw`のような別フィールドに元の値(ロールID等)を保持する(「ユーザーIDである」という含意を`actorUserId`という名前で偽らない)
- X. Other (please specify)

[Answer]:

## Q5: beforeValue/afterValueの扱い(現時点でどのイベントも変更前後の値を運ばない)

`project.md`の`## Mandated`は監査ログに「変更前後の値」の記録を求めていますが、実装済みの3イベント(`ConfigChangedEvent`・`PermissionChangedEvent`・`ImportExecutedEvent`)はいずれも構造化された変更前後の値を運びません(`PermissionChangedEvent`・`ImportExecutedEvent`についてはこの省略が、それぞれpermission-engine・data-import-exportの機能設計インタビューで確認済みの意図的なスコープ判断です)。audit-logging側の`AuditLogEntry.beforeValue`/`afterValue`はこの3イベント由来のエントリについてどう扱いますか。

- A. 3イベントいずれも`beforeValue`/`afterValue`は`null`として記録する。将来record-edit-engine(業務データ変更)・user-management(ユーザー変更)が実装される際に、それらのイベントが変更前後の値を含む形で発行され、その時点で初めて非null値が記録されるようになる、という段階的な充足として明記する
- B. 今回のBoltの対象外の3イベントについても、`beforeValue`/`afterValue`を空値ではなく何らかのプレースホルダ値(例: 対象エンティティの現在の全体スナップショット)で埋める追加実装を行う
- X. Other (please specify)

[Answer]:

## Q6: 監査ログ閲覧APIの並び順とフィルタ範囲

Contract Design(C6: `GET /api/audit-log`)は`page`・`pageSize`・`targetType`のみをクエリパラメータとして定義しており、並び順(ソート順)は契約上未規定です。

- A. `occurredAt`の降順(新しい記録が先頭)を既定の並び順とする。C6契約にはソートパラメータを追加せず、常に固定の降順とする
- B. `occurredAt`の昇順(古い記録が先頭、時系列順)を既定の並び順とする
- X. Other (please specify)

[Answer]:

## Q7: 追記専用(append-only)の強制方法

`## Mandated`(project.md)は「監査ログは改ざん・削除ができないようにする」「アプリケーションからのUPDATE/DELETE経路を持たない追記専用とする」ことを求めています。C6契約レベルでは既にPUT/PATCH/DELETEエンドポイントを実装しないことで強制されていますが、機能設計として、どの水準まで追記専用を強制しますか。

- A. アプリケーション層(リポジトリ/DAOインタフェース)にUPDATE/DELETEに相当するメソッドを一切定義しない、という設計制約のみとする(C6契約の不変条件をコード構造でも一貫させる水準)
- B. Aに加えて、内部設定DB(組込みDB)側にもUPDATE/DELETEを拒否するトリガー等のDBレベルの防御を設ける(アプリケーション層のバグやオペレーターの直接SQL操作からも保護する多層防御)
- X. Other (please specify)

[Answer]:

## Q8: 監査記録処理自体が失敗した場合の扱い

各イベントはfire-and-forgetで発行され、発行元ユニットは監査記録の完了を待たない設計です(`ConfigChangedEvent`等のJavadocに明記済み)。audit-logging側でイベント購読後の内部設定DBへの書き込みが失敗した場合(DB接続断等)、どう扱いますか。

- A. 失敗を構造化ログ(ERRORレベル)へ記録するのみとし、発行元の処理へは一切影響を与えない(例外を再送出しない、リトライもしない)。監査記録の欠落は許容されるMVPスコープの制約とする
- B. 失敗時は一定回数リトライする(例: 3回、指数バックオフ)。リトライも失敗した場合は構造化ログへ記録し、それ以上は追跡しない
- X. Other (please specify)

[Answer]:

## Q9: 複数イベントの並行到着時の記録順序保証

複数のユニットから並行して(同時期に)イベントが発行される場合(例: config-import-exportの1回の実行でConfigChangedEvent・PermissionChangedEventが連続発行される)、audit-logging側での記録順序についてどの水準の保証が必要ですか。

- A. 厳密な到着順・グローバルな順序保証は不要とし、各`AuditLogEntry`の`occurredAt`(イベント発生元が記録した発生日時)のみを正とする。DBへの書き込み順序と`occurredAt`の順序が一致しなくても、閲覧画面側で`occurredAt`によるソート(Q6の並び順)を適用すれば実用上問題ないとする
- B. `AuditLogEntry`に発行元とは独立した単調増加のシーケンス番号(DB採番)を追加し、同一`occurredAt`(秒精度)のイベント間でも一意な記録順序を保証する
- X. Other (please specify)

[Answer]:
