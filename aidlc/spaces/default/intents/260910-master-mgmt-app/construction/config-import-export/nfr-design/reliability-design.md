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

# Reliability Design — config-import-export (U9)

`nfr-requirements/reliability-requirements.md`(NFR4.1〜NFR4.7)と、`nfr-design-questions.md`の確定回答(Q1=C・Q2=A)に基づく、config-import-exportユニットの信頼性設計。アーキテクチャレビュー(iteration 1)の指摘R-01〜R-07・R-09に対応して改訂した。

## NFR4.1: 取り込みの原子性 — トランザクションの設計

- **トランザクションの境界**: `ConfigImportService.importConfig`は、`@Transactional`を付けず、プログラム的なトランザクション(`TransactionTemplate`。既存のdata-import-exportの`CsvImportService`と同じ流儀)で、反映の部分を包む。理由は、(a)失敗の監査イベントを、確実にトランザクションの外で発行するため(メソッド全体が1つのトランザクションだと、メソッド内の`finally`はトランザクションの内側になり、発行した記録が、取り込みと一緒にロールバックされる)。(b)コミット時の例外(確定の失敗)を、`catch`で捕まえて分類するため。

```
importConfig(request, operator):                       // トランザクションなし
   try:
      result = transactionTemplate.execute(tx -> {     // REPEATABLE_READ、REQUIRED
           検証 → 反映(3ユニット) → PostCommitCoordinatorをafterCommitへ登録
      })
      return result
   catch (検証の誤り / DBの例外 / その他):
      失敗の監査イベントを発行(REQUIRES_NEW、try-catchで包む)    // トランザクションは、すでに終了(ロールバック済み)
      例外を、ConfigImportExceptionHandlerへ
```

- **分離レベル(R-06への対応)**: 取り込みのトランザクションは、`REPEATABLE_READ`とする。根拠: (a)機能設計 BR9.11は、権限昇格の判定を、**取り込み開始時点の設定**を基準に行うとする。`READ_COMMITTED`では、約9回の一括の取得が、それぞれ、その時点の最新の確定を読み、途中で別の管理者の変更が確定すると、検証・差分の基準が、表の間・ユニットの間で、新旧の混ざったものになる(エクスポートで避けようとした問題と同じ)。`REPEATABLE_READ`なら、基準が、トランザクションの開始時点の1つのスナップショットになる。(b)`bootstrapAtStart`の判定も、同じスナップショットに基づく。**代償**: H2のMVCCでは、スナップショットの後に、別のトランザクションが、同じ行を更新して確定した場合、こちらが、その行を書き込もうとすると、更新の競合(`Concurrent update`)が検出され、失敗する(下のNFR4.3・NFR4.6)。行が重ならない同時の変更は、両方が確定する。この代償は、Q3=Aの「後から確定した方が勝つ」を、「重なった行では、後から書き込む側が、競合として失敗する」に、部分的に置き換える。ただし、これは、追加の対策(排他・再確認)ではなく、分離レベルの選択の副産物であり、Q3=Aの確定回答(対策を設けない)には反しない。
- **`bootstrapAtStart`の再確認(反映の直前)**: 採用しない(Q3=A)。`REPEATABLE_READ`により、判定の基準は、スナップショットで固定される。スナップショット後に、他のトランザクションが、主権限を投入した場合の、書き込みの競合は、上記のとおり、行が重なれば検出されるが、重ならない場合(別の行の追加)は、検出されない。この窓は、NFR2.4(c)に記録する。
- **反映の順序と、`flush()`(R-07への対応)**: 削除を、依存の逆順で先に行い、追加・更新を、依存の順で行う(機能設計のレビュー指摘R-06への対応)。**段階の境界で、明示的に`flush()`する**(または、JPQL・JDBCの一括削除を使う)。Hibernateは、`flush`の際、挿入→更新→削除の順に実行するため、`flush()`なしでは、メニュー項目の全件の削除→再作成(BR9.14)のような、同じ自然キーの、削除と挿入が同じ段階に並ぶ場合に、一意制約に違反しうる。

| 段階 | 順序 | 内容 |
|---|---|---|
| 削除 | 1 | 補助権限・主権限(ファイルにない対象・ロールのもの)の削除。`flush()` |
| 削除 | 2 | メニュー項目の全件の削除(全置換のため、追加で作り直す。BR9.14)。`flush()` |
| 削除 | 3 | グループとロールの対応・グループ・ロールの削除(ファイルにないもの)。`flush()` |
| 削除 | 4 | カラム・テーブル・翻訳の削除(ファイルにないもの)。`flush()` |
| 追加・更新 | 5 | テーブル・カラム・翻訳(schema。`isPrimaryKey`は維持。BR9.9)。`flush()` |
| 追加・更新 | 6 | ロール・グループ・グループとロールの対応。`flush()` |
| 追加・更新 | 7 | メニュー項目(親→子の順に採番。遷移先のテーブルは、手順5の結果で解決)。`flush()` |
| 追加・更新 | 8 | 主権限・補助権限(対象は、手順5の結果で解決。操作者のactiveRoleIdを、actorRoleIdとして渡す)。`flush()` |

- **外部キー制約と削除の順序**: 内部設定DBの表に、外部キー制約がある場合、この順序で、制約に違反しない。制約のない表(論理的な参照だけ)でも、順序に従う。
- **ロールバック**: 例外が起きたら、`TransactionTemplate`が、全体をロールバックする。反映の途中の内容は、内部設定DBに、残らない。

## NFR4.2: 反映の副作用は、確定後に、無効化とイベントだけ行う(Q1=C)

NFR Requirementsは、キャッシュの「再構築」を確定後に行う、としていた。Q1=Cにより、これを改め、**キャッシュを、確定後に「無効化」し、次の読み取りで、内部設定DBから遅延して読み込む**方式にそろえる。

### 確定後の処理は、1つの調整役にまとめる(R-02への対応)

Springは、`afterCommit`の同期を、登録順に呼び、途中の同期が例外を投げると、後続の同期を呼ばずに、例外を呼び出し元へ伝える。そのため、3ユニットが、それぞれ`afterCommit`を登録する方式は採らない。

- 3ユニットの反映するメソッドは、`afterCommit`を**登録しない**。代わりに、`PostCommit`(`invalidateCaches`と`publishEvents`の、2つの動作)を、戻り値に含めて返す。
- `ConfigImportService`が、`PostCommitCoordinator`という、**1つだけ**の`TransactionSynchronization`を登録する。`afterCommit`では、次の順序で、固定して実行する。各動作は、それぞれ独立に、try-catchで包み、例外は、ERRORのログに出して、次へ進む。

| 順 | 動作 | 失敗の扱い |
|---|---|---|
| 1 | config-engineの`invalidateCaches` | ERRORのログ。次へ進む |
| 2 | menu-navigationの`invalidateCaches` | 同上 |
| 3 | permission-engineの`invalidateCaches` | 同上 |
| 4 | config-engineの`publishEvents`(個別の変更イベント) | 同上 |
| 5 | menu-navigationの`publishEvents` | 同上 |
| 6 | permission-engineの`publishEvents`(サマリイベント、BR3.11) | 同上 |
| 7 | 取り込みの成功の監査イベント(ConfigImportExecutedEvent) | 同上 |

- **無効化を、イベントより先に、すべて行う**: イベントのリスナー(audit-loggingなど)が、キャッシュを読む場合に、古い内容を読まないようにするため。
- **失敗しえない無効化**: `invalidate()`は、フラグと世代番号の更新だけで、DBにも、他のキャッシュにも触らない。したがって、順1〜3は、通常、例外を投げない。try-catchは、万一への備えである。
- **ロールバック**: `afterCommit`は呼ばれない。キャッシュは有効なまま(DBも変わっていないため、食い違わない)。
- **検証するテスト(Code Generationへ引き継ぐ)**: (a)1つの動作の例外が、後続の動作を妨げない。(b)3つの無効化が、すべてイベントより先に実行される。

### 各ユニットのキャッシュの共通の契約(R-03・R-04への対応)

config-engine・menu-navigationは、全件を保持するキャッシュである。permission-engineは、Caffeine(キーごとの遅延計算)であり、構造が異なるため、別に定める。

**config-engine・menu-navigation(全件のキャッシュ)**

| 項目 | 内容 |
|---|---|
| 状態 | `VALID`・`STALE`。加えて、**世代番号**(単調増加のカウンター、`AtomicLong`)を持つ。起動時は、既存のとおり、全件を読み込んで`VALID`にする |
| `invalidate()` | 世代番号を進め、状態を`STALE`にする。フラグの設定だけで、失敗しえない |
| 読み取りの経路 | 状態が`STALE`なら、排他のロックを、**待ちの上限つき**(`reload-wait-timeout`、既定5秒)で取る。取れなければ、503。取れたら、もう一度状態を確認し(二重の確認)、なお`STALE`なら、再読み込みを行う。再読み込みは、開始時の世代番号(g0)を控え、内部設定DBから全件を読み、**終了時に、世代番号がg0のままの場合に限って**、キャッシュを置き換えて`VALID`にする。世代が進んでいた場合(読み込み中に、別の取り込み・個別の更新が起きた)は、`VALID`にせず、`STALE`のままにする(読んだ内容は、その呼び出しには返す。次の読み取りが、再び再読み込みを行う) |
| 再読み込みのトランザクション | **独立した、読み取り専用のトランザクション**(`REQUIRES_NEW`、`readOnly`)で行う。呼び出し元のトランザクションに参加せず、呼び出し元の未確定の内容を読まない・キャッシュに載せない。取り込みの検証の途中に、`STALE`のキャッシュを読む場合も、確定済みの内容だけを読む(取り込み自体は、現在の設定を、内部設定DBから直接読む。performance-design.md) |
| 再読み込みの失敗 | 失敗は、待っているスレッドにも伝える。失敗の後、**抑制の期間**(`reload-failure-backoff`、既定2秒)の間は、後続の読み取りは、再読み込みを試みず、すぐに503を返す(再読み込みを、間引く)。期間が過ぎたら、1つのスレッドが再試行する。これにより、DBが不通の間、待っているスレッドが、直列に、1つずつ、接続・ロックのタイムアウトまで待つ連鎖を、防ぐ |
| DBの読み取りの時間の上限 | 接続の取得(Hikariの`connectionTimeout`)と、H2のロック待ち(2000ms)による。ロックを保持する間の待ちの上限は、上記の`reload-wait-timeout`で切る |
| 個別の更新(既存の書き込みの経路) | `VALID`のときは、既存の方式(そのインスタンスのキャッシュを更新)のまま。`STALE`のとき、および、更新の最中に世代が進んだときは、キャッシュを更新せず、`invalidate()`を呼ぶ(世代を進める)。これにより、更新が、読み込み中の再読み込みに上書きされて失われることを、防ぐ |

**permission-engine(Caffeine、キーごとの遅延計算)**

- 「全件の再読み込み」は新設しない。`invalidate()`は、**世代番号を進め、`invalidateAll()`を呼ぶ**ことに読み替える。
- キャッシュの値は、計算を始めた時点の世代番号を、値に持たせる。読み取りで取り出した値の世代番号が、現在の世代と異なる場合は、破棄して、再計算する。これにより、`invalidateAll()`の後に、進行中の計算が、古い値を格納しても、読み取りに使われない(Caffeineの、進行中のロードと`invalidateAll()`の競合への対策)。
- 再計算(ロード)は、独立した、読み取り専用のトランザクション(`REQUIRES_NEW`、`readOnly`)で行う。失敗は、その読み取りの503。Caffeineが、キーごとに、単一のロードを保証する。全体のロックは持たないため、ブロックの連鎖は、起きない。

**共通のテスト(Code Generationへ引き継ぐ)**: スレッドを使った競合のテスト。(a)再読み込みの最中の`invalidate()`が失われない。(b)個別の更新が、`STALE`の間・再読み込みの最中に、失われない。(c)DBの障害の間、再読み込みの失敗が、間引かれ、待ちの連鎖が起きない。(d)取り込みのトランザクションの未確定の内容が、キャッシュに載らない。

### 確定と無効化の間の窓

トランザクションの確定(DBが新しい設定になる)から、`afterCommit`の無効化までの間は、キャッシュが、古い設定のままである。この間の読み取りは、古い設定を返す。窓は、同じインスタンスの中の、数マイクロ秒〜数ミリ秒であり、受け入れる(記録する)。

### Q1=Cの、確定後の副作用

取り込みの直後の、最初の読み取り(どのユーザーの、どの画面でも)が、再読み込みの時間(想定規模で、数百ミリ秒)を負担する。同時の読み取りは、再読み込みが終わるまで、待つ(待ちの上限つき)。NFR1の3秒(p95)の中に収まる見込みである。性能の確認で、取り込み直後の最初の読み取りも、計測する(performance-design.md)。

## NFR4.3: 内部設定DBの障害と、時間の上限

- **独自の時間の上限は置かない**(Q4=B・Q6=A)。取り込みは、完了するまで続く。
- **内部設定DBの既定のロック待ち**: H2 2.4.240の既定は、2秒(`INITIAL_LOCK_TIMEOUT`=2000ms)である(ソースで確認済み)。他の管理操作と衝突して、行のロックを、2秒待っても取れない場合は、ロック待ちのタイムアウトになる。`REPEATABLE_READ`のもとで、スナップショットの後に他のトランザクションが確定した行を書き込もうとした場合は、更新の競合になる(NFR4.1)。
- **例外と応答の対応**:

| 例外(Spring) | 応答 | 監査イベントのfailureCategory |
|---|---|---|
| `ConcurrencyFailureException`(`PessimisticLockingFailureException`・`CannotAcquireLockException`・`OptimisticLockingFailureException`を含む)・`QueryTimeoutException`・`DataAccessResourceFailureException`・`TransientDataAccessException` | 503 | UNEXPECTED |
| 各ユニットの検証の誤り | 422 | 分類による(VALIDATION_ERRORなど) |
| その他の、想定外の例外(コミット時の例外を含む) | 500 | UNEXPECTED |

- **失敗の監査イベント**: ロールバックの後、`TransactionTemplate`の外で、発行する(NFR4.5)。内部設定DBが、つながらない状態では、audit-loggingも、DBへ書き込めないため、記録できない(audit-loggingのNFR4.2・NFR4.6と同じ、既知の残存リスク)。
- **残余リスク(R-09(1)への対応)**: 本体の大きさの上限なし(NFR2.2)と、実行時間の上限なし(本節)の組み合わせにより、想定規模を大きく超える入力が、内部設定DBの接続と、リクエストのスレッドを、長く占有しうる。認証済みの利用者に限られ(security-design.md NFR2.2)、想定規模(数MB以下)では10秒以内の見込みであるため、MVPでは受け入れる。

## NFR4.4: エクスポートの読み取りの一貫性(Q2=A)

- `ConfigExportService.export`に、`@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`を付ける。
- 3ユニットの`getExportable〜`は、**キャッシュを使わず**、内部設定DBから直接読む(追補一覧の2番)。同じトランザクションの中で読むため、3ユニットは、同じ時点のスナップショットを読む。
- H2 2.4.240では、`REPEATABLE_READ`が、MVCCによる、取り込みの反映の途中の状態を読まない読み取りになる(`org.h2.engine.IsolationLevel`に、`READ_COMMITTED`・`REPEATABLE_READ`・`SNAPSHOT`・`SERIALIZABLE`が定義されていることを、ソースで確認済み)。H2が、`REPEATABLE_READ`を、実際にスナップショットとして扱うことは、Code Generationで、取り込みの反映中にエクスポートを行うテストで確認する(反映の途中の状態が見えないこと)。
- 取り込みの反映を待たせない(読み取りは、書き込みをブロックしない)。

## NFR4.5: 監査イベントの発行(R-01への対応)

取り込みの監査イベント(FR8.1、BR9.16)を、耐久的に記録するための、発行の仕組みを次のとおり定める。既存の、audit-loggingのリスナーは、**同期の`@EventListener`**であり(`@Async`なし)、リスナーの書き込みは、発行した側のトランザクションに参加する。既存のuser-managementの`UserChangedEventPublisher`は、この性質のため、コミット後の発行を、**`REQUIRES_NEW`の`TransactionTemplate`**で包んでいる。本ユニットも、同じ対処を採る。

| 場面 | 発行の方法 |
|---|---|
| 成功 | `PostCommitCoordinator`の`afterCommit`の中(順7)で、`ConfigImportEventPublisher`が、**`REQUIRES_NEW`の`TransactionTemplate`の中で、同期発行**する。`afterCommit`の時点では、元のトランザクションの資源が、まだ束縛されており、`REQUIRES_NEW`なしでは、書き込みが確定しないため。発行全体を、try-catchで包み、例外は、ERRORのログに出して、握りつぶす |
| 失敗 | `importConfig`の`catch`(トランザクションの外。NFR4.1)で、同じく、`REQUIRES_NEW`の`TransactionTemplate`の中で、同期発行する。try-catchで包む。トランザクションは、すでにロールバック済みであるため、失敗の記録が、取り込みと一緒にロールバックされることはない。コミット時の例外も、この`catch`で捕まえる |
| 422(検証の誤り) | 失敗と同じ。取り込みのトランザクションは、検証の誤りで、ロールバックされる |

- リスナー側(audit-logging)が、try-catchで例外を遮断する(audit-loggingのNFR4.2・NFR4.5)。発行元でも、発行を、try-catchで囲む(取り込みの結果に、影響させない)。
- **監査イベントが、成功の応答より前に、確定する**ことは、保証しない(`afterCommit`の中の、`REQUIRES_NEW`の発行が失敗した場合は、記録が失われる。audit-loggingのNFR4.2と同じ、fire-and-forgetの前提)。
- ロールバックされた取り込みの、成功のイベントは、発行しない(`afterCommit`は、ロールバックでは呼ばれない)。

## NFR4.6: 取り込みどうしの競合(受け入れたリスク)

排他は設けない(Q3=A)。ただし、NFR4.1の分離レベル(`REPEATABLE_READ`)の副産物として、同じ行を、同時に書き換える場合は、後から書き込む側が、更新の競合として失敗し、NFR4.3のとおり、503で返る。行が重ならない同時の変更は、両方が確定する。デッドロックやロックの待ちのタイムアウトも、同じく503である。

## NFR4.7: 内部設定DBの永続化・バックアップ・復旧

NFR Requirementsのレビュー指摘R-05のとおり、NFR4.7の「エクスポートで、取り込み前の設定を復元できる」は、設定管理画面へ到達できる場合に限る。自己の締め出し(取り込みの結果、操作者が設定管理画面に到達できなくなる)の場合は成り立たない。次のとおり、設計を補う。

| 場面 | 復旧の手段 |
|---|---|
| 取り込みの内容を、元に戻したい(画面に到達できる) | 取り込みの前に保存した、エクスポートのファイルを、取り込む |
| 取り込みの結果、画面に到達できない(自己の締め出し) | 内部設定DBのファイル(H2)を、取り込みの前の退避から戻す。アプリケーションを停止して、ファイルを入れ替え、起動する |
| 退避の取り方 | 取り込みの前に、アプリケーションを止めずに退避する場合は、H2の`BACKUP TO '<file>.zip'`(SQL文)を使う(接続が`AUTO_SERVER=TRUE`のため、別のプロセスからも実行できる)。運用の手順として、利用者向けの案内に記載する。**本ユニットは、退避の機能を、実装しない**(運用の手順のみ。Operationフェーズは対象外) |

- frontend-uiへの要求(functional-spec.mdの追補一覧7番)に、次を追加する。取り込みの確認モーダルに、「取り込みの前に、現在の設定をエクスポートして保存してください。締め出された場合の復旧には、内部設定DBのファイルの退避が必要です」という趣旨の案内を、表示する。

## 機能設計の残余リスクの引き継ぎ(R-09(2)への対応)

機能設計(functional-spec.md「残余リスク」)の7件が、NFR Designで、どう扱われたかを、1行ずつ記録する。

| 番号 | 機能設計の残余リスク | NFR Designでの扱い |
|---|---|---|
| 1 | 同時実行 | NFR4.6(排他なし。`REPEATABLE_READ`の副産物として、重なる行は競合で失敗) |
| 2 | 大きさの上限なし | security-design.md NFR2.2、scalability-design.md(メモリ)、本書NFR4.3(時間の上限なしとの組み合わせ) |
| 3 | 自己の締め出し | NFR4.7(復旧の手段) |
| 4 | 確認の時点と取り込みの時点のずれ | NFRの対象外。frontend-uiの確認モーダルの限界として受け入れる(機能設計 BR9.18のまま)。引き継ぎ先なし |
| 5 | 個別イベントの`actor` | security-design.md NFR2.4(追跡できる範囲の正直な評価) |
| 6 | ロールの削除とユーザーの`roleIds` | NFRの対象外。機能設計の未解決の事項のまま。Code Generationのplan承認までに確認する |
| 7 | ブートストラップ状態の判定(主権限0件) | NFRの対象外。本ユニットは、BR9.12で回避する。permission-engine側の修正は、別途の課題のまま |

## 障害時の挙動のまとめ

| 障害 | 取り込みの結果 | 内部設定DB | キャッシュ |
|---|---|---|---|
| 検証の誤り(422) | 反映なし。失敗の監査イベント | 変わらない | 変わらない |
| 反映中の例外(500・503)・更新の競合 | ロールバック。失敗の監査イベント | 取り込み前のまま | 変わらない(無効化しない) |
| 確定後のイベント発行の失敗 | 成功(200)。ERRORのログ | 新しい設定 | 無効化済み(次の読み取りで再読み込み) |
| 確定後の、最初の読み取りでの再読み込みの失敗 | (取り込みは成功済み) | 新しい設定 | `STALE`のまま。その読み取りは503。抑制の期間の後に再試行 |
