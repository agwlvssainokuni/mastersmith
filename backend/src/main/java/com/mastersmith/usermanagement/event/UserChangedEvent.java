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

package com.mastersmith.usermanagement.event;

import java.time.Instant;

/**
 * User(招待・招待受諾・更新・無効化・初期管理者作成)の変更操作を、AuditLogging(U7)へ通知するドメインイベント(entities.md UserChangedEvent、
 * rules.md BR4.9)。内部設定DBへ永続化しない値オブジェクトで、config-engineの{@code ConfigChangedEvent}と同じく、コミット後に発行する
 * ({@link UserChangedEventPublisher})。
 *
 * @param operation 操作種別
 * @param targetType 変更対象の種別(固定値{@value #TARGET_TYPE_USER})
 * @param targetId 変更対象のuserId
 * @param beforeValue 変更前のスナップショット。新規作成(初回のINVITED、BOOTSTRAPPED)はnull。再招待のINVITEDは非null
 * @param afterValue 変更後のスナップショット
 * @param actor
 *     操作者。INVITED・UPDATED・DISABLEDは操作した管理者のuserId、ACTIVATEDは受諾したUser自身のuserId、BOOTSTRAPPEDは
 *     システム識別子{@value #SYSTEM_ACTOR}(userIdではない値をuserIdとして偽装しない)
 * @param occurredAt 発生日時
 */
public record UserChangedEvent(
    UserChangeOperation operation,
    String targetType,
    String targetId,
    UserSnapshot beforeValue,
    UserSnapshot afterValue,
    String actor,
    Instant occurredAt) {

  /** {@link #targetType}の固定値。 */
  public static final String TARGET_TYPE_USER = "User";

  /** 初期管理者の自動作成のactor(システム識別子)。 */
  public static final String SYSTEM_ACTOR = "system";

  public static UserChangedEvent of(
      UserChangeOperation operation,
      String targetId,
      UserSnapshot beforeValue,
      UserSnapshot afterValue,
      String actor) {
    return new UserChangedEvent(
        operation, TARGET_TYPE_USER, targetId, beforeValue, afterValue, actor, Instant.now());
  }
}
