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

package com.mastersmith.configio.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 1回の取り込みの実行の文脈(config-import-export entities.md
 * ImportContext。永続化しない)。権限昇格の判定は、この文脈が保持する「取り込み開始時点の設定」を基準に行う(BR9.11)。
 *
 * @param operatorUserId 操作者のuserId(共有契約C15のOperator)
 * @param operatorActiveRoleId 操作者のアクティブロール(未選択ならnull)。permission-engineのactorRoleIdに渡す
 * @param startedAt 取り込みを始めた日時
 * @param bootstrapAtStart
 *     取り込みの開始時点(トランザクションの開始時点のスナップショット)で、permission-engineが初期状態(主権限が0件)だったか。昇格の判定の除外は、この値で固定する
 */
public record ImportContext(
    String operatorUserId,
    String operatorActiveRoleId,
    Instant startedAt,
    boolean bootstrapAtStart) {

  public ImportContext {
    Objects.requireNonNull(operatorUserId, "operatorUserId");
    Objects.requireNonNull(startedAt, "startedAt");
  }

  /** {@code bootstrapAtStart}を確定させた、新しい文脈を返す。 */
  public ImportContext withBootstrapAtStart(boolean bootstrap) {
    return new ImportContext(operatorUserId, operatorActiveRoleId, startedAt, bootstrap);
  }
}
