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
