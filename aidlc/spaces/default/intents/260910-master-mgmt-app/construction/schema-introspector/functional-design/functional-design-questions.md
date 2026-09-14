# Functional Design Questions — schema-introspector (U2)

## Sources

- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/inception/units-generation/unit-of-work.md` — U2定義
- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/inception/domain-design/components.md` — SchemaIntrospectorコンポーネント定義
- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/inception/contract-design/contract-summary.md` — C8(REST API)・C9(ConfigEngineApi、`writeTableConfigDraft`)

schema-introspectorは、対象RDBMSのメタデータ(テーブル/カラム/型/NULL可否/主キー/外部キー等)を読み取り、config-engineへ設定の初期ドラフトを書き込む単純な一過性処理です(責務はC9の`writeTableConfigDraft`呼び出しに限定)。以下は、既存の成果物(unit-of-work.md/components.md/contract-summary.md)だけでは決まらない、本ユニット固有のビジネスルール上の未確定事項です。

## Q1. ドラフト生成時のeditorType/format決定ルール

対象DBのSQL型やカラム属性から、`ColumnConfig`のeditorType(編集部品)・format(書式)の初期ドラフト値をどう決定しますか。外部キー参照カラムの扱いも含みます(選択肢の名称解決自体が実行時動的取得であることはFR1.5で既に確定済みで、ここではeditorTypeという分類値そのものをドラフトでどう設定するかを確認します)。

- A. SQLの型カテゴリ(文字列/数値/日付時刻/真偽値等)に基づく単純なマッピングをeditorType/formatの既定値とし、外部キー参照カラムは自動的にeditorType=selectとする(推奨。業務担当者は生成後に必要に応じて手動調整する)
- B. 外部キー参照カラムも含め、editorTypeは常に既定値(テキスト等)とする。selectへの変更は業務担当者が手動で行う
- C. 型カテゴリに基づくeditorType/formatマッピングは行うが、外部キー検出によるselect化は本ユニットの対象外とする(将来の別課題とする)
- X. Other (please specify)

[Answer]:

## Q2. 楽観ロック対象列(optimisticLockColumn)の自動検出

`TableConfig.optimisticLockColumn`(楽観ロック対象列)は、本ユニットがカラム名の命名規則から自動検出して設定しますか、それとも常に未設定のままドラフト生成し、業務担当者がconfig-engineの設定画面で後から手動指定しますか。

- A. 自動検出は行わない。ドラフトでは常に未設定(null)とし、業務担当者が手動で指定する(推奨。特定の命名規則を前提としないシンプルな設計で、FR1.6のハードコード禁止方針とも整合する)
- B. 特定のカラム名パターン(例: updated_at, version, lock_version等)に一致する場合は自動的にoptimisticLockColumnとして設定する
- X. Other (please specify)

[Answer]:

## Q3. 再実行時のスキップ粒度

「スキーマからドラフト生成」操作を同じテーブルに対して再実行した場合(例: 業務DBに新しいカラムが後から追加された場合)、既存設定に対するスキップの粒度はどうしますか。

- A. カラム単位。既にTableConfigが存在するテーブルでも、まだColumnConfigが存在しない新規カラムのみを追加ドラフトとして生成する(差分検出、推奨。スキーマ変更への追従が容易)
- B. テーブル単位。TableConfigが既に存在するテーブルは丸ごとスキップし、新規カラムがあっても追加しない(再実行は未設定の新規テーブルのみを対象とする、より単純な設計)
- X. Other (please specify)

[Answer]:
