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

package com.mastersmith.common.security;

import java.util.Optional;

/**
 * テスト用の{@link OperatorContext}(C15)。認証フィルタを通さず、テストが、操作者(userId・sessionId・activeRoleId)を直接供給する。
 *
 * <p>従来は、リクエストヘッダー({@code X-User-Id}・{@code
 * X-Active-Role-Id})で、操作者を渡していた。ヘッダーを信頼する経路は、どのプロファイルにも残さない (authentication-serviceの機能設計
 * W7・Q9=A)ため、テストは、この部品で操作者を差し替える。テストごとに、{@link #set}で操作者を設定するか、{@link
 * #clear}で、操作者を解決できない状態(認証なし)にする。テストの前後に、{@link #clear}を呼ぶこと({@link
 * TestOperatorContextConfig}のBeanは、コンテキストの中で共有される)。
 *
 * <p>スレッドローカルではなく、単一の値を保持する(MockMvcは、テストと同じスレッドで処理する。同時実行のテストでは、テストのスレッドとは別のスレッドから読まれる)。
 */
public class TestOperatorContext implements OperatorContext {

  private volatile Operator operator;

  @Override
  public Optional<Operator> current() {
    return Optional.ofNullable(operator);
  }

  /** 操作者を設定する。 */
  public TestOperatorContext set(Operator operator) {
    this.operator = operator;
    return this;
  }

  /** 操作者を設定する(sessionIdは、userIdから導く固定の値)。 */
  public TestOperatorContext set(String userId, String activeRoleId) {
    return set(operator(userId, activeRoleId));
  }

  /** テストの期待値(サービスへ渡される操作者)を作る。{@link #set(String, String)}が設定する操作者と等しい。 */
  public static Operator operator(String userId, String activeRoleId) {
    return new Operator(userId, "session-of-" + userId, activeRoleId);
  }

  /** 操作者を解決できない状態(認証なし)にする。 */
  public TestOperatorContext clear() {
    this.operator = null;
    return this;
  }
}
