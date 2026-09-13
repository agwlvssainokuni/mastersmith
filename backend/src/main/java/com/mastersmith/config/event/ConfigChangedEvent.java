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

package com.mastersmith.config.event;

import java.time.Instant;

/**
 * TableConfig/ColumnConfig/TranslationEntryの変更を通知するドメインイベント (security-design.md「監査ログ連携」)。
 *
 * <p>AuditLogging(将来ユニット)がSpringの{@code
 * ApplicationListener}/{@code @EventListener}経由で本イベントを購読する前提の疎結合設計とする(project.md学習事項:
 * 横断的な記録コンポーネントはドメインイベント発行・購読による疎結合を優先する)。
 *
 * <p><b>既知の制約</b>: project.md Mandatedは監査ログに操作者(actor)の記録を求めるが、C9 契約(writeTableConfigDraft,
 * importConfigSet)は呼び出し元の認証コンテキスト(操作者ID)を 引数として受け取らない。schema-introspectorからの取り込みはシステム操作(actor =
 * "system")として扱い、利用者操作(importConfigSet, 将来のW6 REST API)のactor伝搬は Contract
 * Design追補(認証コンテキストの受け渡し契約)で解決する必要がある (functional-spec.mdのAssumptions & Open Questions参照)。
 *
 * @param operation 変更操作の種別
 * @param target 変更対象の説明(例: "schema.table"、"importConfigSet:3 tables")
 * @param actor 操作者。schema-introspector等のシステム操作は{@code "system"}を用いる
 * @param occurredAt 発生日時
 */
public record ConfigChangedEvent(
    ConfigChangeOperation operation, String target, String actor, Instant occurredAt) {

  public static ConfigChangedEvent of(
      ConfigChangeOperation operation, String target, String actor) {
    return new ConfigChangedEvent(operation, target, actor, Instant.now());
  }
}
