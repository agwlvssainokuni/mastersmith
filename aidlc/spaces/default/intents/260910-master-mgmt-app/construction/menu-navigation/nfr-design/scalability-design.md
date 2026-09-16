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

# Scalability Design — menu-navigation (U6)

`construction/menu-navigation/nfr-requirements/scalability-requirements.md`(NFR3.1)に基づく、menu-navigationユニットのスケーラビリティ設計。

## 想定利用規模の継承(NFR3.1対応)

MenuItem総数は数十〜百件程度、階層深さ3〜4階層程度を想定する(NFR3.1)。この規模では、`performance-design.md`の「全件一括取得+アプリケーション層での木構造組み立て」方式で十分であり、追加のインデックス設計・パーティショニングは不要と判断する。

## 成長への対応

業務メニューの追加は業務担当者の低頻度な設定操作(`/api/menu-items`)による緩やかな増加を想定する。数千件規模・5階層以上への拡大は本MVPスコープの対象外(`nfr-requirements/performance-requirements.md`のPerformance Anti-Requirements)であり、将来その規模に達した場合は、全件一括取得方式からページング・遅延ロード方式への切り替え、およびMenuItemツリーのキャッシュ導入(Q2で見送った選択肢)を再検討事項として引き継ぐ。

## 同時アクセス

`GET /api/menu`は複数利用者からの同時アクセスを想定する(読み取り専用のためロック競合は発生しない)。`/api/menu-items`は低頻度の管理操作であり、複数管理者が同時に同一MenuItemを変更した場合の明示的な排他制御(楽観ロック等)は行わない。NFR4が定める楽観ロック要件はFR6.3(record-edit-engineの責務)に限定されており、設定情報である`MenuItem`は元々その適用範囲外である。

## Scalability Anti-Requirements(除外事項、`scalability-requirements.md`から継承)

- 数千件規模のMenuItem・5階層以上の深い階層に対する明示的なキャッシュ・非再帰アルゴリズムの設計は行わない。
- 水平スケーリング(複数インスタンス構成)固有の設計は行わない。
