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

package com.mastersmith.permission.bootstrap;

import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import org.springframework.stereotype.Component;

/**
 * ブートストラップ状態(RBAC設定が一切存在しない初期状態)の判定(rules.md BR3.13)。
 *
 * <p>PrimaryPermission行がシステム全体で1件も存在しない間に限り、(a) {@code canAccessScreen(activeRoleId,
 * "config-import-export")}は無条件にtrueを返し、(b) {@code
 * assignPermission}はBR3.8の権限昇格チェックを適用しない。PrimaryPermission行が1件でも存在するようになった時点でブートストラップ状態は 永続的に終了する。
 */
@Component
public class BootstrapStateChecker {

  private final PrimaryPermissionRepository primaryPermissionRepository;

  public BootstrapStateChecker(PrimaryPermissionRepository primaryPermissionRepository) {
    this.primaryPermissionRepository = primaryPermissionRepository;
  }

  /** システム全体でPrimaryPermission行が1件も存在しない場合にtrue。 */
  public boolean isBootstrapState() {
    return primaryPermissionRepository.count() == 0;
  }
}
