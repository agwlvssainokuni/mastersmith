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
 * TableConfig/ColumnConfig/TranslationEntryの変更を通知するドメインイベント (rules.md BR1.13,
 * functional-design/entities.md ConfigChangedEvent, security-design.md「監査ログ連携」)。
 *
 * <p>AuditLogging(U7)がSpringの{@code
 * ApplicationListener}/{@code @EventListener}経由で本イベントを購読する前提の疎結合設計とする(project.md学習事項:
 * 横断的な記録コンポーネントはドメインイベント発行・購読による疎結合を優先する)。
 *
 * <p><b>発行粒度(BR1.13)</b>: {@code writeTableConfigDraft}(W2)・{@code
 * importConfigSet}(W5)のように1回の呼び出しで複数のTableConfig/ColumnConfig/TranslationEntryが変更される場合でも、呼び出し単位で1件にまとめてはならず、変更されたエンティティごとに個別の{@code
 * ConfigChangedEvent}を発行する。
 *
 * <p><b>既知の制約(スコープ外の未解決事項)</b>:
 *
 * <ul>
 *   <li>{@code actor}は依然としてschema-introspector等のシステム操作由来の書き込みに対しては{@code "system"}固定である。project.md
 *       Mandatedは監査ログに操作者(actor)の記録を求めるが、C9 契約(writeTableConfigDraft,
 *       importConfigSet)は呼び出し元の認証コンテキスト(操作者ID)を 引数として受け取らないため、利用者操作のactor伝搬はContract
 *       Design追補(認証コンテキストの受け渡し契約)で解決する必要がある (functional-spec.mdのAssumptions & Open Questions参照)。
 *   <li>{@code operation}はentities.mdが定義する{@code CREATED}/{@code UPDATED}の区別ではなく、既存の{@link
 *       ConfigChangeOperation}(呼び出し種別:
 *       DRAFT_IMPORTED/CONFIG_SET_IMPORTED/TRANSLATION_UPSERTED)をそのまま用いる。この区別は本修正(レビュー指摘R-03)のスコープ外として意図的に据え置いた(code-summary.md逸脱事項参照)。
 * </ul>
 *
 * @param operation 変更操作の種別(呼び出し種別。上記「既知の制約」参照)
 * @param targetType 変更されたエンティティの種別({@link #TARGET_TYPE_TABLE_CONFIG}/{@link
 *     #TARGET_TYPE_COLUMN_CONFIG}/{@link #TARGET_TYPE_TRANSLATION_ENTRY}のいずれか。
 *     AuditLogEntry.targetTypeに対応)
 * @param targetId 変更されたエンティティのID。TableConfigはtableConfigId、ColumnConfigはcolumnConfigId、
 *     TranslationEntryは"{i18nKey}:{locale}"を用いる(AuditLogEntry.targetIdに対応)
 * @param beforeValue 変更前のエンティティ状態のスナップショット({@link ConfigChangeSnapshots}参照)。新規作成の場合はnull
 *     (AuditLogEntry.beforeValueに対応)
 * @param afterValue 変更後のエンティティ状態のスナップショット({@link
 *     ConfigChangeSnapshots}参照。AuditLogEntry.afterValueに対応)
 * @param actor 操作者。schema-introspector等のシステム操作は{@code "system"}を用いる
 * @param occurredAt 発生日時
 * @param target 変更対象の自由記述文字列(旧シェイプの互換フィールド。下記{@link #ConfigChangedEvent(ConfigChangeOperation,
 *     String, String, Instant)}参照)
 */
public record ConfigChangedEvent(
    ConfigChangeOperation operation,
    String targetType,
    String targetId,
    Object beforeValue,
    Object afterValue,
    String actor,
    Instant occurredAt,
    String target) {

  /** {@link #targetType}に用いるTableConfig用の定数。 */
  public static final String TARGET_TYPE_TABLE_CONFIG = "TableConfig";

  /** {@link #targetType}に用いるColumnConfig用の定数。 */
  public static final String TARGET_TYPE_COLUMN_CONFIG = "ColumnConfig";

  /** {@link #targetType}に用いるTranslationEntry用の定数。 */
  public static final String TARGET_TYPE_TRANSLATION_ENTRY = "TranslationEntry";

  /**
   * 旧シェイプ(operation, target, actor, occurredAt)の互換コンストラクタ。
   *
   * <p>AuditLogging(U7)は既にコード生成・レビュー済みのユニットであり、その{@code
   * AuditLogEventMapper#fromConfigChangedEvent}(rules.md BR7.2、当該ユニットで承認済みの規則: {@code
   * targetType="ConfigEngine"固定、targetId=target(自由記述文字列をそのまま)})は本コンストラクタの4フィールドのみを読み取る。config-engine側の本修正(レビュー指摘R-03)は他ユニットのコード・テストを変更しない制約
   * (dispatch指示)の下で行うため、このコンストラクタを残してAuditLogging側の既存呼び出し・テストの再コンパイルを妨げない。
   * targetType/targetId/beforeValue/afterValueは未設定(null)となる。config-engine自身の発行箇所は{@link
   * #of}経由でこのコンストラクタを使わない。
   */
  public ConfigChangedEvent(
      ConfigChangeOperation operation, String target, String actor, Instant occurredAt) {
    this(operation, null, null, null, null, actor, occurredAt, target);
  }

  /**
   * エンティティ単位のConfigChangedEventを構築する(BR1.13)。{@code target}(旧シェイプ互換フィールド)は{@code
   * "<targetType>:<targetId>"}として自動導出する。
   */
  public static ConfigChangedEvent of(
      ConfigChangeOperation operation,
      String targetType,
      String targetId,
      Object beforeValue,
      Object afterValue,
      String actor) {
    return new ConfigChangedEvent(
        operation,
        targetType,
        targetId,
        beforeValue,
        afterValue,
        actor,
        Instant.now(),
        "%s:%s".formatted(targetType, targetId));
  }
}
