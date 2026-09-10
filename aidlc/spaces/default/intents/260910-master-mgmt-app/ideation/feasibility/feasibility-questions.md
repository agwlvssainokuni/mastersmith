# Feasibility & Constraints — Questions

## Q1. このアプリは既存のどのシステムと連携・統合する必要がありますか?

- A. 特になし。独立したスタンドアロンのアプリとして、対象RDBMSに直接接続するのみ
- B. 社内の認証基盤(SSO/LDAP等)と連携する必要がある
- C. 前身ツール「MasterMeister」の設定やデータを何らかの形で引き継ぐ必要がある
- D. Not yet defined
- X. Other (please specify)

[Answer]: A. 特になし。独立したスタンドアロンのアプリとして、対象RDBMSに直接接続するのみ

## Q2. 規制・コンプライアンス上の要件(PCI、HIPAA、SOC2、データ所在地規制など)はありますか?

意図把握ステージでは「機微な情報は含まれない」との回答をいただいています。念のため確認させてください。

- A. 特になし。社内利用のみで、規制対象データは扱わない
- B. 社内の情報セキュリティポリシー(規制ではなく自社基準)には準拠する必要がある
- C. 該当する規制がある
- D. Not applicable
- X. Other (please specify)

[Answer]: A. 特になし。社内利用のみで、規制対象データは扱わない

## Q3. 対象とするRDBMS(データベース製品)は何を想定していますか?

参考として、前身ツール「MasterMeister」ではPostgreSQL/MySQL/MariaDBを想定していた実績があるとうかがっています。

- A. PostgreSQLのみ
- B. MySQL/MariaDBのみ
- C. PostgreSQL/MySQL/MariaDBの複数対応(MasterMeisterと同様)
- D. Not yet defined
- X. Other (please specify)

[Answer]: C. PostgreSQL/MySQL/MariaDBの複数対応(MasterMeisterと同様)

## Q4. 想定される利用規模(同時接続数・利用者数)はどの程度ですか?

参考として、MasterMeisterでは約10名規模での利用実績があるとうかがっています。

- A. 同程度の小規模(10名前後、同時接続は数名程度)
- B. もう少し大きい規模(数十名程度)
- C. 業務システムごとに規模は変動する(転用先次第)
- D. Not yet defined
- X. Other (please specify)

[Answer]: B. もう少し大きい規模(数十名程度)

## Q5. 開発チームの現在の技術スタックとスキルセットを教えてください(使用言語、フレームワーク、実行環境など)

- A. Javaベース(WARとしてビルドする想定のため)
- B. その他の言語・フレームワーク
- C. 未確定、これから選定する
- D. Not yet defined
- X. Other (please specify)

[Answer]: X. Other — backend = Java 25 + Spring Boot(最新) + Gradle(最新)、frontend = TypeScript + Vite + React。開発時はdev serverのproxyでbackendへリバースプロキシ、実行環境では実行可能WARに同梱(Spring Bootからfrontendを配信)し、CORS設定なしで実行する構成。

## Q6. 予算・スケジュール面での制約はありますか?

- A. 特に厳密な制約はない(個人/小規模プロジェクトとして進める)
- B. 特定の期日までに一定の機能を完成させる必要がある
- C. 予算(工数・費用)の上限が決まっている
- D. Not applicable
- X. Other (please specify)

[Answer]: A. 特に厳密な制約はない(個人/小規模プロジェクトとして進める)

## Q7. 組織的な障害(変更凍結期間、競合する優先タスクなど)はありますか?

- A. 特になし
- B. 他の優先度の高いタスクと並行して進める必要があり、着手ペースが制約される
- C. 特定の期間、変更や着手ができない制約がある
- D. Not applicable
- X. Other (please specify)

[Answer]: A. 特になし

## Q8. インフラ・実行環境として想定しているものはありますか?(AWS等のクラウド、オンプレミス等)

意図把握ステージでは「実環境へのデプロイ・環境構築は今回のワークフローでは対象外」との確認をいただいています。将来的な想定があれば教えてください。

- A. 特に想定なし。実行可能WARを生成するところまでが今回のスコープで、実行環境は未定
- B. AWSを想定している
- C. オンプレミス環境を想定している
- D. Not yet defined
- X. Other (please specify)

[Answer]: A. 特に想定なし。実行可能WARを生成するところまでが今回のスコープで、実行環境は未定

## Assumptions & Open Questions

None.

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
