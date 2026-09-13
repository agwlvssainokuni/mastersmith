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

package com.mastersmith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MasterSmithアプリケーションのエントリポイント。
 *
 * <p>本クラスは{@code com.mastersmith}直下に置き、以降追加される各Unit(config-engine = {@code
 * com.mastersmith.config}、permission-engine = {@code com.mastersmith.permission}等)を
 * コンポーネントスキャンの対象に含める。単一の実行可能WARとしてビルドし、フロントエンド成果物を 同梱してSpring Bootから配信する(team.md確定のパッケージング方針)。
 */
@SpringBootApplication
public class MastersmithApplication {

  public static void main(String[] args) {
    SpringApplication.run(MastersmithApplication.class, args);
  }
}
