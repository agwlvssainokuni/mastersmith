# Reliability Requirements: notification

自宅サーバ1台構成のため、SLA/SLO数値目標(稼働率%等)は設けない(requirements.md NFR1、team.md Deployment)。

## NFR-FAILSAFE.1: SMTP送信失敗時のリトライなし

SMTP送信失敗(接続不可・拒否等)はログに記録し、リトライは行わない(BR3.1)。イベント自体はこの時点で失われる。これは明示的なトレードオフであり、利用者本人が再操作で再試行できるフロー(パスワード忘れ〈BR1.5〉・メールアドレス変更〈BR1.6〉)は利用者の再実行に委ねることでカバーする。管理者起点のアカウント作成通知(BR1.1)についての再送手段はMVPスコープに含めない(functional-design-questions.md Q1)。
