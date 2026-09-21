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

# Security Design — config-import-export (U9)

`nfr-requirements/security-requirements.md`(NFR2.1〜NFR2.7)と、`nfr-design-questions.md`の確定回答(Q3=B)、`team.md`・`project.md`(Forbidden・Mandated)に基づく、config-import-exportユニットのセキュリティ設計。

## NFR2.1: 認可のアーキテクチャ

```
POST /api/config/import
  Spring Security(authentication-service): アクセストークンの検証 → OperatorContextに操作者を設定
  Spring MVC: @RequestBody JsonNode の束縛(本体の読み取りと構文の解析)   ← Q3=B: 認可より前
     束縛の失敗(構文の誤り・空の本体・Content-Typeの不一致)は、コントローラーに入る前に例外になる
     → ConfigImportExceptionHandler(このコントローラー専用)が、「認可 → 422(MALFORMED)」の順で処理する(下の「Q3=Bの帰結: 構文の誤りの経路」)
  ConfigImportController:
     1. OperatorContextから操作者を得る。解決できなければ401
     2. permissionEngine.canAccessScreen(activeRoleId, "config-import-export")。偽なら403
     3. ConfigImportServiceを呼ぶ
GET /api/config/export
  同じ。ただし本体はないため、1・2の後に、ConfigExportServiceを呼ぶ
```

- 認可の判定は、サーバー側で必ず行う(project.md Mandated)。クライアントの画面の出し分けには依存しない。
- 401と403は、RFC 9457のProblemDetailsで返す。
- `activeRoleId`がnullでも、自前で拒否せず、そのままC10に渡す(初期状態の例外を、permission-engineに一元化するため。BR9.4)。

## NFR2.2: 本体の大きさ・JSONの読み取りの制限(受け入れたリスク)と、Q3=Bによる追記

- 本ユニット独自の上限・制限は設けない(Q2=C)。JSONの読み取りは、Spring Bootが既定で使う**Jackson 3系**(`tools.jackson.databind`)の、既定の制限に従う。
- **Jackson 3を使う根拠**: Spring Boot 4.1.1が、`spring-boot-starter-web`の既定で構成するのは、Jackson 3系(`tools.jackson`、キャッシュ上は3.1.x)である。既存の`build.gradle.kts`には、Jackson 2系(`com.fasterxml.jackson.core:jackson-databind`)への明示的な依存があるが、既存の**本体のコード**は、`com.fasterxml.jackson.annotation`(アノテーション。Jackson 3も同じパッケージを使う)だけを使っており、Jackson 2のdatabind(`ObjectMapper`・`JsonNode`)の使用は確認できなかった。ただし、**テストのコード**(`ProblemDetailsWriterTest`)は、`com.fasterxml.jackson.databind.JsonNode`・`ObjectMapper`をimportしている(アーキテクチャレビュー iteration 1のR-10)。本ユニットは、Jackson 3の`JsonNode`を使う。本ユニットは、Jackson 2への依存を使わない。既存の明示的なJackson 2への依存の要否は、テストの依存を含めて、Code Generationで確認する(logical-components.md 追補6)。
- **既定の制限の値は、Code Generationで確認する**: Jackson 3.1.xの、入れ子の深さ・文字列の長さ・重複するプロパティの扱いの、既定の挙動は、本設計の時点では、値を断定しない。Code Generationで、使用するバージョンの実際の挙動を、テストで確認して固定する(重複するプロパティが、黙って上書きされる場合は、その旨を、既知の挙動として記録する)。要件は、制限を設けないこと(Q2=C)であり、制限の値そのものは、要件ではない。
- **Q3=Bによる、残余リスクの追記(NFR Requirementsのレビュー指摘R-02への対応)**:
  - 通常の`@RequestBody`の束縛は、コントローラーのメソッドが呼ばれる前に、本体を全件読み込んで解析する。そのため、認可の判定は、本体の読み取りより後になる。
  - 結果として、認証済みだが、`config-import-export`の権限を持たない利用者も、巨大な本体を送って、メモリを消費させられる余地が残る。NFR2.2の前提「権限を持つ操作者に限られる」は、**認証済みの利用者(数十名規模の社内の利用者)まで**に、訂正する。
  - 受け入れる理由: 社内の、認証済みの利用者に限られる。数十名規模・単一インスタンスである(NFR3)。想定規模の本体は、数MB以下である。
  - 緩和: 設けない(Q2=C)。将来、本体の大きさの上限を設ける場合は、フォーム用・マルチパート用の既存の設定項目は、JSONの本体には効かない可能性があるため、リクエストの大きさを制限するフィルター(または、サーバー・リバースプロキシ側の設定)で行うことを、Code Generation以降で、検討する。

- **Q3=Bの帰結: 構文の誤りの経路(アーキテクチャレビュー iteration 1のR-05への対応)**:
  - 通常の`@RequestBody`の束縛が失敗した場合(構文の誤り・空の本体・Content-Typeの不一致)、Springは、コントローラーのメソッドに入る前に、`HttpMessageNotReadableException`・`HttpMediaTypeNotSupportedException`などを投げる。既定の応答は400であり、機能設計(BR9.6・BR9.7)の422(RFC 9457、`errors[]`)と食い違う。
  - **設計**: `ConfigImportExceptionHandler`を、このコントローラー専用(`@ControllerAdvice(assignableTypes = ConfigImportExportController.class)`)とし、上記の束縛の例外を、次の順序で処理する。(1)コントローラーと**同じ認可の処理**(`ConfigImportAuthorizer`。操作者の解決→`canAccessScreen`)を先に行い、失敗したら、401・403を返す(構文の誤りの詳細を、権限のない利用者に返さない)。(2)認可に成功した場合だけ、422(`failureCategory`=MALFORMED、または、Content-Typeの不一致・空の本体は、同じくMALFORMED)を返し、失敗の監査イベント(MALFORMED)を発行する。
  - これにより、機能設計の順序「認可(401・403)→構文・形式(422)」が、**応答の上では**保たれる。権限のない利用者は、不正な本体を送っても、監査ログに行を追加できない(監査イベントは、認可の後にのみ発行する)。
  - 差分として残るのは、認可の前に、本体が読み込まれる点だけである(上のメモリの消費の残余リスク)。

## NFR2.3: 権限昇格の防止

project.md Forbidden「権限の昇格(自分自身への昇格を含む)を、権限管理者による明示的な操作を経ずに許可しない」を、次の設計で満たす。

1. **判定の位置**: 取り込みの検証の段階(反映の前)で、permission-engineの検証専用メソッドが、すべてのRBACエントリについて、操作者(activeRoleId)の、取り込み開始時点の実効権限を基準に、昇格を判定する(BR9.11)。
2. **拒否の単位**: 1件でも昇格が検出されたら、何も反映せず、422で拒否する。集めた誤りは、全件を返す(最大100件)。
3. **初期状態の例外**: 主権限が0件の初期状態(`bootstrapAtStart`=true)では、初回のRBACの投入のために、昇格の判定を行わない。ただし、投入後に主権限が0件となるファイルは拒否する(BR9.12)。
4. **限界(NFR2.4)**: 検証から反映までの間の競合は、対策を設けていない。

## NFR2.4: 検証から反映までの間の競合(受け入れたリスク)と、緩和の正直な評価

NFR Requirementsのレビュー指摘R-01のとおり、NFR2.3を、競合がある場合にも「満たす」と断定することはできない。次のとおり、正直に記録する。

| 競合の内容(NFR Requirementsの(a)(b)(c)と同じ番号) | 起こりうる結果 | project.md Forbidden・Mandatedとの関係 |
|---|---|---|
| (a)2人の管理者が、同時に取り込む | 行が重ならなければ、両方が確定する(全置換のため、後から確定した方の内容が、重なる範囲で残る)。行が重なる場合は、後から書き込む側が、更新の競合として失敗し、503になる(reliability-design.md NFR4.1・NFR4.6。`REPEATABLE_READ`の副産物) | 権限昇格には当たらない |
| (b)検証の途中で、別の管理者が、操作者のロールを取り消した | 取り込みは、開始時点(スナップショット)の権限に基づいて、反映される | 「権限管理者による明示的な操作」として、取り消しの操作は行われている。取り消しの後にも、開始時点の判定による反映が起こる。昇格ではなく、取り消しの遅れである |
| (c)取り込み開始時点の初期状態(`bootstrapAtStart`=true)が、反映の時点では成り立たなくなっている | 昇格の判定を行わずに、反映される。他の管理者が、主権限を投入していた場合、本来なら昇格として拒否されたRBACの変更が、反映されうる(行が重ならない場合は、更新の競合としても検出されない) | 昇格の経路になりうる。ただし、初期状態は、システムの立ち上げの直後の、ごく短い期間に限られ、その間に、権限管理者が2人、同時に操作する状況が前提になる |

- **緩和(過大な評価を訂正)**: 「監査イベントで、後から追跡できる」とは言えない。取り込みの監査イベント(ConfigImportExecutedEvent)は、操作者・日時・結果・セクションごとの件数・失敗の分類を記録するが、対象(どのロールにどの権限を与えたか)と、変更の前後の値は、記録しない(機能設計 BR9.16・Q9)。個別のイベント(ConfigChangedEvent・PermissionChangedEvent)の`actor`は、取り込みでは`"system"`になる(BR9.15)。追跡できるのは、「誰が、いつ、取り込みを実行したか」までである。project.md Mandatedの「変更前後の値の記録」を、取り込みについては、満たしていない(機能設計のレビュー指摘R-05と同じ。人間の判断で、受け入れられている)。
- **運用の緩和**: 取り込みは、1人の管理者が、他の権限変更と重ならない時間に行う。取り込みの前に、エクスポートと、内部設定DBのファイルの退避を行う(NFR4.7の設計)。
- **引き継ぎ**: このリスクは、Code Generationのplan承認と、ステージ全体の承認ゲートで、機能設計のレビュー指摘(R-03・R-05)とあわせて、人間に再提示する。

## NFR2.5: 機微情報の非出力

| 対象 | 設計 |
|---|---|
| エクスポートの内容 | ユーザー・認証情報・監査ログ・業務データを含めない(BR9.1)。ロール・権限・スキーマ・翻訳・メニューは、業務の構成情報であり、管理者だけが取得できる(NFR2.1) |
| ログ | 設定ファイルの内容・ファイル名・検証の誤りの個々の内容を出さない(NFR5.2) |
| 検証の誤りの応答 | `field`(JSON Pointer)・`message`(i18nキー)・`params`(項目の名前・許容値・上限など、内容そのものではない値)のみ。入力された値の全体は、応答に含めない |
| エクスポートの監査 | エクスポートは、監査イベントを発行しない(BR9.16)。設定の構成情報の取得は、記録に残らない(機能設計で受け入れ済み) |

## NFR2.6: エラーレスポンスの設計

- 401・403・422・500・503は、RFC 9457のProblemDetailsで返す(既存のグローバルな例外の処理と同じ)。
- 500・503のレスポンスに、スタックトレース・SQL文・内部設定DBの接続情報を含めない。詳細は、ERRORのログにだけ出す(NFR5.2)。
- 422の`errors[]`は、フィールド単位(JSON Pointerとi18nキー)で返す。

## NFR2.7: セキュリティ関連CIゲート

`team.md`の既定(SAST・シークレットスキャンをマージ前のCIブロッキング)を、そのまま適用する。本ユニット固有のゲートはない。

## 脅威の整理(STRIDE、本ユニット固有の観点)

| 分類 | 脅威 | 対策 | 残余 |
|---|---|---|---|
| Spoofing | 他人の権限での取り込み | 認証はauthentication-service。操作者は`OperatorContext`から取る(クライアントが指定できない。project.md) | なし |
| Tampering | 悪意のある設定ファイルによる、権限の書き換え | 検証(構造・参照整合・昇格の判定・主権限0件の拒否)と、全体を1つのトランザクションで反映 | NFR2.4の競合 |
| Repudiation | 取り込みの否認 | 監査イベント(操作者・日時・結果・件数) | 対象・変更前後の値は記録しない |
| Information disclosure | 設定の構成情報の取得 | `config-import-export`の権限での認可。エクスポートは監査しない | 記録に残らない(受け入れ済み) |
| Denial of service | 巨大な本体・深い入れ子による、メモリ・処理時間の枯渇 | 対策なし(Q2=C) | 認証済みの利用者に限られる(Q3=B。NFR2.2) |
| Tampering(識別子・表示文字列) | 取り込んだテーブル名・カラム名・表示名・翻訳・メニュー名が、業務データ用RDBMSへの動的なSQLの識別子、または、画面の表示に流れる | 本ユニットは、構造の検証(必須・型・長さ・許容値・一意性。BR9.6)までを行い、文字列の意味を解釈しない(BR9.20)。実在するスキーマとの照合は、config-engineの検証(BR1.1〜BR1.4)に依拠する。SQLの識別子の引用・エスケープは、業務データにアクセスする側(schema-introspector・data-import-export・後続のlist-engine・record-edit-engine)の実行時の責務であり、画面の表示のエスケープは、frontend-uiの責務(Reactの既定のエスケープに依拠) | 他ユニットの既存の対策への依拠であり、本ユニットの範囲では、確認できない。識別子の引用・エスケープの実装が、実際に有ることを、Code Generation(および後続のユニット)で確認する項目とする |
| Elevation of privilege | 自分自身への昇格 | NFR2.3 | NFR2.4(c) |
