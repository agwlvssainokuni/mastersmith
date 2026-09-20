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

package com.mastersmith.usermanagement.web;

import com.mastersmith.usermanagement.dto.UpdateUserRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code PUT /api/users/{userId}}のJSONオブジェクトから、{@link UpdateUserRequest}を組み立てる。更新可能な項目は{@code
 * name}と{@code roleIds}のみで (rules.md
 * BR4.14)、それ以外の項目(email・status・passwordHash・invitationTokenなど)は、無視せずに{@link
 * UpdateUserRequest#unsupportedFields()}へ集め、サービスが、フィールド単位の422にする。
 *
 * <p>型が違う値({@code name}が文字列でない、{@code roleIds}が文字列の配列でない)は、nullとして扱う(サービスの検証が、{@code
 * name.required}・ {@code roleIds.required}・{@code
 * roleIds.blank}の422にする)。これにより、型の不備でも、認可(401・403)より前に422にならない。
 */
final class UpdateUserRequestFactory {

  private static final String NAME = "name";
  private static final String ROLE_IDS = "roleIds";

  private UpdateUserRequestFactory() {}

  static UpdateUserRequest from(Map<String, Object> body) {
    Map<String, Object> json = body == null ? Map.of() : body;
    String name = json.get(NAME) instanceof String value ? value : null;
    List<String> roleIds = json.get(ROLE_IDS) instanceof List<?> list ? toStrings(list) : null;
    List<String> unsupported = new ArrayList<>();
    for (String key : json.keySet()) {
      if (!NAME.equals(key) && !ROLE_IDS.equals(key)) {
        unsupported.add(key);
      }
    }
    return new UpdateUserRequest(name, roleIds, unsupported);
  }

  private static List<String> toStrings(List<?> values) {
    List<String> result = new ArrayList<>(values.size());
    for (Object value : values) {
      result.add(value instanceof String text ? text : null);
    }
    return result;
  }
}
