# Logical Components: audit-log

## コンポーネント構成

audit-logは単一のSpring Bootモジュール(パッケージ)内に、以下4つの論理コンポーネントで構成する。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| イベントリスナー | AuditableActionOccurredEventの購読、AuditLogEntryへの変換・永続化、例外の自己完結捕捉(BR1.2) | リポジトリ |
| RESTコントローラ | 参照(GET)・エクスポート(GET)・削除(DELETE)・設定変更(PUT)の受付、isAdmin認可検証、入力バリデーション | サービス |
| サービス | 絞り込み条件(actionType・actorAccountId・occurredAt範囲)とtargetDescription部分一致(Search欄)を組み合わせた検索条件の組み立て(BR2.1・BR2.2)、CSV/JSON形式のエクスポートファイル生成(BR3.1)、`olderThanDays`から削除基準日時(現在日時-olderThanDays日)を算出したうえでの一括削除の実行(BR4.1)、retentionDays設定の更新(BR4.2) | リポジトリ |
| リポジトリ | AuditLogEntry・AuditLogSettingsの内部H2への永続化(Spring Data JPA) | 内部H2 |

## 障害ドメインとブラストラディウス

本Unitの障害ドメインはaudit-log自身に閉じる。イベントリスナーの例外処理(BR1.2)により、記録失敗が発行元Unit(config-management・dynamic-data-access・auth・account-management)の主処理に伝播することはない。したがって、audit-log自身の障害(内部H2への書き込み失敗等)のブラストラディウスは、監査ログ機能自体(参照・エクスポート・削除・設定変更画面)に限定され、業務機能(データ登録・設定変更等)には影響しない。

## 共有リソース

内部H2データストアは他Unit(config-management、permission等)と共有するが、テーブル自体(AuditLogEntry、AuditLogSettings)は本Unit専有であり、他Unitとのテーブルレベルの競合はない。
