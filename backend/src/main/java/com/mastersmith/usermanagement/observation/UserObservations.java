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

package com.mastersmith.usermanagement.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * user-managementの観測(スパン)の入口(nfr-design/observability-design.md NFR5.3)。{@link
 * ObservationRegistry}による観測を、サービス・メール送信・ハッシュ計算・
 * C11の各メソッドに付ける。呼び出し元(HTTPリクエストの観測)のトレースコンテキストを継承する子のスパンになる。
 *
 * <p><b>属性(キーと値)には、固定の値だけを使う</b>({@code unit}と、ハッシュ計算の{@code
 * operation}のみ)。メールアドレス・氏名・件名・招待トークン・userIdなど、
 * 入力や利用者に由来する値は、属性に含めない(NFR2.6・NFR2.10)。処理が失敗した場合、例外は観測に記録される(エラーとして)ため、U4の例外のメッセージは、
 * 利用者の値を含めない方針としている(例外の型名と分類だけ)。
 *
 * <p>{@link ObservationRegistry}が構成されていない環境(スライスのテストなど)では、何も観測しない(NOOP)。
 */
@Component
public class UserObservations {

  /** 何も観測しない既定の実装(コンストラクタで組み立てるテストなど、Springの外で用いる)。 */
  public static final UserObservations NOOP = new UserObservations(ObservationRegistry.NOOP);

  private static final String UNIT_KEY = "unit";
  private static final String UNIT_VALUE = "user-management";
  private static final String OPERATION_KEY = "operation";

  private final ObservationRegistry registry;

  @Autowired
  public UserObservations(ObjectProvider<ObservationRegistry> registry) {
    this(registry.getIfAvailable(() -> ObservationRegistry.NOOP));
  }

  public UserObservations(ObservationRegistry registry) {
    this.registry = registry;
  }

  /** 処理を、指定の名前の観測の中で実行し、結果を返す。 */
  public <T> T observe(String name, Supplier<T> action) {
    return newObservation(name).observe(action);
  }

  /** 処理を、指定の名前と、固定の操作名({@code operation})の観測の中で実行し、結果を返す。 */
  public <T> T observe(String name, String operation, Supplier<T> action) {
    return newObservation(name).lowCardinalityKeyValue(OPERATION_KEY, operation).observe(action);
  }

  /** 戻り値のない処理を、指定の名前の観測の中で実行する。 */
  public void run(String name, Runnable action) {
    newObservation(name).observe(action);
  }

  private Observation newObservation(String name) {
    return Observation.createNotStarted(name, registry)
        .lowCardinalityKeyValue(UNIT_KEY, UNIT_VALUE);
  }
}
