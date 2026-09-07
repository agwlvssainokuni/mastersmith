# Logical Components: auth

## コンポーネント構成

authは単一のSpring Bootモジュール(パッケージ)内に、以下3つの論理コンポーネントで構成する。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| RESTコントローラ | ログイン・リフレッシュ・ログアウト(`/api/auth/*`)、自己サービス系(`/api/me/*`)エンドポイントの受付、入力バリデーション | サービス |
| サービス | Argon2ハッシュ照合・生成(BR1.1、BR5.1)、JWTアクセストークン発行(BR2.2)、リフレッシュトークン発行・ローテーション(BR2.3、BR3.2)、ログイン試行回数・ロック判定(BR4.1〜BR4.3)、AccountActionToken発行・検証(BR6.1〜BR6.2)、permission Unitへの有効ロール集合取得のプロセス内呼び出し(契約#20、BR2.1) | リポジトリ |
| リポジトリ | Account・RefreshToken・AccountActionTokenの内部H2への永続化(Spring Data JPA) | 内部H2 |

## 障害ドメインとブラストラディウス

auth自身の障害(内部H2への読み書き失敗、Argon2処理の異常等)は認証機能全体(ログイン・トークンリフレッシュ・自己サービス操作)を停止させる。auth自身は他Unitの主処理を呼び出さないため、auth側の障害が他Unitの機能(業務データ操作等)を直接停止させることはないが、多くのUnitがisAdmin/rolesクレームを含むアクセストークンに依存するため、実質的な影響範囲は広い(新規ログイン・トークン更新ができなくなる)。サービスコンポーネントがpermission Unitへ行うプロセス内呼び出し(契約#20)が失敗した場合は、ログイン処理自体が失敗する(reliability-design.md NFR-FAILSAFE.2参照)。

## 共有リソース

内部H2データストアは他Unit(audit-log、permission等)と共有するが、テーブル自体(Account、RefreshToken、AccountActionToken)は本Unit専有であり、他Unitとのテーブルレベルの競合はない。
