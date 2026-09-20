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

package com.mastersmith.usermanagement.repository;

import com.mastersmith.usermanagement.entity.UserStatus;
import java.util.List;

/**
 * ユーザー一覧(W7)用の射影。{@code passwordHash}と{@code invitationToken}を持たない型とし、これらを一覧のために読み出さない
 * (performance-design.md NFR1.1、security-design.md NFR2.2)。
 */
public record UserSummary(
    String userId, String name, String email, UserStatus status, List<String> roleIds) {}
