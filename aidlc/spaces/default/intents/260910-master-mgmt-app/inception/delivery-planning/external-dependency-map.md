# External Dependency Map — MasterSmith(マスタ管理アプリ)

`delivery-planning-questions.md` Q5の確定回答(A: 外部依存なし)に基づき、本ドキュメントは軽量な記載に留める。

## 確認結果

チーム外の依存(外部API・データ提供・承認プロセス・他チームからの引き継ぎ)は**存在しない**。

根拠(`requirements.md` Constraints / Out of Scope):
- **既存システム連携なし**: スタンドアロンアプリとして対象RDBMSに直接接続するのみ。
- **社内認証基盤(SSO/LDAP等)との連携は対象外**。
- **実環境へのデプロイ、環境構築、監視基盤の構築、性能検証は本ワークフロー対象外**(Operationフェーズの全ステージがSKIP対象、`team-practices.md` Deployment節)。
- **MasterMeisterとの連携・データ移行は行わない**(別アプリとして新規立ち上げ)。

## 外部依存に準じる注意事項(参考、ブロッキングではない)

以下は「外部チームからの依存」ではないが、Bolt実行順序に影響しうる既知のフォローアップ事項として記録する(いずれも本プロジェクト内で解消可能であり、外部承認や外部チームの作業を待つものではない)。

| 事項 | 内容 | 影響するBolt | 対応 |
|---|---|---|---|
| FR2.7の要件定義書文言修正 | 要件定義書「管理画面から設定可能」と設計(`application.yml`方式)の不一致が未解消 | Bolt 7(authentication-service完全実装) | Bolt7着手前に要件定義書側の文言修正を完了させる(`unit-of-work.md` U5・`contract-summary.md` C4引継ぎ事項) |
| パスワードハッシュ化アルゴリズム未選定 | bcrypt/argon2等の具体的選定が未定(`requirements.md` Open Questions) | Bolt 6(user-management完全実装)、Bolt1(Walking Skeleton) | Functional Design(3.1)で選定する |

いずれもチーム内で解消可能な事項であり、Bolt実行を外部要因でブロックするものではない。
