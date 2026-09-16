# NFR Design Questions — menu-navigation (U6)

`construction/menu-navigation/nfr-requirements/`の確定要件(GET /api/menuは3秒/95%ile、/api/menu-itemsは5秒、想定規模数十〜百件・3〜4階層、NFR1.1で指摘したコールドキャッシュ時の残存リスク、NFR4.4のカスケード削除拒否デフォルト)を、具体的な実装アーキテクチャに落とし込むための質問。

## Q1: PermissionEngineへの権限問い合わせの実装方式(NFR1.1のコールドキャッシュリスクへの対応)

`nfr-requirements/performance-requirements.md` NFR1.1は、リーフ項目ごとに`canAccessScreen`を逐次呼び出す場合、コールドキャッシュ時に3秒予算を超過しうる残存リスクを指摘し、フォローアップ候補として「キャッシュのプリウォーム」または「一括権限判定APIの追加」を挙げています。本Boltでの実装方針はどうしますか。

- A. 逐次呼び出し(1リーフ項目につき1回の`canAccessScreen`呼び出し)のまま実装する。PermissionEngine側のキャッシュ(TTL30秒)は同一ロールでの再訪問により自然にウォームされるため、初回アクセス時の一時的な超過は許容されるリスクとして受け入れ、追加実装は行わない(YAGNI、実測で問題が顕在化した場合に対応する)
- B. PermissionEngine側に一括権限判定API(`canAccessScreens(activeRoleId, screenKeys: List<String>)`)の追加を提案する(本Boltのスコープ外のPermissionEngine側変更が必要になるため、別途調整が必要)
- X. Other (please specify)

[Answer]: A

## Q2: MenuItemツリー構造のキャッシュ方針

MenuItem階層は`/api/menu-items`による低頻度な書き込みに対し、`GET /api/menu`は高頻度に読み取られます(読み取り:書き込み比が非常に高い)。MenuItemツリー自体(木構造・attributes)をアプリケーション内でキャッシュしますか。

- A. キャッシュしない。NFR3.1の想定規模(数十〜百件)ではDBからの毎回読み取り(内部設定DB、embedded)で十分に高速であり、キャッシュ導入による無効化ロジックの複雑さに見合わない
- B. PermissionEngineの`PermissionCacheConfig`と同様にCaffeineで短TTLキャッシュする(`/api/menu-items`での書き込み時は`invalidateAll()`で全体無効化する方式を踏襲)
- X. Other (please specify)

[Answer]: A

## Q3: DELETE時の子孫存在チェック実装方式

`nfr-requirements/reliability-requirements.md` NFR4.4により、子孫を持つMenuItemの削除は409 Conflictで拒否する既定方針が確定しています。子孫の存在確認はどう実装しますか。

- A. 削除対象の`menuItemId`を`parentMenuItemId`に持つMenuItemが1件でも存在するかをDBへ問い合わせ(`EXISTS`クエリ相当)、存在すれば409を返す
- B. 事前にアプリケーション内で全MenuItemをロードし、メモリ上のツリー構造から子の有無を判定する
- X. Other (please specify)

[Answer]: A

## Q4: ログの相関ID伝播

`nfr-requirements/observability-requirements.md` NFR5.1・NFR5.2に関連し、`GET /api/menu`・`/api/menu-items`呼び出しの相関ID(トレースID)の伝播方式はどうしますか。

- A. 呼び出し元・OTEL計装基盤(具体的なライブラリ選定はCode Generation/CI Pipelineで確定)が管理するトレースコンテキストにそのまま乗せる。menu-navigation自身が独自の相関ID生成・伝播の仕組みを持つ必要はない(他ユニットと同一方針)
- X. Other (please specify)

[Answer]: A

## Q5: 論理コンポーネント境界(Infrastructure Designへの橋渡し)

本プロジェクトは単一の実行可能WAR(単一JVMプロセス)として動作し、AWS等のクラウドインフラは対象外(Operationフェーズの全ステージはSKIP、team.md確定事項)です。`logical-components.md`が扱う「サービス境界・障害ドメイン・影響範囲(blast radius)」をどう位置づけますか。

- A. menu-navigationは単一WARプロセス内の論理モジュールであり、独立したデプロイ単位ではない。障害ドメインは自身のDB読み書き処理(内部設定DB、embedded)とPermissionEngineへの同期呼び出しに限定される。クラウドインフラ・Infrastructure Design(3.4)は本プロジェクトの対象外であるため、logical-components.mdはプロセス内の論理的な責務分離・障害ドメインの説明に留め、AWSサービスの選定は行わない(audit-loggingユニットと同一方針)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
