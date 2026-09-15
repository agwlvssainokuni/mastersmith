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

# Scalability Design — audit-logging (U7)

`construction/audit-logging/nfr-requirements/scalability-requirements.md`(NFR3.1〜NFR3.4)に基づく、audit-loggingユニットのスケーラビリティ設計。

## 想定利用規模の継承(NFR3.1・NFR3.4対応)

NFR3(利用規模は数十名程度)・最大同時アクセス50ユーザーをそのまま継承する。監査ログ閲覧画面は管理メニュー配下の低頻度アクセス画面であり、`performance-design.md`のDBコネクションプール設計(専用プール分離なし)で十分と判断する。

## 成長への対応(NFR3.2・NFR3.3対応)

AuditLogEntryは無期限保持(FR8.3)のため件数が増加し続ける。年間数万〜数十万件程度の増加想定(NFR3.2)に対し、現段階ではテーブルパーティショニング等の追加戦略は導入しない。`performance-design.md`のインデックス設計(`occurred_at`降順+`target_type`複合)により、この規模までは応答時間(NFR1.1)を維持できる設計とする。

## 再検討トリガーの実装への反映(NFR3.3対応、アーキテクチャレビュー指摘R-02対応)

`scalability-requirements.md`が定めた再検討トリガー(AuditLogEntryの行数が8万行、NFR1想定上限10万行の8割に達した時点)について、本設計では以下の運用的な監視方法とする。専用のダッシュボード・自動アラートの実装は本Boltでは必須としない(`scalability-requirements.md` NFR3.3の判断を踏襲)。

- 運用者が月次を目安に、内部設定DBへの問い合わせ(例: `SELECT COUNT(*) FROM audit_log_entry`)でAuditLogEntryの行数を確認する。
- 8万行に到達した時点で、以下のいずれかの再検討を開始する(具体的な実装は本Boltのスコープ外、将来課題として引き継ぐ):
  - `performance-design.md`のページング方式をオフセット方式からキーセット(カーソル)方式へ切り替える
  - 読み取り専用の別ストアへの分離(project.md Out of Scopeにより本MVPでは削除・アーカイブ機能を実装しないため)

## 同時アクセス(NFR3.4対応)

監査ログ閲覧画面は業務メニューの一覧画面と比べて同時アクセス数が小さいと想定する。本ユニット固有の同時実行制御(分散ロック等)は設けない。イベント購読処理(BR7.1〜BR7.4)は各購読メソッドの呼び出しごとに独立して実行され、複数イベントが並行して発行される場合でも記録順序の厳密な保証は行わない(BR7.8)ため、同時実行に伴う排他制御は不要である。

## Scalability Anti-Requirements(除外事項、`scalability-requirements.md`から継承)

- テーブルパーティショニングは現段階では導入しない(8万行到達までの再検討事項として記録済み)。
- 水平スケーリング(複数インスタンス構成)固有の設計は行わない(NFR3.4の同時アクセス規模では不要と判断)。
