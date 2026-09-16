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
 * assignPermission(またはassignAuxiliaryPermission)による権限変更をAuditLogging(既存実装済みユニット)へ通知するための
 * ドメインイベント (rules.md BR3.11)。config-engineの{@code ConfigChangedEvent}と同じパターンで、Springの{@code
 * ApplicationEventPublisher}経由でfire-and-forget発行することを想定する(security-design.md「監査ログとの連携」)。
 *
 * <p><b>発行粒度(アーキテクチャレビュー iteration 1, NOT-READY, R-01対応で確定)</b>: rules.md BR3.11は「config-import-exportの1回の
 * インポート実行につき1件のサマリイベント(実行者・変更件数・日時)、個々の変更前後の値は含めない」ことを求める。この粒度は
 * config-import-export(1回のインポート実行に複数のassignPermission呼び出しが含まれることを知り得る唯一のユニット)自身の責務であり、
 * permission-engine自身は{@code assignPermission}/{@code assignAuxiliaryPermission}の呼び出し単位ではこのイベントを発行しない
 * (以前の実装は呼び出し単位で発行しておりBR3.11に反していたため是正した)。本レコードはconfig-import-export自身のCode
 * Generation(未着手)がBR3.11の粒度で発行する際の契約として温存する。
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
