<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Observability Design — menu-navigation (U6)

`construction/menu-navigation/nfr-requirements/observability-requirements.md`(NFR5.1・NFR5.2)、および`nfr-design-questions.md` Q4確定に基づく、menu-navigationユニットの可観測性設計。

## メトリクス実装(NFR5.1対応)

他の実装済みユニットと同水準で、`GET /api/menu`・`/api/menu-items`のHTTPリクエストレイテンシ・エラー率をMicrometer(Spring Boot標準)経由でOTELメトリクスとして計装する。

```java
// 概念設計(実装はCode Generationで確定)
Timer.builder("menu_navigation.get_menu.duration").register(meterRegistry);
Counter.builder("menu_navigation.get_menu.error_count").register(meterRegistry);
Timer.builder("menu_navigation.menu_items_crud.duration").register(meterRegistry);
Counter.builder("menu_navigation.menu_items_crud.error_count").register(meterRegistry);
```

## 相関ID伝播(NFR5.1関連、Q4確定)

`GET /api/menu`・`/api/menu-items`呼び出しにおける相関ID(トレースID)は、呼び出し元・OTEL計装基盤(具体的なライブラリ選定はCode Generation/CI Pipelineで確定)が管理するトレースコンテキストにそのまま乗せる。menu-navigation自身が独自の相関ID生成・伝播の仕組みを持つ必要はない(他ユニットと同一方針)。

## 構造化ログ(NFR5.2対応)

- `/api/menu-items`の作成・更新・削除の実行はINFOレベルの構造化ログ(JSON形式)とし、実行者(activeRoleId)・対象(menuItemId)・操作種別を含める。
- `/api/menu-items`の失敗(400/403/404/409)はERRORレベルの構造化ログとし、失敗理由・リクエストIDを含める。
- `GET /api/menu`自体は参照系の高頻度パスであり、個別のINFOログは出力しない(security-requirements.md NFR2.3参照、ログ量の抑制)。

## アラート・ダッシュボード(NFR5.2対応)

`GET /api/menu`のエラー率が一定閾値を超えた場合にアラートする(アプリ全体共通のエラー率監視基準に従う)。トップ画面・サイドバーの初期表示に直結する高頻度パスであるため、アプリ全体のエラー監視ダッシュボードに含める。`/api/menu-items`は低頻度の管理操作であり、専用の常時監視アラートは設定しない。

## Observability Anti-Requirements(除外事項、`observability-requirements.md`から継承)

- `GET /api/menu`の個別リクエストに対するINFOレベルのログ出力は行わない(高頻度パスのため)。
- `/api/menu-items`専用のダッシュボードは設けない(アプリ全体の監視ダッシュボードに含める)。
