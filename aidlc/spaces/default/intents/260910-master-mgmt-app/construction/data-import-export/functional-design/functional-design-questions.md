# Functional Design Questions — data-import-export (U8)

`inception/units-generation/unit-of-work.md`(U8定義)・`inception/domain-design/components.md`(DataImportExportコンポーネント定義)・`inception/contract-design/contract-summary.md`(C13: DataImportExportApi)に基づき、data-import-exportユニットの機能設計(エンティティ・業務ルール・振る舞い仕様)を確定するための質問。

Domain Design・Contract Designで既に確定済みの事項(exportCsv/importCsvのメソッドシグネチャ、list-engine/record-edit-engineからの内部委譲、config-engineからのカラム定義・バリデーションルール取得、行単位のバリデーションエラー収集)は再確認しない。ここでは、それらの確定事項からは読み取れない、機能設計として具体化が必要な論点のみを問う。

## Q1: CSVファイル形式の仕様

FR12.1は「CSV等によるエクスポート・インポート」とのみ規定しており、具体的なファイル形式は未確定です。エクスポート・インポート双方で共通に用いるCSV形式はどれにしますか。

- A. 文字コードUTF-8(BOM付き、Excelでの文字化け回避)、区切り文字はカンマ、1行目はカラム名のヘッダー行、改行コードはCRLF
- B. Aと同様だがBOMなし(UTF-8純正)
- C. 文字コードShift_JIS(社内利用者の既存Excel運用に合わせる)、区切り文字はカンマ、ヘッダー行あり
- X. Other (please specify)

[Answer]: A. 文字コードUTF-8(BOM付き、Excelでの文字化け回避)、区切り文字はカンマ、1行目はカラム名のヘッダー行、改行コードはCRLF

## Q2: エクスポート対象範囲

Contract Design(C1: `/api/tables/{tableConfigId}/records/export`)のエンドポイントには検索・フィルタ条件のパラメータがありません。エクスポートは常に対象テーブルの全件を出力する、という理解でよいですか。それとも一覧画面の現在の検索条件を反映した範囲のみをエクスポートする必要がありますか。

- A. 常に対象テーブルの全件をエクスポートする(既存契約どおり、検索条件は反映しない)
- B. 一覧画面の現在の検索条件・ソート順を反映した範囲のみをエクスポートする(Contract Designへの追補が必要になる)
- X. Other (please specify)

[Answer]: B. 一覧画面の現在の検索条件・ソート順を反映した範囲のみをエクスポートする(Contract Designへの追補が必要になる)

## Q3: エクスポート対象列の範囲

`components.md`のColumnConfig(config-engineが保持)には`visibility`(visible/hidden)属性があり、また利用者のロールによってはREAD権限未満のカラムも存在します。CSVエクスポート時にどの列を出力しますか。

- A. `visibility: hidden`の列およびREAD権限未満の列は除外し、実行ユーザーが一覧画面で実際に閲覧できる列のみをエクスポートする
- B. 権限・表示可否に関わらず、対象テーブルの全ColumnConfig列を常にエクスポートする(バックアップ・データ移行用途を優先)
- C. `visibility: hidden`の列は除外するが、READ権限の有無は考慮しない(表示設定のみを反映し、権限は別軸として扱う)
- X. Other (please specify)

[Answer]: A. `visibility: hidden`の列およびREAD権限未満の列は除外し、実行ユーザーが一覧画面で実際に閲覧できる列のみをエクスポートする

## Q4: インポート時の行識別・新規作成/更新の判定方法

FR12.1は「エクスポート・インポートの両方に対応」とのみ規定しており、インポートが常に新規作成(INSERT)のみを行うのか、既存行の更新(UPDATE)にも対応するのかは未確定です。CSVインポート時、行をどのように識別し、新規作成/更新を判定しますか。

- A. インポートは常に新規作成(INSERT)専用とする。既存行の更新はrecord-edit-engineの詳細・編集画面から個別に行う(CSVに主キー列を含めない、または含めても無視する)
- B. CSVに主キー列(または一意識別列)が含まれる場合は既存行の更新(UPDATE)、含まれない・値が空の場合は新規作成(INSERT)として扱う(upsert方式)
- C. 主キー列の有無に関わらず、常に(schemaName, tableName)内の一意制約列(業務キー)でマッチングしUPDATE/INSERTを判定する
- X. Other (please specify)

[Answer]: B. CSVに主キー列(または一意識別列)が含まれる場合は既存行の更新(UPDATE)、含まれない・値が空の場合は新規作成(INSERT)として扱う(upsert方式)

## Q5: インポート時の楽観ロック競合の扱い

`rules.md`(config-engine)のBR1.7により、楽観ロック対象列(`optimisticLockColumn`)の有無はconfig-engineが管理し、対象列が存在する場合のみrecord-edit-engineの詳細・編集画面で競合検出を行います(FR6.3)。CSVインポート(Q4でBまたはCを選び既存行の更新を伴う場合)において、楽観ロック対象列が設定されているテーブルへのインポートはどう扱いますか。

- A. CSVインポートには楽観ロック競合検出を適用しない(対象列の設定有無に関わらず、インポートは常に後勝ちで上書きする)。同時実行中の詳細編集画面での競合はrecord-edit-engine側の責務とし、本ユニットは考慮しない
- B. CSVに楽観ロック対象列の現在値を含めることを必須とし、値が一致しない行はエラー(行単位のバリデーションエラー)として更新を拒否する
- C. Q4でAを選んだ場合(インポートは常にINSERT専用)、本論点は該当しない
- X. Other (please specify)

[Answer]: A. CSVインポートには楽観ロック競合検出を適用しない(対象列の設定有無に関わらず、インポートは常に後勝ちで上書きする)。同時実行中の詳細編集画面での競合はrecord-edit-engine側の責務とし、本ユニットは考慮しない

## Q6: インポートバリデーションの適用範囲

config-engineの`ColumnConfig.validationRule`(構造化バリデーションルール、`rules.md` BR1.9参照)は、record-edit-engineの詳細・編集画面のフォーカスアウト時インラインバリデーションで使われるものと同じ定義です。CSVインポート時にも同じ`validationRule`をそのまま適用し、行単位のエラーとして収集する、という理解でよいですか。それとも、型変換エラー(例: 数値列に文字列が入っている)など、CSV特有のエラーも別途考慮が必要ですか。

- A. `validationRule`(required/minLength/maxLength/min/max/pattern等)をそのまま適用する。加えて、CSVの値を`editorType`に応じた型へ変換できない場合(例: integer列に非数値文字列)も行単位のバリデーションエラーとして扱う
- B. `validationRule`のうち`required`のみを適用し、その他の詳細な形式チェックはインポート時には行わない(緩い検証)
- X. Other (please specify)

[Answer]: A. `validationRule`(required/minLength/maxLength/min/max/pattern等)をそのまま適用する。加えて、CSVの値を`editorType`に応じた型へ変換できない場合(例: integer列に非数値文字列)も行単位のバリデーションエラーとして扱う

## Q7: インポート実行時の権限検証の主体

`components.md`のDataImportExportの`depends_on`にはPermissionEngineが含まれておらず、list-engine(エクスポート起動元)・record-edit-engine(インポート起動元)がそれぞれ自身の権限判定(READ/CREATE等)を事前に済ませたうえでDataImportExportへ処理を委譲する、という設計になっています。`project.md`のMandated「権限の判定はサーバー側で必ず実効権限を再検証する」との関係で、DataImportExport自身も権限を再検証すべきですか、それとも呼び出し元(list-engine/record-edit-engine)による事前検証を信頼してよいですか。

- A. DataImportExportは権限を再検証しない。list-engine(エクスポート時: READ権限)・record-edit-engine(インポート時: CREATE権限、およびUPDATEを伴う場合はFULL権限)が呼び出し前に実効権限を検証済みであることを前提とする(同一プロセス内の内部委譲であり、外部から直接呼び出されるAPIではないため、Mandatedの「サーバー側再検証」はlist-engine/record-edit-engineの境界で満たされているとみなす)
- B. DataImportExport自身もPermissionEngineへ問い合わせて実効権限を再検証する(depends_onにPermissionEngineの追加が必要)
- X. Other (please specify)

[Answer]: A. DataImportExportは権限を再検証しない。list-engine(エクスポート時: READ権限)・record-edit-engine(インポート時: CREATE権限、およびUPDATEを伴う場合はFULL権限)が呼び出し前に実効権限を検証済みであることを前提とする(同一プロセス内の内部委譲であり、外部から直接呼び出されるAPIではないため、Mandatedの「サーバー側再検証」はlist-engine/record-edit-engineの境界で満たされているとみなす)

## Q8: 監査ログイベントの粒度

`components.md`はDataImportExportからAuditLoggingへの「業務データインポート実行のドメインイベント」発行を定義していますが、粒度(インポート実行1回につき1イベントか、インポートされた行ごとに1イベントか)は未確定です。config-engineの機能設計(`rules.md` BR1.13)では「変更されたエンティティごとに1イベント発行」という方針が既に採用されています。

- A. config-engineと同様、インポートで作成・更新に成功した行ごとに1件のイベント(操作者・対象テーブル・対象行識別子・変更前後の値・日時)を発行する(`project.md` Mandatedの「変更前後の値」を行単位で正確に記録できる)
- B. インポート実行1回につき1件のイベント(実行者・対象テーブル・成功件数・エラー件数・日時)のみを発行し、行単位の変更前後の値は記録しない(実行単位の要約イベント)
- C. Aに加えてBも発行する(行単位イベント+実行単位のサマリイベントの両方)
- X. Other (please specify)

[Answer]: B. インポート実行1回につき1件のイベント(実行者・対象テーブル・成功件数・エラー件数・日時)のみを発行し、行単位の変更前後の値は記録しない(実行単位の要約イベント)

## Q8 Follow-up: Mandated(監査ログの変更前後の値記録)との整合確認

`project.md`の`## Mandated`は「監査ログは... 少なくとも操作者・操作対象・操作種別・日時・変更前後の値を記録する」としています。Q8で選んだ「実行単位のサマリ1件のみ」の場合、CSVインポートで変更された個々の行の変更前後の値は監査ログに残りません。この理解でよいですか。

- A. サマリ1件のままでよい(行単位の変更前後の値は意図的に省略する)。CSVインポートは業務データの一括投入・移行用途であり、個々の行の変更前後値までは監査ログの対象としない、というスコープ判断として明示的に採用する
- B. サマリに加え、行単位の変更内容(rowId・変更前後の値)を発行する方式に変更する
- X. Other (please specify)

[Answer]: A. サマリ1件のままでよい(行単位の変更前後の値は意図的に省略する)。CSVインポートは業務データの一括投入・移行用途であり、個々の行の変更前後値までは監査ログの対象としない、というスコープ判断として明示的に採用する

## Q9: 大量データ処理時の方式

NFR1は「1テーブルあたり最大10万行程度までを想定した性能を確保する」としています。CSVエクスポート・インポートについて、この規模を想定した実装方針はありますか。

- A. ストリーミング処理(一括でメモリに読み込まず、行単位でストリーム処理)を必須とする。エクスポートはDBカーソルからの逐次読み取り→CSV書き出し、インポートはCSVの逐次読み取り→行単位のバリデーション・書き込みとする
- B. 今回のMVPスコープでは10万行規模の性能最適化(ストリーミング処理)は必須とせず、素朴な実装(全件をメモリに読み込む等)でよい。性能が問題になった場合は後続の改善課題とする
- X. Other (please specify)

[Answer]: A. ストリーミング処理(一括でメモリに読み込まず、行単位でストリーム処理)を必須とする。エクスポートはDBカーソルからの逐次読み取り→CSV書き出し、インポートはCSVの逐次読み取り→行単位のバリデーション・書き込みとする

## Q10: インポート失敗時のコミット単位

Contract Design(C13)の`importCsv`は「行単位のバリデーションエラーを収集して返す(全体を即時失敗にはしない)」と定義されており、一部の行がバリデーションエラーになっても処理全体を中断しないことは既に確定しています。DBへの反映(コミット)の単位はどうしますか。

- A. バリデーションに成功した行は、他の行の成否に関わらずDBへ反映(コミット)する。バリデーションエラーの行のみDBへ反映せず、エラー一覧として返す(行単位トランザクション)
- B. バリデーションを全行に対して先に実施し、1件でもエラーがあれば全体をロールバックしてDBへの反映を一切行わない(全体をエラー一覧として返す。ただしバリデーションと保存を分離するため`importCsv`の「全体を即時失敗にはしない」という契約note文言との整合を要確認)
- X. Other (please specify)

[Answer]: B. バリデーションを全行に対して先に実施し(ストリーミング読み取りで全行を検証し、書き込みは1つのDBトランザクション内で保留)、1件でもエラーがあれば全体をロールバックしてDBへの反映を一切行わない。契約note「全体を即時失敗にはしない」は、行単位のバリデーションを最初のエラーで中断せず全行分の検証結果を収集して返す、という意味であり、コミット単位(トランザクション境界)とは別の論点として整理する

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
