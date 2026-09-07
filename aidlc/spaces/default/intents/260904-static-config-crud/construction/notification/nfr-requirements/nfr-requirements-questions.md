# NFR Requirements Questions: notification

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、notification固有に新たな数値目標は追加しない。functional-design(rules.md BR1.1〜BR3.1)で既に確定済みの内容(6種のイベント購読・メール送信、Mustacheテンプレート、SMTP送信失敗時のログ記録のみ・リトライなし)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

notification Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし)を踏襲。SMTP送信の成否は主処理をブロックしない非同期的な扱いとする(BR3.1、送信失敗時もイベント発行元の処理には影響しない)。

**security-requirements.md**: メール本文に埋め込むトークン(registrationToken・resetToken・changeToken)はURLの一部として送信するが、SMTP送信失敗時のログには含めない(BR3.1、機微情報を除く)。Mustacheテンプレート(java-mustache-processor)描画時、テンプレート変数(利用者名・変更内容等)はHTMLエスケープされた状態で埋め込み、メール本文へのHTML/スクリプト注入を防ぐ。

**scalability-requirements.md**: NFR2(1インスタンス=1業務)を踏襲。EmailDispatchは永続化されないため、蓄積によるスケーラビリティ課題は生じない(entities.md)。

**reliability-requirements.md**: 自宅サーバ1台構成のためSLA/SLO数値目標は設けない。SMTP送信失敗時はログ記録のみでリトライしない(BR3.1)、これは明示的なトレードオフである。利用者本人が再操作で再試行できるフロー(パスワード忘れ・メールアドレス変更)は利用者の再実行に委ねることでカバーし、管理者起点のアカウント作成通知(BR1.1)についての再送手段はMVPスコープに含めない(functional-design-questions.md Q1)。

**observability-requirements.md**: NFR3を踏襲。SMTP送信失敗はEmailDispatchの内容(宛先・templateId、機微情報を除く)をログに記録する(BR3.1)。これが本Unitの主要な可観測性要件であり、送信失敗の永続的な記録・追跡は行わない(entities.md)。

**tech-stack-decisions.md**: 自作Mustacheエンジンjava-mustache-processor(BR2.1)を記載する。

**traceability.json**: upstream_ids = NFR1, NFR2, NFR3(OK)。NFR4・NFR6〜NFR9はN/A(横断方針または他Unit担当)。NFR5は、本Unitが永続エンティティを持たない(entities.md)ためN/Aとする。NFR7は本Unit自身がパスワードを扱わないためN/Aとする。

[Answer]: Looks correct
