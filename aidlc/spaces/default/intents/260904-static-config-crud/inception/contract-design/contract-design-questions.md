# Contract Design Questions: MasterSmith MVP

units-generation/unit-of-work-dependency.mdに列挙された全境界(バックエンド間のプロセス内呼び出し10件、notificationのイベント購読2件、frontend-core/frontend-adminからバックエンドへのREST呼び出し8件)を対象に、契約の形式・所有・エラー方針を確定するための質問。

## Q1. 外部公開APIの有無

MasterSmithは自宅サーバで動く1業務1インスタンスのアプリケーション(NFR2)であり、requirements.mdのOut of Scopeには外部IdP連携等のフル認証基盤も含まれていません。unit-of-work-dependency.mdに記載されたネットワーク境界は、frontend-core/frontend-admin(ブラウザで動くReact/TSX SPA)からバックエンドUnitへのREST呼び出しのみで、これはシステム内の境界(ブラウザ-サーバ間)です。パートナー企業や外部の別システムが直接呼び出すAPIは、現時点の要件からは見当たりません。

- A. 外部公開APIは存在しない。frontend-core/frontend-adminからバックエンドへのREST呼び出しのみを対象境界とする(推奨)
- B. 実は外部にも公開したいAPIがある(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q2. バックエンド間プロセス内呼び出し(sync)の契約の記述粒度

unit-of-work-dependency.mdに列挙された同期のプロセス内呼び出し(例: dynamic-data-access→config-management、config-management→schema-ingestion、dynamic-data-access→permission、account-management→auth)は、単一WARの同一JVM内で実行されるJavaメソッド呼び出しであり、コンパイラが型を保証します。Contract Designでこれらをどこまで具体的に定義するかを決めます。

- A. 各境界を「呼び出し元→呼び出し先、渡すデータ・返すデータの意味」という簡潔な記述に留め、具体的なJavaインタフェースのメソッドシグネチャ(引数・戻り値の型)確定はFunctional Design以降に委ねる(推奨: 同一JVM内でコンパイラが型を保証するため、この段階での前倒し確定は過剰)
- B. Contract Designの時点で各境界のJavaインタフェースの具体的なメソッドシグネチャまで確定する
- X. Other (please specify)

[Answer]: A

## Q3. 監査ログ呼び出し(記載上「async」)の実現方式

unit-of-work-dependency.mdは4件の呼び出し(config-management→audit-log、dynamic-data-access→audit-log、auth→audit-log、account-management→audit-log)を「プロセス内呼び出し(async)」と記載していますが、非同期の実現方式が未確定です。

- A. Springの`@Async`アノテーション付きメソッドとして実現する(専用スレッドプールで実行し、呼び出し元は監査ログの記録完了を待たない)
- B. Springの`ApplicationEventPublisher`/`@EventListener`によるイベント連携として実現する(U7 notificationのアカウント系イベント連携と同じ仕組みを監査ログにも使う)
- C. 実装は同期的な直接メソッド呼び出しで良く、「async」という表記は「監査ログの記録失敗が呼び出し元の主処理を止めない(例外を握りつぶすかログ記録のみに留める)」という意図を表すに留める
- X. Other (please specify)

[Answer]: B

## Q4. notification(U7)向けアカウントライフサイクルイベントの名前とペイロード

domain-design/components.mdの未解決事項に「AccountComponentとNotificationComponent間のイベント(イベント名・ペイロード)の具体的な定義は、Contract Design以降で確定する」と記載されています。U5(auth)・U6(account-management)がU7(notification)へ発行する、FR6.4の6種のメールフロー(アカウント作成通知・登録完了通知・情報変更通知・パスワード変更通知・パスワード忘れ対応・メールアドレス変更リクエスト)に対応するイベントを、今回確定するかを決めます。

- A. 今回、6種のイベントそれぞれの名前(例: `AccountCreatedEvent`)とペイロード項目(最低限: 宛先メールアドレス、対象アカウントID、メール本文に埋め込む固有情報 [トークン付きURL等])を確定する(推奨)
- B. イベント名・ペイロードの確定はFunctional Design以降に持ち越し、Contract Designでは「U5/U6がU7へSpringのアプリケーションイベントを発行する」という境界の存在と連携方式のみを記録する
- X. Other (please specify)

[Answer]: A

## Q5. アクセストークンの形式とisAdminクレームの伝播方式

units-generation/unit-of-work.mdおよびdomain-design/components.mdの両方に「Account.isAdminをアクセストークンのクレームとして伝播する具体的な実装(JWT等)は、Contract Design以降で確定する」という未解決の前提が記載されています。FR6.2(ステートレスなアクセストークン)・FR6.3(リフレッシュトークンによる自動延長)・FR5.5/FR5.6(isAdminクレームによる管理者ゲーティング)・FR5.4(複数ロール保有時の作業中ロール切替)を踏まえ、今回トークンの形式を確定します。

- A. JWT(署名付き、HS256)を採用し、標準クレームに加えて`isAdmin`(boolean)と、現在作業中のロールを表す`activeRoleId`のカスタムクレームを含める(推奨: ステートレスというFR6.2の要件と、FR5.4のロール切替機能の両方に整合する)
- B. JWTではなく独自形式の署名付きトークンを採用する
- X. Other (please specify)

[Answer]: A(ただしQ6で修正: `activeRoleId`はクレームとして持たない。代わりに割り当てられたロールID一覧を表す`roles`クレームを持ち、作業中ロールはQ6のヘッダで別途伝える)

## Q6. アクセストークン・リフレッシュトークンの有効期限とロール切替時の再発行

Q5で確定するトークンについて、FR6.3(リフレッシュトークンによる自動延長)とFR5.4(作業中ロールの切替)を実現するための具体的な有効期限方針と、ロール切替時の挙動を決めます。

- A. アクセストークンは短命(例: 15分)、リフレッシュトークンは長命(例: 7日)とし、アクセストークン期限切れ時はリフレッシュトークンで自動再発行する。ロール切替時は`activeRoleId`クレームを更新した新しいアクセストークンを即座に再発行する(推奨)
- B. 異なる有効期限方針を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: 基本的にA(短命アクセストークン/長命リフレッシュトークンによる自動再発行)を採用するが、ロール切替時の挙動を修正する: アクセストークンには割り当てられたロール一覧(`roles`クレーム)のみを含め、作業中ロールはトークンに含めない。作業中ロールは各リクエストの`X-Active-Role`ヘッダで伝える。バックエンドは毎リクエスト、指定されたroleIdがアクセストークンの`roles`クレームに含まれるかを検証し、含まれなければ403を返す。ロール切替時にトークンを再発行する必要はない。

## Q7. REST APIの記述形式と一覧系エンドポイントの共通規約

frontend-core/frontend-adminからバックエンドへのREST呼び出し(8件)について、Contract Designで作成するcontract-summary.mdにどの形式で仕様を記述するか、また一覧画面(FR3.1の検索フォーム+一覧テーブル+ページング+ソート)向けエンドポイントの共通的なクエリパラメータ規約を決めます。

- A. OpenAPI 3.xのYAMLブロックで記述する。ページング・ソート・検索条件は`page`/`size`/`sort`/テーブルごとの検索条件パラメータという共通クエリパラメータ規約に統一する(推奨)
- B. OpenAPI 3.x以外の形式を使う(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q8. REST APIのエラーレスポンス形式とHTTPステータスコードの割り当て

REST境界でのエラー・タイムアウト・リトライ挙動(Contract Designステージの必須項目)を決めます。特に、FK制約違反(FR3.3の編集画面での保存時)、権限不足(FR5.1/FR5.2のテーブル・カラム権限)、設定インポート時の不整合(FR2.3.1)といった、業務的なエラーケースの表現方法を確定します。

- A. RFC 7807(Problem Details for HTTP APIs)形式のJSONエラーボディ(`type`/`title`/`status`/`detail`/`errors`配列)を採用する。400=バリデーション/FK制約違反、401=未認証、403=権限不足、404=対象レコードなし、409=設定インポート不整合、500=予期しないエラーに割り当てる(推奨)
- B. 独自形式のエラーレスポンス(RFC 7807ではない)を採用する
- X. Other (please specify)

[Answer]: A

## Q9. REST境界での認証ヘッダーとリフレッシュトリガー

frontend-core/frontend-adminがQ5で確定したアクセストークンをどうバックエンドへ渡すか、および期限切れをどう検知してQ6のリフレッシュ処理を起動するかを決めます。

- A. `Authorization: Bearer <アクセストークン>`ヘッダーで送信する。リフレッシュは「401応答を受け取った時点でリフレッシュトークンを使い再試行する」というリアクティブ方式とする(推奨: 実装がシンプルで、有効期限を先読みする専用ロジックが不要)
- B. アクセストークンの有効期限が近づいたことをフロントエンドが事前検知し、能動的にリフレッシュするプロアクティブ方式とする
- X. Other (please specify)

[Answer]: A。加えて、作業中ロールは`X-Active-Role: <roleId>`ヘッダーで毎リクエスト送信する。バックエンドは指定されたroleIdがアクセストークンの`roles`クレームに含まれるかを毎回検証し、含まれなければ403を返す。

## Q10. 契約の所有権の原則

各境界の契約(REST仕様、イベント仕様、共有スキーマ仕様)を誰が「所有」する(仕様変更の最終決定権を持つ)かの原則を決めます。

- A. 各契約は提供側(呼び出される側)のUnitが所有する。例えばfrontend-admin→config-managementのREST契約はconfig-managementが所有し、破壊的変更はconfig-management側の変更として扱う(推奨: DDDのUpstream/Downstreamパターンに沿い、開発者一人体制でも「どちらが変更起点か」を明確にできる)
- B. 異なる所有権の原則を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q11. バージョニングと破壊的変更の方針

NFR4により、フロントエンド(React/TSX)とバックエンド(Java/Spring Boot)は別々に開発されつつ、ビルド時に単一の実行可能WARへパッケージングされます。両者は常に同じビルド・同じデプロイ単位で提供されるため、REST契約に明示的なAPIバージョニング(URLパスへの`/v1/`等)が必要かを決めます。

- A. 明示的なAPIバージョニングは行わない。フロントエンドとバックエンドは常に同一ビルド・同一デプロイ単位であり、破壊的変更はビルド時に両者が同期して更新されるため、バージョン間の後方互換性を維持する必要がない(推奨)
- B. 明示的なAPIバージョニング(URLパスへの`/v1/`等)を導入する
- X. Other (please specify)

[Answer]: A

## Q12. タイムアウト・リトライ方針

自宅サーバでの個人利用が中心(NFR1)という前提のもと、REST境界およびバックエンド間プロセス内呼び出しにサーキットブレーカー・リトライ等の耐障害性パターンを導入するかを決めます。

- A. 導入しない。単一インスタンス・個人利用という前提では、サーキットブレーカー・リトライによる複雑性追加は過剰であり、REST境界は標準的なHTTPクライアントのデフォルトタイムアウト、業務DBへの接続はJDBC標準のタイムアウト設定のみとする(推奨)
- B. 一部の境界にリトライ等の耐障害性パターンを導入する(具体的にどの境界か教えてください)
- X. Other (please specify)

[Answer]: A

## Q13. U5(auth)/U6(account-management)間の共有スキーマ契約の記述

units-generation/unit-of-work.mdは、U5(auth)とU6(account-management)がAccountエンティティの永続化スキーマを共有し、U6は直接テーブルアクセスせず必ずU5経由で呼び出すと定めています。この共有スキーマ契約をcontract-summary.mdでどう記述するかを決めます。

- A. `shared-schema`形式のフェンスドブロックで、Accountテーブルの列定義(domain-design/components.mdのAccountエンティティ属性: name, email, passwordHash, status, isAdmin)と、「U5がスキーマ・マイグレーションの所有者であり、U6はU5が公開するリポジトリ/サービスインタフェース経由でのみアクセスする」という所有権ルールを明記する(推奨)
- B. 異なる記述方法を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- 外部APIは存在せず、frontend-core/frontend-adminからバックエンドへのREST呼び出しのみが対象境界
- バックエンド間sync呼び出しは意味レベルの記述に留め、Javaインタフェース詳細はFunctional Design以降
- 監査ログ(async)はSpringのApplicationEventPublisher/@EventListenerによるイベント連携
- notificationの6種メールイベントの名前・ペイロードを今回確定する
- アクセストークンはJWT(HS256)。クレームはisAdmin+roles(割当ロール一覧)のみで、activeRoleIdは持たない
- アクセストークン短命/リフレッシュトークン長命の自動再発行。作業中ロールはX-Active-Roleヘッダで毎リクエスト伝達し、rolesクレームとの整合をバックエンドが毎回検証(トークン再発行不要)
- REST仕様はOpenAPI 3.x。一覧系はpage/size/sort+テーブル別検索条件パラメータの共通規約
- REST認証はAuthorization: Bearerヘッダ+401時のリアクティブなリフレッシュ、X-Active-Roleヘッダ併用
- エラー形式はRFC 7807 Problem Details。400/401/403/404/409/500を業務エラーに割り当て
- 契約所有権は提供側(呼び出される側)Unitが所有
- 明示的なAPIバージョニングは行わない(常に同一ビルド・同一デプロイ単位)
- サーキットブレーカー・リトライ等の耐障害性パターンは導入せず、標準タイムアウトのみ
- 共有スキーマ(Account)はshared-schemaブロックでU5所有・U6はU5経由アクセスの原則を明記

- Looks correct
- Request changes

[Answer]: Looks correct
