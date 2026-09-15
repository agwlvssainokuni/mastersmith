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

# Reliability Design — audit-logging (U7)

`construction/audit-logging/nfr-requirements/reliability-requirements.md`(NFR4.1〜NFR4.6)、および`nfr-design-questions.md` Q1確定に基づく、audit-loggingユニットの信頼性設計。

## イベントリスナーの例外遮断設計(NFR4.2・NFR4.5対応、Q1確定)

BR7.7(記録失敗時は発行元の処理へ一切影響を与えない)を実現するため、同期の`@EventListener`とし、リスナーメソッド内でイベントのマッピング処理(BR7.2〜BR7.4)からAuditLogEntryのDB書き込みまでの全体を`try-catch`で囲み、いかなる例外も発行元(config-engine・permission-engine・data-import-export)へ伝播させない。`@Async`は導入しない(同期実行のシンプルさを優先)。

```java
// 各イベントリスナーの概念設計(3種類の専用リスナーメソッドで共通のパターン)
@EventListener
void onConfigChangedEvent(ConfigChangedEvent event) {
    try {
        AuditLogEntry entry = mapper.fromConfigChangedEvent(event); // BR7.2
        repository.save(entry);
    } catch (Exception e) {
        log.error("audit log entry の記録に失敗しました", e); // 構造化ログ、BR7.7
        // 例外を再送出しない。発行元(config-engine)の呼び出しスタックへは伝播させない
    }
}
```

Springの`@EventListener`は既定では発行元と同一スレッド・同期実行であるため、この`try-catch`境界が唯一の例外遮断機構となる。3種類のリスナーメソッド(ConfigChangedEvent・PermissionChangedEvent・ImportExecutedEvent)いずれも同一のパターンを適用する。

## データ完全性(NFR4.1対応)

AuditLogEntryは追記専用(append-only)とし、`security-design.md`の`AuditLogEntryRepository`設計によりアプリケーション層にUPDATE/DELETE経路を持たない(`functional-design/rules.md` BR7.5)。

## 記録失敗時の許容(NFR4.2対応、Q1確定)

イベント購読後の内部設定DBへの書き込みが失敗した場合、上記の`try-catch`境界内で捕捉し、構造化ログ(ERRORレベル)へ記録するのみとする。例外の再送出・リトライは行わない。監査記録の欠落は許容されるMVPスコープの制約とする。専用のメトリクス・アラートは設けない(`observability-requirements.md` NFR5.3)。

## 無期限保持(NFR4.3対応)

AuditLogEntryは無期限に保持する(FR8.3)。削除・アーカイブ機能は本MVPスコープでは実装しない(project.md Out of Scope)。

## 内部設定DB接続断時の閲覧APIの挙動(NFR4.4対応)

`GET /api/audit-log`呼び出し時に内部設定DBが利用不可の場合、503 Service Unavailable(RFC 9457形式のProblemDetails、`contract-summary.md` C6契約の503レスポンス追補)を返す。専用のフォールバック(キャッシュ等)は設けず、他の内部設定DB依存エンドポイントと同様の一般的な障害処理に従う。

## イベント発行元の疎結合(NFR4.5対応)

audit-loggingの内部設定DB書き込み処理の遅延・失敗が、イベント発行元(config-engine・permission-engine・data-import-export)の可用性・応答性に影響を与えないことを、上記の同期`@EventListener` + `try-catch`境界により保証する。非同期化(`@Async`)は導入していないが、リスナーメソッド内の処理時間そのものが発行元のスレッドをブロックする点は、NFR1.3(内部処理時間に個別の数値目標を設けない)により許容する。

## 監査記録パイプライン全断の既知の残存リスク(NFR4.6対応、アーキテクチャレビュー指摘R-03対応、意図的なスコープ判断)

NFR4.2(記録失敗時は構造化ログのみ)とNFR5.3(専用メトリクス・アラートなし)の組み合わせにより、audit-loggingの記録処理そのものが完全に停止した場合(内部設定DBへの接続が恒久的に失われた等)、これを自動的に検知する手段は本設計にも存在しない。唯一の検知経路は、上記`try-catch`境界内の構造化ログ(BR7.7)をオペレーターが手動で確認する運用である。これは見落としではなく、`reliability-requirements.md` NFR4.6に記録済みの**受容された残存リスク(accepted risk)**であり、本設計もこれを追加で緩和する仕組みは導入しない。将来この残存リスクが許容できないと判断された場合の再検討事項(AuditLogEntry書き込み成功をカウントする軽量なメトリクス1つの追加)は、`reliability-requirements.md`の記載を引き継ぐ。

## Reliability Anti-Requirements(除外事項、`reliability-requirements.md`から継承)

- サーキットブレーカー・リトライ・タイムアウト設定等の耐障害性パターンは導入しない(内部設定DBへの書き込みは同一プロセス内の呼び出しであり、ネットワーク越しの外部呼び出しを持たない)。
- 記録失敗時の再送・キューイングは実装しない(NFR4.2の意図的な許容判断)。
