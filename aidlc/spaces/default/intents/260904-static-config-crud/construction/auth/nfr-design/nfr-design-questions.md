# NFR Design Questions: auth

軽量版方針で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

auth Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: NFR1.1・NFR1.2を踏襲。Argon2パラメータ(メモリ・反復回数)はSpring SecurityのArgon2PasswordEncoderのデフォルト値を用い、意図的な計算コストをそのまま許容する(チューニングはcode-generation段階)。

**security-design.md**: パスワードはArgon2PasswordEncoderでハッシュ化(NFR7.1)。JWTアクセストークンはHS256署名、有効期限15分、署名鍵は環境変数経由で外部化(NFR-AUTHN.1)。リフレッシュトークンはランダム値のハッシュのみ永続化、ローテーション実施(NFR-AUTHN.2)。ログイン試行制限はAccount.consecutiveFailureCount+lockedUntilで実装(NFR-AUTHN.3)。AccountActionTokenも同様にハッシュではなく実トークン値をURLに埋め込む方式のまま(NFR-AUTHN.4、DBには実トークン値相当を保持、functional-design時点の既存設計を踏襲)。isAdminクレームは発行のみ、判定は各消費Unit(NFR-AUTHZ.1)。

**scalability-design.md**: 単一インスタンス構成(NFR2.1)。リフレッシュトークンローテーションによるRefreshTokenレコード増加(NFR2.2)は、失効済みレコードの物理削除を行わない設計のまま(将来的なバッチ削除は本ステージのスコープ外)。

**reliability-design.md**: ログインロック(NFR-FAILSAFE.1)は可用性とのトレードオフとして明示的に許容。permission呼び出し失敗時(NFR-FAILSAFE.2)は例外を握りつぶさず5xxとして伝播させる。

**observability-design.md**: ログイン・自己サービス操作の監査イベント発行(NFR3.1、LOGIN_SUCCESS/LOGIN_FAILED/LOGIN_LOCKED/SELF_SERVICE_PROFILE_CHANGED)。機微情報(パスワード・トークン実値)はログに出力しない(NFR3.2)。

**logical-components.md**: authは以下3つの論理コンポーネントで構成する。RESTコントローラ(ログイン・リフレッシュ・ログアウト・自己サービス系エンドポインドの受付)、サービス(Argon2ハッシュ照合・JWT発行・リフレッシュトークンローテーション・ログインロック判定・AccountActionToken発行検証・permission Unitへのロール取得のプロセス内呼び出し〈契約#20〉)、リポジトリ(Account・RefreshToken・AccountActionTokenの内部H2永続化)。

**traceability.json**: nfr-requirementsで確定した各NFRx.y項目を上記の設計解へマッピングする。NFR9(初期管理者アカウント自動作成)は、nfr-requirements時点から継続してDeferred(未実装、functional-designステージ終了ゲートで既に記録済み)として扱う。

[Answer]: Looks correct
