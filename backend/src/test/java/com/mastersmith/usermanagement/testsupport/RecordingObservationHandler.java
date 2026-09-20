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

package com.mastersmith.usermanagement.testsupport;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 観測(スパン)の名前・属性(低・高カーディナリティのキーと値)・エラーを記録する、テスト用の{@link ObservationHandler}。スパンの属性に、メールアドレス・氏名・
 * 件名・トークンが含まれないことの検査に用いる(実際のトレーシングのブリッジは、この属性をスパンの属性として書き出す)。
 */
public class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

  /** 記録された観測1件。 */
  public record Recorded(String name, List<String> keyValues, String error) {

    /** 検査用の、すべての値を連結した文字列。 */
    public String haystack() {
      return name + " " + String.join(" ", keyValues) + " " + (error == null ? "" : error);
    }
  }

  private final List<Recorded> recorded = Collections.synchronizedList(new ArrayList<>());

  @Override
  public boolean supportsContext(Observation.Context context) {
    return true;
  }

  @Override
  public void onStop(Observation.Context context) {
    List<String> keyValues =
        java.util.stream.Stream.concat(
                context.getLowCardinalityKeyValues().stream(),
                context.getHighCardinalityKeyValues().stream())
            .map(kv -> kv.getKey() + "=" + kv.getValue())
            .collect(Collectors.toList());
    Throwable error = context.getError();
    String errorText =
        error == null ? null : error.getClass().getName() + ": " + error.getMessage();
    recorded.add(
        new Recorded(context.getName() + "|" + context.getContextualName(), keyValues, errorText));
  }

  public List<Recorded> recorded() {
    synchronized (recorded) {
      return List.copyOf(recorded);
    }
  }

  /** 名前(観測の名前)が{@code prefix}で始まる記録だけ。 */
  public List<Recorded> named(String prefix) {
    return recorded().stream().filter(r -> r.name().startsWith(prefix)).toList();
  }

  public void clear() {
    recorded.clear();
  }
}
