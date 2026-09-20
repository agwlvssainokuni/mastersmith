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

import com.mastersmith.usermanagement.entity.User;
import java.util.Optional;

/** {@link UserRepository}のカスタム実装の断片(Spring Dataの規約: 実装クラス名は{@code UserRepositoryCustomImpl})。 */
public interface UserRepositoryCustom {

  /**
   * User行を、行ロック付き(悲観的な書き込みロック、{@code SELECT ... FOR UPDATE}相当)で読み取る。同一Userへの更新・無効化を直列化し、
   * beforeValueが常に直前の確定した値になるようにする(reliability-design.md NFR4.1)。ロック待ちの最大時間は {@code
   * mastersmith.users.db-lock-timeout}。待ちが超過した場合は、Spring Dataの例外変換により、ロック取得失敗の例外が投げられる。
   *
   * <p>呼び出し元のトランザクションの中で用いること(トランザクションがなければ、ロックはただちに解放される)。
   */
  Optional<User> findByIdForUpdate(String userId);
}
