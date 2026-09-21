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

# Security Requirements — config-import-export (U9)

`inception/requirements-analysis/requirements.md`のNFR2、`team.md`・`project.md`(Forbidden・Mandated)、`nfr-requirements-questions.md`の確定回答(Q2・Q3)、機能設計(`rules.md` BR9.4・BR9.11・BR9.12・BR9.16・BR9.19・BR9.20)に基づく、config-import-exportユニットのセキュリティ要件。

## NFR2.1: 認可(サーバー側の実効権限の再検証)

`GET /api/config/export`と`POST /api/config/import`は、認証済みの操作者(authentication-serviceが`OperatorContext`に設定)だけが呼べる。操作者が解決できなければ401、`canAccessScreen(activeRoleId, "config-import-export")`(C10)が偽なら403を返す(functional-design rules.md BR9.4)。判定はサーバー側で必ず行い、クライアント側UIの出し分けだけに依存しない(project.md Mandated)。初期状態(主権限が0件)の例外は、permission-engineのBR3.13に従う。

## NFR2.2: リクエスト本体の大きさと、JSONの読み取りの制限(受け入れたリスク)

```
NFR2.2: リクエスト本体の大きさの上限は設けない(受け入れたリスク)
要件: インポートのリクエスト本体の大きさ、および、JSONの入れ子の深さ・重複するプロパティの扱いには、本ユニット独自の上限・制限を設けない。フレームワーク(Spring Boot・Jackson)の既定の挙動に従う
根拠: Q2=C。機能設計の受け入れたリスク(BR9.19)を維持する。インポートは、認証済みで、`config-import-export`の権限を持つ操作者に限られる
```

- **残余リスク**: 巨大なファイルや深い入れ子のJSONで、メモリや処理時間が使い果たされうる。誤操作や、乗っ取られた管理者のアカウントによる悪用が考えられる。想定規模(NFR1.3)は数MB以下であり、数十名規模・単一インスタンス(NFR3)では、管理者の操作に限られるため、MVPでは受け入れる。
- **引き継ぎ**: NFR Designで、フレームワークの既定の挙動(JSONの読み取りの既定の制限、`spring.servlet.multipart`などの既定値)を確認し、既定で成り立つ範囲を記録する。本体を全件メモリに読み込む方式であることを、設計に明記する。

## NFR2.3: 権限昇格の防止(project.md Forbiddenの充足)

project.md Forbidden「権限の昇格(自分自身への昇格を含む)を、権限管理者による明示的な操作を経ずに許可しない」を、次で満たす(functional-design rules.md BR9.11・BR9.12)。

- 権限昇格の判定は、取り込み開始時点の設定を基準に、すべてのRBACエントリについて行い、1件でも昇格が検出されたら、何も反映せず422で拒否する。
- 主権限が0件になる設定ファイルは拒否する(初期状態の例外の再有効化を防ぐ)。
- 上記の判定は、permission-engineの検証専用メソッドが行う(判定の基準は、操作者のactiveRoleIdの、取り込み開始時点の実効権限)。

## NFR2.4: 検証から反映までの間の競合(受け入れたリスク)

```
NFR2.4: 検証から反映までの間の競合への対策は設けない(受け入れたリスク)
要件: 検証から反映までの間に、操作者の権限や初期状態が変わったことの再確認は、行わない。取り込みどうしの排他も設けない
根拠: Q3=A。機能設計の受け入れたリスク(BR9.19)を維持する
```

- **残余リスク**: (a)2人の管理者がほぼ同時に取り込むと、後から確定した方が勝つ。(b)検証の途中で、別の管理者が操作者の権限を取り消しても、開始時点の判定に基づく取り込みが反映されうる。(c)取り込み開始時点の初期状態(`bootstrapAtStart`)が、反映の時点では成り立たなくなっていても、反映されうる。
- **緩和**: 取り込みの結果は、監査イベント(BR9.16)で、後から追跡できる。運用の手順として、取り込みは、1人の管理者が、他の権限変更と重ならない時間に行うことを勧める。
- **引き継ぎ**: このリスクは、アーキテクチャレビュー(機能設計、iteration 1)の指摘R-03で提示された。Code Generationのplan承認、および、ステージ全体の承認ゲートで、人間に再提示する。

## NFR2.5: 機微情報の非出力

- エクスポートする設定には、ユーザー・認証情報(パスワードのハッシュ、トークン、招待トークン)・監査ログ・業務データは含まれない(functional-design rules.md BR9.1)。
- 設定ファイルの内容(ロール名・権限・翻訳などの値)は、ログにもメトリクスのラベルにも出さない(NFR5.2)。
- 検証の誤りの個々の内容(位置・メッセージのキー)は、応答にだけ含め、ログには出さない(NFR5.2)。
- project.md Mandated「パスワード等の認証情報を平文でログ・監査ログ・エラーメッセージに出力しない」に抵触する情報は、本ユニットが扱うデータに含まれない。

## NFR2.6: エラーレスポンスの情報開示制御

401・403・422・500・503のレスポンスは、RFC 9457形式のProblemDetailsとし、開発者向けのスタックトレースや内部実装の詳細(SQL文、内部設定DBの接続情報等)を含めない。422の`errors[]`は、フィールド単位(JSON上の位置とi18nキー)で返す(functional-design rules.md BR9.7・BR9.21、project.md Mandated)。

## NFR2.7: セキュリティ関連CIゲート

`team.md`の既定(SAST・シークレットスキャンをマージ前のCIブロッキングチェックとして導入)をそのまま適用する。本ユニット固有の追加ゲートはない。依存関係の脆弱性スキャンは、意図的に対象外(`team.md`の既定)。
