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

package com.mastersmith.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ColumnConfig.validationRuleの構造化データ(entities.md, rules.md BR1.9)。
 *
 * <p>ルール種別(required/minLength/maxLength/min/max/pattern等)をキーとし、各ルールの
 * パラメータ値を保持する。エラーメッセージの固定文字列は保持せず、i18nキー(BR1.6の導出規則) を呼び出し側で解決する。内部設定DB上はJSON型カラムとして永続化する
 * (tech-stack-decisions.md)。
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: レコードの派生メソッド(例: {@link
 * #isEmpty()})がJacksonのBean introspectionにより追加プロパティとしてシリアライズ されうるため、デシリアライズ時にそれらを許容する。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValidationRule(Map<String, Object> rules) {

  public ValidationRule {
    rules = rules == null ? Map.of() : Map.copyOf(rules);
  }

  public static ValidationRule empty() {
    return new ValidationRule(Map.of());
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEmpty() {
    return rules.isEmpty();
  }

  public boolean has(String ruleType) {
    return rules.containsKey(ruleType);
  }

  public Object get(String ruleType) {
    return rules.get(ruleType);
  }

  /** テスト・呼び出し側での簡便な組み立て用ビルダー。 */
  public static final class Builder {
    private final Map<String, Object> rules = new LinkedHashMap<>();

    public Builder rule(String ruleType, Object value) {
      rules.put(ruleType, value);
      return this;
    }

    public ValidationRule build() {
      return new ValidationRule(rules);
    }
  }
}
