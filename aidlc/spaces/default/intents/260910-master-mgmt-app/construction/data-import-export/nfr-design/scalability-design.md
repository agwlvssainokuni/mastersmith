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

# Scalability Design — data-import-export (U8)

`construction/data-import-export/nfr-requirements/scalability-requirements.md`(NFR3.1〜NFR3.3)に基づく、data-import-exportユニットのスケーラビリティ設計。

## ステートレス設計(NFR3.1対応)

`performance-design.md`のコンポーネント分割(CSV I/O層・バリデーション層・一時バッファ管理・コミット層)は、いずれもリクエストスコープ(Spring Beanとしては`@RequestScope`または呼び出しごとにインスタンス化するPOJO)で設計し、インスタンス間・リクエスト間で共有される可変状態を持たない。一時バッファ(`ImportRowBuffer`)はリクエストローカルなオブジェクトであり、アプリケーションインスタンスを水平に追加してもインスタンス間のデータ共有・同期は不要とする。

## 同時実行制御を設けない設計(NFR3.2対応)

同一テーブルへの同時インポートに対する排他制御(分散ロック、DBレベルのアドバイザリロック等)は実装しない。DBの標準的なトランザクション分離レベル(Spring Bootのデフォルト、通常`READ_COMMITTED`)・行ロックに競合解決を委ねる。

- **設計上の留意点**: 大量行(10万行規模)を対象とする1トランザクションが長時間実行されると、同一テーブルへの他のトランザクション(通常の一覧・詳細編集操作を含む)がロック待ちになる可能性がある。この影響範囲はテーブル単位に閉じ、アプリケーション全体を停止させるものではない。

## メモリバッファのスケーラビリティ設計(NFR3.3対応)

`ImportRowBuffer`(一時バッファ管理コンポーネント)は、`List<ValidatedRow>`(Java標準コレクション)としてMVPスコープでは常に実装する。将来、NFR1想定規模(10万行)を大きく超えるデータ量に対応する必要が生じた場合の拡張ポイントとして、`ImportRowBuffer`をインタフェース化し、メモリ実装(`InMemoryImportRowBuffer`)と一時テーブル実装(`StagingTableImportRowBuffer`、未実装)を差し替え可能な設計としておく。

```java
// 拡張性を見込んだインタフェース設計(概念設計)
interface ImportRowBuffer {
    void add(ValidatedRow row);
    List<ValidatedRow> drain();
    void discard();
}
```

現時点(MVP)では`InMemoryImportRowBuffer`のみを実装する(`nfr-requirements-questions.md` Q8確定)。
