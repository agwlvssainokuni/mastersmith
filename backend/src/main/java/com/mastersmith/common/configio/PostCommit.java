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

package com.mastersmith.common.configio;

import java.util.Objects;

/**
 * 取り込みの反映(伝播{@code MANDATORY}のメソッド)が返す、トランザクションの確定後に行う2つの動作 (config-import-export
 * nfr-design/reliability-design.md NFR4.2、logical-components.md 追補3)。
 *
 * <p>各ユニットは、{@code afterCommit}を自身では登録せず、この値を戻り値に含めて返す。{@code PostCommitCoordinator}が、1つの{@code
 * TransactionSynchronization}の中で、固定した順序(無効化 → 個別イベント →
 * 成功の監査イベント)で、それぞれ独立に実行する(1つの動作の例外が、他のユニットの動作を妨げないため)。
 *
 * @param invalidateCaches キャッシュの無効化(メモリ上のフラグ・世代番号の更新だけで、失敗しえない)
 * @param publishEvents 個別の変更イベントの発行(例外は、呼び出し元が握りつぶす)
 */
public record PostCommit(Runnable invalidateCaches, Runnable publishEvents) {

  /** 何もしない動作(キャッシュを持たない・イベントを発行しない場合)。 */
  public static final PostCommit NONE = new PostCommit(() -> {}, () -> {});

  public PostCommit {
    Objects.requireNonNull(invalidateCaches, "invalidateCaches");
    Objects.requireNonNull(publishEvents, "publishEvents");
  }
}
