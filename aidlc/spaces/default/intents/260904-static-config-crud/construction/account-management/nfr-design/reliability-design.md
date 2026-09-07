# Reliability Design: account-management

自宅サーバ1台構成のため、SLA/SLO数値目標は設けない。

## 初期ロール割り当て失敗時の部分失敗許容

サービスコンポーネントは、auth(契約#4)によるAccount作成が成功した後、permission(契約#21)による初期ロール割当が失敗(存在しないroleIdを含む)しても、作成済みのAccountをロールバックしない(BR1.3、NFR-CONSISTENCY.1)。400応答本体に作成済みaccountIdを含め、管理者がPUT編集で改めてロールを設定できるようにする。

## アカウント無効化とリフレッシュトークン失効の依存関係

アカウント無効化はauth(契約#4)へ委譲し、authが該当accountIdの有効なリフレッシュトークンを即時失効させることになっている。この即時失効の実装はauth Unit側で未反映のままであり、auth Unit自身のfunctional-design・nfr-design双方で既に記録済みの未解消事項である(NFR-FAILSAFE.1)。本Unit(account-management)側の無効化呼び出し自体は契約仕様どおり正しく実装するが、この既知の依存先ギャップは解消されるまで残る。

## 通知送信失敗の扱い

AccountCreatedEventの送信失敗(notification側)はログ記録のみで、リトライしない(契約#9 Q12の耐障害性方針、NFR-FAILSAFE.2)。アカウント作成自体の成否には影響しない。

## リトライ・サーキットブレーカー

auth・permissionへの委譲呼び出し自体のリトライは行わない。失敗時は各契約のFailure behaviorに従いエラーを呼び出し元へ伝播させる。
