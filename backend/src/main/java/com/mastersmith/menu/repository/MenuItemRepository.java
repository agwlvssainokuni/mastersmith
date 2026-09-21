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

package com.mastersmith.menu.repository;

import com.mastersmith.menu.entity.MenuItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * {@link MenuItem}のSpring Data JPAリポジトリ(code-generation-plan.md Step 5)。
 *
 * <p>{@link org.springframework.data.jpa.repository.JpaRepository}は継承せず、audit-loggingの{@code
 * AuditLogEntryRepository}と同様に{@link Repository}(メソッドを持たないマーカーインタフェース)を直接継承したうえで、本ユニットが
 * 実際に必要とするメソッドのみを宣言する({@code findAll}・{@code findById}・{@code existsByParentMenuItemId}・{@code
 * save}・{@code deleteById}、いずれもSimpleJpaRepositoryの実装へシグネチャ一致で委譲される)。
 */
public interface MenuItemRepository extends Repository<MenuItem, String> {

  /** 性能設計(performance-design.md「W1手順1」)のとおり、木構造の組み立てはアプリケーション層で行うため、1クエリで全件取得する。 */
  List<MenuItem> findAll();

  Optional<MenuItem> findById(String menuItemId);

  /** NFR4.4: DELETE時の子孫MenuItem存在確認(直接の子が1件でも存在すれば、その子孫を通じて間接的な子孫も必ず存在する)。 */
  boolean existsByParentMenuItemId(String parentMenuItemId);

  MenuItem save(MenuItem menuItem);

  void deleteById(String menuItemId);

  long count();

  /**
   * 全件を一括で削除する(config-import-exportの取り込みの全置換。BR9.14)。削除した件数を返す。永続化コンテキストは更新しないため、呼び出し元は、続けて{@code
   * flush}・必要なら{@code clear}する。
   */
  @Modifying
  @Query("delete from MenuItem")
  int deleteAllItems();
}
