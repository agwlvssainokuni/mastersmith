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

package com.mastersmith.configio.exception;

import java.io.Serial;

/** 操作者が解決できない(認証されていない)要求(401、BR9.4)。 */
public class ConfigImportUnauthorizedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public ConfigImportUnauthorizedException() {
    super("operator could not be resolved");
  }
}
