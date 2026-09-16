<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Business Rules — menu-navigation (U6)

`functional-design-questions.md`の確定回答に基づく、menu-navigationユニットの業務ルール。ルールIDは`BR6.y`(グループ6 = menu-navigation、Unit ID番号に対応)とする。

```yaml
rules:
  - id: BR6.1
    statement: >
      業務メニュー配下にマスタメンテのメニューをN階層(制限なし)、管理メニュー配下に管理者機能
      (業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)を配置する。パンくずリストは設けない。
    category: constraint
    applies_to: [MenuItem]
    trigger: N/A(恒久的な制約)
    logic: N/A
    violation_behaviour: N/A
    source: "FR7.1"

  - id: BR6.2
    statement: >
      管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)はMenuItemエンティティ
      として永続化せず、アプリケーションコードに固定配列としてハードコードする。表示名・順序とも
      config-import-export経由の変更対象にはしない。
    category: constraint
    applies_to: [MenuItem]
    trigger: N/A(恒久的な制約)
    logic: N/A
    violation_behaviour: N/A
    source: "FR7.1。functional-design-questions.md Q8=A"

  - id: BR6.3
    statement: >
      メニュー項目ごとの表示可否判定に用いるscreenKeyは、項目の種別により以下のとおり決定する。
      (1) 業務メニューのリーフ項目(targetTableConfigId != null): targetTableConfigIdの値を
      screenKeyとしてpermission-engineへ問い合わせる。
      (2) 業務メニューのフォルダ項目(targetTableConfigId == null): 個別の権限判定は行わず、
      BR6.4に従い配下リーフの可視性から再帰的に導出する。
      (3) 管理メニュー「ユーザ管理」: 予約screenKey"user-management"。
      (4) 管理メニュー「監査ログ管理」: 予約screenKey"audit-log"。
      (5) 管理メニュー「業務メニュー設定」「設定管理」: いずれも予約screenKey
      "config-import-export"を共有する(schema-introspectorが自身の画面に対して既に確定済みの
      screenKeyと同一のものを用いる。両画面は同一の権限スコープに属する)。
    category: authorization
    applies_to: [MenuItem]
    trigger: "GET /api/menuの呼び出し時"
    logic: >
      IF リーフ項目 THEN PermissionEngine.canAccessScreen(activeRoleId, targetTableConfigId)
      ELSE IF フォルダ項目 THEN BR6.4に委譲
      ELSE IF 管理メニュー項目 THEN 対応する予約screenKeyでcanAccessScreenを呼び出す
    violation_behaviour: "非表示(除外)とする。エラーを返さない"
    source: "FR7.2。functional-design-questions.md Q1=A(選び直し)・Q4"

  - id: BR6.4
    statement: >
      フォルダ項目(targetTableConfigId == null)自体に対する個別の権限判定は行わない。配下
      (子孫)に1件でも表示可能なリーフ項目があれば、そのフォルダを再帰的に表示する。配下が全て
      非表示ならフォルダ自体も非表示にする。
    category: authorization
    applies_to: [MenuItem]
    trigger: "GET /api/menuの呼び出し時、フォルダ項目の可視性判定時"
    logic: "isVisible(folder) = OR(isVisible(child) for child in folder.children)"
    violation_behaviour: N/A(本ルール自体が判定方法を定める)
    source: "functional-design-questions.md Q3=A"

  - id: BR6.5
    statement: >
      `GET /api/menu`は権限フィルタ後の結果(businessMenu・adminMenuとも空配列を含む)をそのまま
      返す。「権限のあるメニューが1件もない」場合の案内メッセージ表示(FR7.3)は、businessMenu・
      adminMenuの両方が空である場合の判定も含め、フロントエンド側(frontend-ui)の責務とする。
      menu-navigation側で明示的な空フラグ(isEmpty等)は追加しない。
    category: policy
    applies_to: [MenuItem]
    trigger: "GET /api/menuの呼び出し時"
    logic: N/A(フロントエンド側の責務を明記する制約)
    violation_behaviour: N/A
    source: "FR7.3。functional-design-questions.md Q5=A"

  - id: BR6.6
    statement: >
      同一階層内(同一parentMenuItemIdを持つ兄弟項目間)の表示順は`order`昇順とする。`order`値の
      重複は許容し、重複時の同点順序は未規定(安定ソートに委ねる)とする。起動時・設定投入時の
      一意性検証は行わない。
    category: policy
    applies_to: [MenuItem]
    trigger: "メニュー階層の構築・レスポンス生成時"
    logic: "children配列をorder昇順でソートする"
    violation_behaviour: N/A
    source: "functional-design-questions.md Q6=A"

  - id: BR6.7
    statement: >
      MenuItem.targetTableConfigIdが指すTableConfig(ConfigEngine管理)が存在しない場合、
      `GET /api/menu`は当該MenuItemを実行時に結果から除外する。起動時のfail fast検証は行わない
      (設定投入時の整合性検証はconfig-import-export側のインポート時検証に委ねる)。
    category: constraint
    applies_to: [MenuItem]
    trigger: "GET /api/menuの呼び出し時、リーフ項目のtargetTableConfigId解決時"
    logic: "IF TableConfig(targetTableConfigId)が存在しない THEN 当該MenuItemを結果から除外する"
    violation_behaviour: "結果から除外(非表示)。エラーを返さない"
    source: "functional-design-questions.md Q7=A"

  - id: BR6.8
    statement: >
      業務メニュー(MenuItem階層)の作成・更新・削除は、menu-navigation自身が提供する
      `POST/PUT/DELETE /api/menu-items`で行う。実効権限はサーバー側で必ず再検証し
      (project.md Mandated)、screenKey"config-import-export"でPermissionEngine.canAccessScreen
      を呼び出す。拒否時は403を返す。作成・更新時にtargetTableConfigIdを指定する場合は
      ConfigEngine側の存在確認を行い、存在しなければ400を返す。
    category: authorization
    applies_to: [MenuItem]
    trigger: "POST/PUT/DELETE /api/menu-itemsの呼び出し時"
    logic: >
      IF PermissionEngine.canAccessScreen(activeRoleId, "config-import-export") = false
      THEN 403 Forbiddenを返す
      ELSE IF (POST/PUT) AND targetTableConfigIdが指定されておりConfigEngine側に存在しない
      THEN 400 Bad Requestを返す
      ELSE 作成・更新・削除を実行する
    violation_behaviour: "403 Forbidden(権限不足)または400 Bad Request(targetTableConfigId不整合)、
      いずれもRFC 9457形式のProblemDetailsを返す"
    source: "契約設計追補(C3)。functional-design-questions.md Q2=B・Q10=A。project.md Mandated
      (実効権限のサーバー側再検証)"

  - id: BR6.9
    statement: >
      トップ画面のCard表示は業務メニューの第1階層(parentMenuItemId == null)のグループ単位で
      行う(`inception/refined-mockups/mockups.md`で既に確定済みの表示仕様)。menu-navigationは
      `GET /api/menu`が返す階層構造(childrenを含むMenuItemツリー)をそのまま提供すればよく、
      カードクリック時の遷移のために追加のAPI・属性は不要とする。
    category: policy
    applies_to: [MenuItem]
    trigger: N/A(恒久的な制約)
    logic: N/A
    violation_behaviour: N/A
    source: "FR7.2。functional-design-questions.md Q9=A"
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR6.1 | constraint | 業務メニューN階層・管理メニュー4項目、パンくずリストなし |
| BR6.2 | constraint | 管理メニュー4項目はハードコード(MenuItem非永続化) |
| BR6.3 | authorization | 項目種別ごとのscreenKey決定規則 |
| BR6.4 | authorization | フォルダの表示可否は配下リーフから再帰的に導出 |
| BR6.5 | policy | 空メニュー判定・案内表示はフロントエンド側の責務 |
| BR6.6 | policy | 兄弟項目はorder昇順、重複時の同点順序は未規定 |
| BR6.7 | constraint | 参照先TableConfig欠損時は実行時除外(fail fastにしない) |
| BR6.8 | authorization | MenuItem CRUD APIの認可・バリデーション |
| BR6.9 | policy | トップ画面カード表示は第1階層グループ単位、追加対応不要 |
