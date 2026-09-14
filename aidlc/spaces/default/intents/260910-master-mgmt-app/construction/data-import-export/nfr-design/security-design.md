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

# Security Design — data-import-export (U8)

`construction/data-import-export/nfr-requirements/security-requirements.md`(NFR2.1〜NFR2.4)、および`nfr-design-questions.md`の確定回答に基づく、data-import-exportユニットのセキュリティ設計。

## 認可アーキテクチャ(NFR2.2対応)

DataImportExportは権限を再検証しない設計を維持する(`functional-design/rules.md` BR8.8)。列単位の実効READ権限一覧(`permittedColumnNames`)は、list-engineが呼び出し前にPermissionEngineへ問い合わせて解決した結果を、C13(`DataImportExportApi.exportCsv`)のメソッドパラメータとして直接受け渡す(`nfr-design-questions.md` Q2確定)。

```java
// C13契約(内部Javaインタフェース、DataImportExportApi)のメソッドシグネチャ設計方針
InputStream exportCsv(String tableConfigId, Map<String, Object> filter, String sort,
                       List<String> permittedColumnNames);
ImportResult importCsv(String tableConfigId, InputStream file, String actor);
```

- `permittedColumnNames`はJavaのメソッドパラメータであり、HTTPリクエスト(C1)とは独立した内部インタフェース(C13)のみに現れる(Contract Design追補Q6=Aで解決済み)。C1(list-engineのfrontend-ui向けREST API、`GET /records/export`)には`filter`・`sort`クエリパラメータのみを追加し、`permittedColumnNames`はWEB APIには一切公開しない。クライアント(ブラウザ)が自身の権限範囲を指定できる余地を作らないための意図的な設計であり、list-engineがサーバー側で算出した値をC13呼び出し時にのみ渡す。
- `actor`は`nfr-design-questions.md` Q1確定によりC13へ明示的なパラメータとして追加する(SecurityContextHolder方式は不採用、Contract Design追補Q7=Aで解決済み)。record-edit-engineが自身のREST層(C2、Bearer認証済み)のSpring Security認証済みプリンシパルから取得した実行者ユーザーIDをそのまま渡す。WEB API(C2)のリクエストボディには`actor`を公開しない。

## 入力検証設計(NFR2.3・NFR2.4関連)

- CSVインジェクション(数式インジェクション)対策は実装しない(NFR2.3、意図的なリスク受容)。エクスポート処理(`CsvWriter`)でセル値のエスケープ処理は行わない。
- アップロードファイルのサイズ・Content-Type・拡張子チェックは追加しない(NFR2.4)。`CsvReader`(Apache Commons CSV)がBR8.1のCSV形式で読み取れない場合、ファイル単位のパースエラーとして扱う。

## 監査ログ設計(NFR2.1対応)

`ImportCommitter`コンポーネント(`performance-design.md`のコンポーネント分割参照)が、インポート処理完了時(コミット成功・全体ロールバックのいずれの場合も)に`ImportExecutedEvent`を1件、AuditLogging(U7)へ発行する。イベントの発行はSpringの`ApplicationEventPublisher`を用いたアプリケーション内イベント(fire-and-forget、`components.md`の疎結合な依存関係を実装レベルで反映)とする。

```java
// ImportCommitter内の発行処理(概念設計、実装はCode Generationで確定)
applicationEventPublisher.publishEvent(
    new ImportExecutedEvent(tableConfigId, actor, successCount, errorCount, committed, occurredAt));
```

行単位の変更前後の値は含めない(`security-requirements.md` NFR2.1で確定済みのスコープ制限)。

## Security Anti-Requirements(除外事項、`security-requirements.md`から継承)

- 依存関係の脆弱性スキャンは対象外(`team.md`確定事項)。
- 業務データ本体の暗号化要件は本ユニット固有には設けない(対象RDBMSの責務範囲)。
