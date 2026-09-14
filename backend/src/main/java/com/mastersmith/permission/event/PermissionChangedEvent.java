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

package com.mastersmith.permission.event;

import com.mastersmith.permission.entity.ScopeType;
import java.time.Instant;

/**
 * assignPermission(またはassignAuxiliaryPermission)による権限変更をAuditLogging(将来ユニット)へ通知するドメインイベント (rules.md
 * BR3.11)。config-engineの{@code ConfigChangedEvent}と同じパターンで、Springの{@code
 * ApplicationEventPublisher}経由でfire-and-forget発行する(security-design.md「監査ログとの連携」)。
 *
 * <p><b>発行粒度(計画Step8で識別した設計判断)</b>: rules.md BR3.11はconfig-import-exportの1回のインポート実行単位のサマリイベントを
 * 求めるが、本ユニット単体はconfig-import-exportの実行単位(1回のインポートに複数のassignPermission呼び出しが含まれること)を知り得ない。
 * そのため本ユニットは各{@code assignPermission}/{@code assignAuxiliaryPermission}呼び出しの粒度でイベントを発行し、実行単位への集約
 * (件数の合算)はconfig-import-export側の責務とする(data-import-exportの{@code ImportExecutedEvent}集約パターンとは異なる設計)。
 *
 * @param targetRoleId 権限が変更されたロール
 * @param scopeType 変更対象のスコープ種別
 * @param scopeRef 変更対象のスコープ参照(BR3.14: 不透明な識別子)
 * @param actor 変更操作を実行した利用者のactiveRoleId(割当操作の実行者)
 * @param occurredAt 変更発生日時
 */
public record PermissionChangedEvent(
    String targetRoleId, ScopeType scopeType, String scopeRef, String actor, Instant occurredAt) {

  public static PermissionChangedEvent of(
      String targetRoleId, ScopeType scopeType, String scopeRef, String actor) {
    return new PermissionChangedEvent(targetRoleId, scopeType, scopeRef, actor, Instant.now());
  }
}
