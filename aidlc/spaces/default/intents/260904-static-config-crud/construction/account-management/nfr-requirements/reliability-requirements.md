# Reliability Requirements: account-management

自宅サーバ1台構成のため、SLA/SLO数値目標(稼働率%等)は設けない(requirements.md NFR1、team.md Deployment)。

## NFR-CONSISTENCY.1: 初期ロール割り当て失敗時の部分失敗許容

アカウント作成時、契約#4によるAccount作成が成功した後、契約#21による初期ロール割り当てが失敗(存在しないroleIdを含む場合)しても、作成済みのAccount自体はロールバックしない(BR1.3)。これは明示的な設計判断であり、400応答本体に作成済みのaccountIdを含めることで、管理者がPUT編集で改めてロールを設定できるようにする(同一emailでの再作成を防ぐため)。

## NFR-FAILSAFE.1: アカウント無効化とリフレッシュトークン失効の依存関係

アカウント無効化(BR4.1)は契約#4経由でauthに無効化を指示し、authが該当accountIdの有効なリフレッシュトークンを即時失効させることになっている。しかし、この即時失効の実装はauth Unit側でまだ反映されておらず、auth Unitのfunctional-design終了ゲート(R-03)およびnfr-requirements(R-01)で既に繰延べ事項として記録済みの未解消ギャップである。本Unit(account-management)側の無効化呼び出し自体は契約仕様どおり正しく実装するが、この既知の依存先ギャップが解消されるまでは、無効化後も最大7日間リフレッシュトークンによるセッションが継続し得ることを記録しておく。

## NFR-FAILSAFE.2: 通知送信失敗の扱い

AccountCreatedEvent(BR1.4)の送信失敗はログ記録のみで、リトライしない(契約#9 Q12の耐障害性方針)。アカウント作成自体の成否には影響しない。
