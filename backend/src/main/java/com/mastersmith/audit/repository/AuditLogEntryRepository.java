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

package com.mastersmith.audit.repository;

import com.mastersmith.audit.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * {@link AuditLogEntry}のSpring Data JPAリポジトリ(security-design.md「データ完全性」、rules.md BR7.5)。
 *
 * <p>{@link org.springframework.data.repository.CrudRepository}/{@link
 * org.springframework.data.jpa.repository.JpaRepository}は継承せず、{@link
 * Repository}(メソッドを持たないマーカーインタフェース)を直接継承したうえで、{@code save}(INSERT相当)・{@code
 * findByTargetType}・{@code findAll}(いずれもSimpleJpaRepositoryの実装へシグネチャ一致で委譲される、Spring Data
 * JPAの標準的な手法)のみを宣言する。UPDATE/DELETEに相当するメソッドをアプリケーション層へ一切持ち込まないことで、監査ログの追記専用(append-only)制約をリポジトリ契約レベルで強制する。
 */
public interface AuditLogEntryRepository extends Repository<AuditLogEntry, String> {

  /** INSERT相当(新規AuditLogEntryは常に新しいIDを持つため、既存行の更新は発生しない)。 */
  AuditLogEntry save(AuditLogEntry entry);

  /** BR7.9: targetType指定時の等価一致フィルタ + occurredAt降順ページング(呼び出し側がSortを指定する)。 */
  Page<AuditLogEntry> findByTargetType(String targetType, Pageable pageable);

  /** BR7.9: targetType未指定時のoccurredAt降順ページング(呼び出し側がSortを指定する)。 */
  Page<AuditLogEntry> findAll(Pageable pageable);
}
