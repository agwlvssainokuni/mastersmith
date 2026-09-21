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

package com.mastersmith.configio.parser;

import com.mastersmith.common.configio.ImportValidationError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 取り込みの検証の誤りを、最大100件まで集める(config-import-export BR9.7)。100件を超えた誤りは、保持せず、総数だけを数える(超えたことは、{@link
 * #truncated()}で示す)。 位置・i18nキー・パラメータを保持する。入力値そのものは、保持しない。
 *
 * <p>同じ位置(field)に、すでに誤りがある場合、各ユニットの検証の誤り({@link
 * #addAll})は、追加しない(パーサーと各ユニットが、同じ項目の同じ問題を、二重に報告しないため。位置ごとに、最初の1件だけ)。
 * 失敗の分類の判定のため、これまでに集めた(保持しなかったものを含む)メッセージキーを覚える({@link #hasMessage})。
 */
public final class ImportErrorCollector {

  /** 1回の応答に含める誤りの最大件数(BR9.7)。 */
  public static final int MAX_ERRORS = 100;

  private final List<ImportValidationError> kept = new ArrayList<>();
  private final Set<String> fields = new HashSet<>();
  private final Set<String> messages = new HashSet<>();
  private int total;

  /** 1件の誤りを加える(同じ位置の誤りも、そのまま加える。パーサーの、位置ごとに1件と決まった報告用)。 */
  public void add(String field, String message) {
    add(ImportValidationError.of(field, message));
  }

  public void add(String field, String message, Map<String, Object> params) {
    add(ImportValidationError.of(field, message, params));
  }

  public void add(ImportValidationError error) {
    total++;
    fields.add(error.field());
    messages.add(error.message());
    if (kept.size() < MAX_ERRORS) {
      kept.add(error);
    }
  }

  /** 各ユニットの検証の誤りを、位置に接頭辞(セクション名)を付けて加える。すでに同じ位置の誤りがあるものは、加えない。 */
  public void addAll(String prefix, List<ImportValidationError> errors) {
    for (ImportValidationError error : errors) {
      ImportValidationError prefixed = error.withFieldPrefix(prefix);
      if (!fields.contains(prefixed.field())) {
        add(prefixed);
      }
    }
  }

  public boolean hasErrors() {
    return total > 0;
  }

  /** 検出した誤りの総数(打ち切り前)。 */
  public int total() {
    return total;
  }

  /** 総数が、保持できる最大件数を超え、一部を保持しなかったか。 */
  public boolean truncated() {
    return total > kept.size();
  }

  /** 保持している誤り(最大100件、加えた順)。 */
  public List<ImportValidationError> errors() {
    return List.copyOf(kept);
  }

  /** 指定のメッセージキーの誤りが、1件でもあったか(保持しなかったものを含む)。 */
  public boolean hasMessage(String message) {
    return messages.contains(message);
  }
}
