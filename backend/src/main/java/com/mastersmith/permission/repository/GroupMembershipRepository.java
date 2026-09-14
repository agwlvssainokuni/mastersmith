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

package com.mastersmith.permission.repository;

import com.mastersmith.permission.entity.GroupMembership;
import com.mastersmith.permission.entity.GroupMembershipId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link GroupMembership}のSpring Data JPAリポジトリ。{@code findByIdUserId}が、選択可能ロール一覧算出(rules.md BR3.2,
 * BR3.3)における「Userが所属するGroup一覧」の検索を担う(scalability-design.mdの{@code user_id}単体インデックス)。
 */
public interface GroupMembershipRepository
    extends JpaRepository<GroupMembership, GroupMembershipId> {

  List<GroupMembership> findByIdUserId(String userId);
}
