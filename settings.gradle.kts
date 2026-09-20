// Copyright 2026 agwlvssainokuni
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

rootProject.name = "mastersmith"

// バックエンド(Java 25 + Spring Boot)サブプロジェクト。
// フロントエンド成果物(TypeScript + Vite + React)は将来ここに同梱される
// (backend/src/main/resources/static配下。packagingユニットで結線予定)。
include("backend")

// user-management(U4): 招待メールのHTML生成に用いる自作mustacheエンジン
// (https://github.com/agwlvssainokuni/java-mustache-processor)。Gitサブモジュールとして
// external/java-mustache-processorに取り込み(コミット固定)、複合ビルド(includeBuild)で
// 参照する。backendからは implementation("cherry.mustache:cherry-mustache-core") で使う。
// エンジン側のビルド設定(OWASP依存関係チェックのプラグインを含む)はエンジン側のまま用い、
// このプロジェクトのビルド設定には混ぜない。
includeBuild("external/java-mustache-processor")
