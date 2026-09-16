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

# Entities — menu-navigation (U6)

```yaml
entities:
  - name: MenuItem
    description: >
      業務メニュー(N階層)を構成する1項目。フォルダ(中間階層、targetTableConfigId=null)と
      リーフ(targetTableConfigId!=null、一覧・編集画面への遷移先)の両方を同じエンティティで表す。
      `inception/domain-design/components.md`のMenuNavigationコンポーネント定義に1:1対応する。
      管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)はMenuItemとして
      永続化せず、アプリケーションコードに固定でハードコードする(functional-design-questions.md
      Q8、下記「Domain Designからの逸脱」参照)。
    attributes:
      - name: menuItemId
        type: string
        required: true
        unique: true
      - name: parentMenuItemId
        type: string
        required: false
        default: null
        references: MenuItem
        description: >
          親MenuItemのID。ルート直下の項目はnull。自己参照によりN階層の木構造を表現する。
      - name: label
        type: string
        required: true
        description: メニュー項目の表示名。
      - name: order
        type: integer
        required: true
        description: >
          同一階層内(同一parentMenuItemIdを持つ兄弟項目間)での表示順。昇順でソートする。
          一意性は要求しない(functional-design-questions.md Q6=A、重複時の同点順序は未規定)。
      - name: targetTableConfigId
        type: string
        required: false
        default: null
        description: >
          遷移先のTableConfig(ConfigEngine管理)のID。nullの場合はフォルダ(中間階層、子を束ねる
          だけの項目)を表す。非nullの場合はリーフ(list-engine/record-edit-engineへの遷移先)を表す。
    constraints:
      - parentMenuItemIdを持つ場合、参照先のMenuItemが存在しなければならない(木構造の整合性)。
      - targetTableConfigIdが指すTableConfigが存在しない場合、`GET /api/menu`では当該MenuItemを
        実行時に結果から除外する(fail fastにはしない。functional-design-questions.md Q7=A)。
      - MenuItemの作成・更新時(`POST/PUT /api/menu-items`)のみ、指定されたtargetTableConfigId
        がConfigEngine側に存在することを検証し、存在しなければ400を返す(functional-design-questions.md
        Q10=A)。
    relationships:
      - target: MenuItem
        cardinality: 0-or-1-to-many
        direction: 1つのMenuItem(親)は0件以上のMenuItem(子)を持つ(自己参照による木構造)
        description: parentMenuItemIdによる親子関係。ルート直下の項目はparentMenuItemId=null。
      - target: TableConfig
        cardinality: 0-or-1-to-1
        direction: MenuItem may reference one TableConfig(owned by ConfigEngine)via targetTableConfigId
        description: >
          targetTableConfigIdが非nullの場合のみ、その値はConfigEngineが所有するTableConfigの
          tableConfigIdを指す。永続化上の外部キー制約は設けず、不透明な文字列参照として扱う
          (TableConfig自体は本ユニットの管轄外)。
```

## human-readable summary

menu-navigationは単一のエンティティ`MenuItem`のみを保持する。`MenuItem`は自己参照によりN階層の木構造を表現し、`targetTableConfigId`の有無で「フォルダ」(中間階層)と「リーフ」(一覧・編集画面への遷移先)を区別する。管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)はシステム固定の画面であり`MenuItem`として永続化しない(functional-design-questions.md Q8)。

## Domain Designからの逸脱(要追補)

1. **管理メニューの非永続化**: `inception/domain-design/components.md`のMenuNavigationコンポーネント定義はエンティティを`MenuItem`のみとしており、管理メニュー4項目の扱いを明示していなかった。本機能設計で、管理メニューはアプリケーションコードへの固定ハードコードとし`MenuItem`テーブルへは記録しないことを確定した(functional-design-questions.md Q8=A)。データモデル自体の追補は不要(Domain Designの`MenuItem`定義と矛盾しない、対象範囲の明確化)。
