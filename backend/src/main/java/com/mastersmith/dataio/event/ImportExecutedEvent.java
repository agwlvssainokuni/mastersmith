/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastersmith.dataio.event;

import java.time.Instant;

/**
 * CSVインポート実行そのものをAuditLogging(U7)へ通知するドメインイベント(rules.md BR8.9、entities.md
 * ImportExecutedEvent)。config-engineの{@code ConfigChangedEvent}と同様、SpringのApplicationEvent
 * publisherを用いたfire-and-forgetな疎結合発行とする(security-design.md「監査ログ設計」)。
 *
 * <p><b>project.md Mandated(監査ログは変更前後の値を記録する)との関係</b>: 本イベントは行単位の変更前後値を
 * 含めない実行単位のサマリイベントとする。これはCSVインポートが業務データの一括投入・移行用途であり、
 * 個々の行の変更前後値までは監査ログの対象としないという、インタビューで明示的に確認済みの意図的な スコープ判断である(functional-spec.md Assumptions & Open
 * Questions、rules.md BR8.9のnotes参照)。
 *
 * @param tableConfigId インポート対象テーブル(config-engineのTableConfig)を指すID
 * @param actor インポートを実行した利用者のユーザーID
 * @param successCount インポートに成功した行数(0件を含む。全体ロールバック時は常に0)
 * @param errorCount バリデーションエラーとなった行数
 * @param committed 全行有効でコミットに成功した場合はtrue、1件でもエラーがあり全体ロールバックした場合はfalse
 * @param occurredAt インポート実行完了日時
 */
public record ImportExecutedEvent(
    String tableConfigId,
    String actor,
    int successCount,
    int errorCount,
    boolean committed,
    Instant occurredAt) {

  public static ImportExecutedEvent of(
      String tableConfigId, String actor, int successCount, int errorCount, boolean committed) {
    return new ImportExecutedEvent(
        tableConfigId, actor, successCount, errorCount, committed, Instant.now());
  }
}
