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

# Reliability Requirements — config-import-export (U9)

`inception/requirements-analysis/requirements.md`のNFR4、`nfr-requirements-questions.md`の確定回答(Q3・Q4・Q6)、機能設計(`rules.md` BR9.5・BR9.10・BR9.16・BR9.21)に基づく、config-import-exportユニットの信頼性要件。

## NFR4.1: 取り込みの原子性(全体を1つの単位とする)

```
NFR4.1: 取り込みの原子性
要件: インポートは、全件の検証に合格した場合に限り、schema・ロール・グループ・メニュー・権限の反映を、1つのトランザクションで行う。いずれかの反映が失敗したら、全体をロールバックし、内部設定DBを、取り込みの前の状態に保つ(functional-design rules.md BR9.10)
効果: 設定が、部分的に置き換わった状態(たとえば、テーブルだけが新しく、権限が古い状態)で残ることを防ぐ
```

## NFR4.2: キャッシュの再構築とイベントの発行は、コミット後に限る

```
NFR4.2: 反映の副作用は、トランザクションの確定後にだけ行う
要件: 取り込みの反映に伴う、インメモリのキャッシュの再構築(config-engine・menu-navigation・permission-engine)と、変更イベントの発行は、トランザクションの確定後にだけ行う。ロールバックされた場合は、キャッシュにも、イベントにも、反映の内容を残さない
根拠: NFR4.1(原子性)の帰結。ロールバック後に、キャッシュへ未確定の内容が残ると、内部設定DBと、キャッシュが食い違う。permission-engineのサマリイベント(BR3.11)は、この扱いを、既に前提としている
```

- **引き継ぎ**: C9・C12の契約の追補(functional-spec.mdの追補一覧2・3番)は、反映するメソッドが、呼び出し元のトランザクションに参加し、自身でコミットしないことに加えて、キャッシュの再構築とイベントの発行を、コミット後(たとえば、トランザクション同期の`afterCommit`)に限ることを、明記する(アーキテクチャレビューの指摘R-08)。Code Generationで、対応する。

## NFR4.3: 取り込みの時間の上限と、内部設定DBの障害

```
NFR4.3: 取り込みの実行時の時間の上限は置かない
要件: 取り込みのトランザクションに、本ユニット独自の時間の上限は設けない。内部設定DB(H2)の既定のロック待ちのタイムアウトに任せる。取り込みが、NFR1.2の10秒を超えても、完了するまで続ける
内部設定DBの障害: 内部設定DBに、一時的につながらない、または、ロックの待ちがタイムアウトした場合は、全体をロールバックし、503(RFC 9457のProblemDetails)を返す。失敗の監査イベント(failureCategory=UNEXPECTED)を発行する(functional-design rules.md BR9.16・BR9.21)
根拠: Q4=B、Q6=A
```

- **残余リスク**: 長時間かかる取り込みが、内部設定DBのロックを保持し続け、他のリクエスト(設定の更新など)を待たせうる。想定規模(NFR1.3)では、10秒以内に収まる見込みであり、MVPでは受け入れる。

## NFR4.4: エクスポートの読み取りの一貫性

```
NFR4.4: エクスポートは、1つの時点として整合する内容を読む
要件: エクスポートは、読み取り専用のトランザクションで、スナップショットの分離を使い、取り込みの反映の途中の状態(たとえば、schemaは新しく、権限は古い状態)を読まない。3つのユニットからの読み取りが、同じ時点の内容であること(functional-design rules.md BR9.5)
根拠: Q4=B(読み取りの一貫性はスナップショットの分離。時間の上限は置かない)
```

- **引き継ぎ**: H2(バージョンはSpring Bootの依存管理に従う)が提供する分離レベルと、その指定の方法(JPAのトランザクションの`isolation`と、H2のMVCCの挙動)は、NFR Designで確認して確定する。取り込みの反映中に、エクスポートを待たせる方式(読み取り・書き込みのロック)は採らない(Q4の選択肢Cは不採用)。

## NFR4.5: 監査イベントの発行の失敗の扱い

取り込みの成功・失敗の監査イベント(ConfigImportExecutedEvent、BR9.16)は、fire-and-forgetで発行し、audit-loggingの記録の遅延・失敗が、取り込みの結果(応答)に影響しないようにする(audit-loggingのNFR4.2・NFR4.5と同じ)。記録の失敗は、audit-logging側の構造化ログに残る。

## NFR4.6: 取り込みどうしの競合(受け入れたリスク)

NFR2.4のとおり、取り込みどうしの排他、および、検証から反映までの間の再確認は設けない(Q3=A)。同時に取り込まれた場合は、後から確定した方が勝つ。デッドロックやロックの待ちのタイムアウトは、内部設定DBの既定の挙動に従い、失敗した側は、NFR4.3のとおり、503で失敗する。

## NFR4.7: 内部設定DBの永続化とバックアップ

内部設定DBは、ファイルモードで運用し、再起動後も設定が失われない(config-engineのNFR4.2)。取り込みは、全置換であり、取り込み前の状態に戻す専用の手段(元に戻す操作)は、本ユニットには持たない。取り込み前の設定は、事前に、エクスポート(NFR1.1)で保存しておくことで、復元できる(取り込みで、復元用のファイルを取り込む)。この運用の手順を、利用者向けの案内(frontend-uiの確認モーダルなど)に含めることを、frontend-uiへの要求として引き継ぐ。
