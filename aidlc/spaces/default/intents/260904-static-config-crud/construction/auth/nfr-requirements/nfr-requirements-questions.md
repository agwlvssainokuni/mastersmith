# NFR Requirements Questions: auth

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、auth固有に新たな数値目標は追加しない。functional-design(rules.md BR1.1〜BR8.1)で既に確定済みの内容(Argon2ハッシュ化、JWTアクセストークン15分・リフレッシュトークン7日、ログイン試行回数制限、自己サービストークン管理)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

auth Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし、著しい遅延の兆候があれば別途検証)を踏襲。ログイン処理はArgon2ハッシュ照合(意図的に計算コストが高い、NFR7)を含むため、他の操作より応答が遅くなること自体は許容する(セキュリティとのトレードオフとして明記)。

**security-requirements.md**: 本Unitの中核はセキュリティ機能そのものである。パスワードのArgon2ハッシュ化(BR5.1、NFR7)、JWTアクセストークン(15分、HS256署名)・リフレッシュトークン(7日、ハッシュ化永続化・ローテーション、BR2.2〜BR3.2)、ログイン試行回数制限(BR4.1〜BR4.3、既定n=5回・m=300秒)、自己サービストークン(単回使用・有効期限24時間、BR6.1〜BR6.2)を、認証・認可の要件として具体化する。

**scalability-requirements.md**: NFR2(1インスタンス=1業務)を踏襲。Account・RefreshToken等のレコード数は登録利用者数に比例するが、想定運用規模では問題にならない。

**reliability-requirements.md**: 自宅サーバ1台構成のためSLA/SLO数値目標は設けない。ログイン失敗時のロック機構(BR4.1〜BR4.2)は可用性とのトレードオフ(正規利用者も一時的にロックされうる)であることを明記する。

**observability-requirements.md**: NFR3を踏襲。ログイン成功・失敗・ロック等の監査ログイベント(BR8.1)は既にfunctional-designで確定済みであり、これが本Unitの主要な可観測性要件を兼ねる。パスワード・トークン等の機微情報はログに出力しない。

**tech-stack-decisions.md**: Argon2PasswordEncoder(Spring Security)、JWT(HS256署名)をauth固有の技術選定として記載する。

**traceability.json**: upstream_ids = NFR1, NFR2, NFR3, NFR7(パスワードハッシュ化、本Unitが直接の担当)。NFR4〜NFR6・NFR8はN/A(横断方針または他Unit担当)、NFR9(初期管理者アカウント自動作成)は本Unit側での未実装が既にfunctional-designステージ終了ゲートで繰延べ事項として記録済みであることを明記する(Deferred)。

[Answer]: Looks correct
