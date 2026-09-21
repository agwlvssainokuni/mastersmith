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

package com.mastersmith.configio.event;

import com.mastersmith.common.configio.SectionCounts;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 設定の取り込みの実行を、audit-loggingへ知らせるドメインイベント(config-import-export entities.md
 * ConfigImportExecutedEvent、BR9.16、FR8.1)。取り込み1回につき1件で、成功・失敗の両方で発行する。 エクスポートでは、発行しない。
 *
 * <p>監査ログは追記専用で、無期限に保持される(FR8.2・FR8.3)ため、内容は、操作者・日時・結果・件数・失敗の理由の分類に限る。<b>ファイルの内容(設定の値・名前)は、含めない。</b>
 *
 * @param actorUserId 操作者のuserId
 * @param actorRoleId 操作者のアクティブロール(未選択ならnull)
 * @param outcome 取り込みの結果
 * @param failureCategory FAILUREのときの、失敗の理由の分類。SUCCESSではnull
 * @param sections SUCCESSのときの、セクションごとの件数。FAILUREではnull(何も反映していない)
 * @param errorCount FAILUREのときの、検出した誤りの件数(打ち切り前の総数)。SUCCESSではnull
 * @param occurredAt 発生日時
 */
public record ConfigImportExecutedEvent(
    String actorUserId,
    String actorRoleId,
    Outcome outcome,
    FailureCategory failureCategory,
    Map<String, SectionCounts> sections,
    Integer errorCount,
    Instant occurredAt) {

  /** 取り込みの結果。 */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  /** 失敗の理由の分類(BR9.16)。 */
  public enum FailureCategory {
    /** 構文(JSONとして読めない)。 */
    MALFORMED,
    /** 形式の版(formatVersionが欠落・未対応)。 */
    UNSUPPORTED_FORMAT,
    /** 構造・参照整合・各ユニットの検証の誤り。 */
    VALIDATION_ERROR,
    /** 権限昇格。 */
    ESCALATION_DENIED,
    /** 主権限が0件。 */
    RBAC_EMPTY,
    /** 反映中の想定外の失敗(内部の障害)。 */
    UNEXPECTED
  }

  public ConfigImportExecutedEvent {
    Objects.requireNonNull(actorUserId, "actorUserId");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(occurredAt, "occurredAt");
    sections = sections == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(sections));
  }

  /** 成功のイベント(反映が確定したあとに発行する)。 */
  public static ConfigImportExecutedEvent success(
      String actorUserId, String actorRoleId, Map<String, SectionCounts> sections) {
    return new ConfigImportExecutedEvent(
        actorUserId, actorRoleId, Outcome.SUCCESS, null, sections, null, Instant.now());
  }

  /** 失敗のイベント(反映を行っていないため、判定の時点で発行する)。 */
  public static ConfigImportExecutedEvent failure(
      String actorUserId, String actorRoleId, FailureCategory category, int errorCount) {
    return new ConfigImportExecutedEvent(
        actorUserId,
        actorRoleId,
        Outcome.FAILURE,
        Objects.requireNonNull(category, "category"),
        null,
        errorCount,
        Instant.now());
  }
}
