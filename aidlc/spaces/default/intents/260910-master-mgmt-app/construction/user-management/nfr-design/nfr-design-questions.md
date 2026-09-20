# NFR Design Questions — user-management (U4)

`construction/user-management/nfr-requirements/`(performance・security・scalability・reliability・observability・tech-stack-decisions)、`functional-design/functional-spec.md`、`inception/contract-design/contract-summary.md`(C5・C11)に基づき、user-managementの非機能設計のうち、まだ決まっておらず判断が分かれる点だけを問う。

NFR Requirementsで確定済みの事項(Argon2idの初期パラメータと同時実行の制御、SMTPの同期送信と10秒のタイムアウト、招待の同時実行数の上限と正規化後email単位の直列化、イベントをコミット後にfire-and-forgetで発行する方針、招待トークンのURL露出の抑止対象、メトリクスとアラートの暫定値など)は、そのまま設計に落とし込み、ここでは再確認しない。

## Q1: 招待メールのリンクにおける招待トークンの置き場所

招待メールのリンクは、受け取った人がクリックして、招待受諾の画面を開くためのものです。このリンクのどこに招待トークンを入れるかで、トークンがどこに残るかが変わります。NFR Requirements(NFR2.10)では、ログ・トレース・Refererなどからトークンの実際の値が漏れないようにすることが求められています。なお、招待受諾API自体(`POST /api/users/invitations/{token}/accept`)は、C5の契約でトークンをパスに含んでおり、変更しません。

- A. リンクのフラグメント(`#`より後ろ)にトークンを入れる(例: `https://.../invitations/accept#token=...`)。フラグメントはブラウザがサーバーへ送らないため、サーバーのアクセスログやリバースプロキシのログ、Refererに、リンクを開いた時点ではトークンが残らない。受諾画面のフロントエンドがフラグメントから読み取って、受諾APIに渡す
- B. リンクのパスにトークンを入れる(例: `https://.../invitations/{token}`)。実装は単純だが、受諾画面を開くリクエストのURLにトークンが含まれ、サーバーのアクセスログ・プロキシのログに残りうるため、NFR2.10のマスク・`Referrer-Policy: no-referrer`の対応をすべての経路で確実に行う必要がある
- X. Other (please specify)

[Answer]: A. リンクのフラグメント(`#`より後ろ)にトークンを入れる(例: `https://.../invitations/accept#token=...`)。フラグメントはブラウザがサーバーへ送らないため、サーバーのアクセスログやリバースプロキシのログ、Refererに、リンクを開いた時点ではトークンが残らない。受諾画面のフロントエンドがフラグメントから読み取って、受諾APIに渡す

## Q2: 招待メール送信時のSMTP接続の暗号化

招待メールは、メールサーバー(SMTP)に接続して送信します。NFR Requirementsでは「TLS(暗号化接続)の要否は環境ごとの設定で切り替える。開発のMailpitでは不要、本番相当の環境では必須」という仮の方針を置いていました。その具体的な設計を決めます。

- A. 暗号化の方式(STARTTLSまたはSMTPS)を設定で選べるようにし、本番相当の環境では暗号化と認証を必須とする。暗号化が必須の環境で、暗号化なしの接続しかできない設定は、起動時に検知して失敗させる(fail fast)。開発のMailpit環境では暗号化なしを許可する
- B. 暗号化の要否は、環境ごとの設定(ホスト・ポート・暗号化の有無)に任せ、アプリケーションでは特別なチェックを行わない
- X. Other (please specify)

[Answer]: B. 暗号化の要否は、環境ごとの設定(ホスト・ポート・暗号化の有無)に任せ、アプリケーションでは特別なチェックを行わない

## Q3: パスワードハッシュのパラメータを強くした後の、既存ハッシュの扱い

Argon2idのパラメータ(メモリ・反復回数など)は、`application.yml`で変更できます(NFR1.2)。将来パラメータを強くした場合、それまでに保存されたパスワードのハッシュは、古いパラメータのまま残ります(ハッシュには、計算に使ったパラメータが含まれるため、古いハッシュでも検証はできます)。

- A. ログインに成功した時点で、保存済みのハッシュのパラメータが現在の設定より古ければ、入力されたパスワードで新しいパラメータのハッシュに更新する(利用者は何もしなくてよい)。ただし、ログイン(認証サービスがC11の`verifyPasswordHash`を呼ぶ)の処理の中で、ハッシュを書き換える処理が加わる
- B. 既存のハッシュは更新せず、新しいパラメータは、新たにパスワードを設定するとき(招待受諾・初期管理者の作成)だけに適用する。実装は単純だが、パラメータを強くしても、古いハッシュは古い強さのまま残り続ける
- X. Other (please specify)

[Answer]: A. ログインに成功した時点で、保存済みのハッシュのパラメータが現在の設定より古ければ、入力されたパスワードで新しいパラメータのハッシュに更新する(利用者は何もしなくてよい)。ただし、ログイン(認証サービスがC11の`verifyPasswordHash`を呼ぶ)の処理の中で、ハッシュを書き換える処理が加わる

## Consolidated Summary Confirmation

以下の内容で成果物(performance-design.md・security-design.md・scalability-design.md・reliability-design.md・observability-design.md・logical-components.md・traceability.json)を生成します。

- **招待リンクのトークン位置**: リンクのフラグメント(`#token=...`)にトークンを入れる。フラグメントはサーバーへ送られないため、リンクを開いた時点ではアクセスログ・Refererに残らない。受諾画面のフロントエンドがフラグメントから読み取って受諾API(パスにトークンを含む契約のまま)に渡す。受諾API呼び出し時のログ・トレース・メトリクスからの除外は、NFR2.10のとおり別途行う(Q1)
- **SMTP接続の暗号化**: 暗号化の要否は環境ごとの設定に任せ、アプリケーションでは特別なチェックを行わない。NFR Requirementsで置いた「本番相当の環境では暗号化を必須とする」は、アプリケーションが強制するのではなく、環境側(運用)の責任とする。暗号化なしの誤設定で、招待メール(招待リンク)が平文で送られる恐れは、残余リスクとして記録する(Q2)
- **パスワードハッシュの更新**: ログインに成功した時点で、保存済みのハッシュのパラメータが現在の設定より古ければ、入力されたパスワードで新しいパラメータのハッシュに更新する。C11の`verifyPasswordHash`の中で行う(利用者の操作は不要)(Q3)
- **NFR Requirementsで確定済みの設計をそのまま落とし込む項目**: Argon2id(Spring Securityの`Argon2PasswordEncoder`)と同時ハッシュ計算の制御(既定はCPUコア数、待機2秒で503)、招待メール送信の同期処理(SMTPタイムアウト10秒、同時5件まで)と正規化後email単位の直列化、UserChangedEventのコミット後の発行(他ユニットと同じ、同期`@EventListener`とtry-catch境界によるfire-and-forget)、招待トークンのURL露出の抑止、メトリクス(カウンタ・ヒストグラム)とアラートの暫定値

- Looks correct
- Request changes

[Answer]: Looks correct
