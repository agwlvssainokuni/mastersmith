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

package com.mastersmith.usermanagement.dto;

import java.util.List;

/**
 * ユーザー情報更新のリクエスト(C5、{@code PUT /api/users/{userId}})。更新可能な項目は{@code name}と{@code
 * roleIds}のみ(rules.md BR4.14)。
 *
 * @param unsupportedFields リクエストに含まれていた、更新できない項目の名前(email・status・passwordHashなど)。指定されていれば422にする
 *     (無視しない)。JSONからの組み立ては、Web層が行う
 */
public record UpdateUserRequest(String name, List<String> roleIds, List<String> unsupportedFields) {

  public UpdateUserRequest {
    unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
  }

  public UpdateUserRequest(String name, List<String> roleIds) {
    this(name, roleIds, List.of());
  }
}
