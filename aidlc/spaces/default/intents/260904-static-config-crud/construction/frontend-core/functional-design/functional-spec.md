# Functional Specification: frontend-core

frontend-coreは、domain-design/components.mdの定義どおり、管理者ロールを持たない利用者も含む全利用者が使う画面群(React/TSX、make-you-chic-ui)であり、永続エンティティ・業務ルールを自身では持たない(entities.md/rules.mdはUI Unitのため作成しない)。各画面は、対応するバックエンドUnitのREST契約を呼び出すクライアントとして振る舞う。画面のレイアウト・状態・アクセシビリティの詳細は、ideation/rough-mockups/wireframes.md(1〜6、7d)・inception/refined-mockups/mockups.md(1、9)・interaction-spec.md・design-system-mapping.mdを参照する(iteration 1レビューR-04フォロー: wireframes.mdの画面番号は1〜8までであり、画面9はmockups.md側の参照のみが正しい)。

## 管理者ゲーティングとの関係(横断的関心事、FR5.5・FR5.6)

frontend-core自身の画面は管理者ロールの有無を問わず全利用者が使用するため、frontend-core自身の画面・APIには管理者ゲーティングは適用されない。ワークフロー2手順2で述べるとおり、frontend-adminの各画面への導線(サイドナビ項目の表示・非表示)のみがisAdminクレームに基づいて出し分けられる。このゲーティングの実装・判定ロジックの詳細はfrontend-admin Unit Functional Designが定義する。

## ワークフロー

### 1. ログイン画面

1. Email/ID・Passwordを入力し、`POST /api/auth/login`(契約#11)を呼び出す。
2. 成功時、accessToken・refreshTokenを受け取り、トップ画面(2)へ遷移する。accessTokenのrolesクレームには、permission Unitが計算した有効なロール集合(直接割当+グループ経由割当の和集合)が含まれる(契約#20)。isAdminクレームも同時に含まれる。
3. 認証失敗時(401)は「ログインIDまたはパスワードが正しくありません」を表示する。
4. ログイン試行回数超過によるロック中(403)は、LoginLockoutNoticeコンポーネントで「ログイン試行回数が上限に達しました。しばらく時間をおいて再度お試しください」を表示する。具体的な残り時間(秒数・カウントダウン)は表示しない(auth BR4.1〜BR4.3)。
5. 「Forgot your password?」リンクから、ワークフロー7a(パスワード忘れ申請)へ遷移する。
6. トークンのリフレッシュ(FR6.2・FR6.3、iteration 1レビューR-02フォロー): 以降のいずれかのワークフローでAPI呼び出しが401(アクセストークン期限切れ)を返した場合、クライアントは失敗したリクエストを保留したまま`POST /api/auth/refresh`(契約#11)を自動的に呼び出す。成功すれば新しいaccessTokenで元のリクエストを1回だけ再試行する。リフレッシュ自体が401(リフレッシュトークン無効・期限切れ)を返した場合は、保持しているaccessToken・refreshTokenを破棄し、ログイン画面(1)へ強制的に遷移する。複数のリクエストが同時に401を検知した場合でも、リフレッシュ呼び出しは1回にまとめる(多重リフレッシュによるリフレッシュトークンローテーションの競合を避けるため)。

### 2. トップ画面(テーブル・メニューのカード一覧)

1. ログイン後、`GET /api/menu`(契約#23、X-Active-Roleヘッダー必須)を呼び出し、呼び出しロールがcanList権限を持つテーブルのみを含むメニュー階層を取得する(config-management BR: メニュー取得は#15の管理者専用CRUDとは別の非管理者向けエンドポイントであり、permission契約#22で取得したcanList権限によりフィルタ済みである。frontend-adminのメニュー管理画面(14)がisAdminによる全件アクセスであるのとは対照的)。テーブルノードをCard一覧として表示する。各Cardを選択すると、そのテーブルの一覧画面(3)へ遷移する。フォルダ/グループノードは、配下に1件も可視なテーブルノードがない場合はサーバ側で除外済みのため、空のフォルダが表示されることはない。
2. isAdminクレームを持つ利用者に対してのみ、サイドナビに設定管理・スキーマ取り込み・監査ログ・アカウント管理・メニュー管理(frontend-adminの各画面)への導線を表示する(FR5.5・FR5.6、frontend-admin Unit Functional Design「管理者ゲーティング」参照)。isAdminクレームを持たない利用者にはこれらの項目自体が表示されない。
3. Topbarのユーザーメニューに、複数ロール保有時のみ「現在のロール」セレクトを表示する(ワークフロー8参照)。
4. このインスタンスにテーブル・メニュー設定が1件もない場合は、「まだテーブルが設定されていません」+設定投入を促す案内を表示する。
5. `GET /api/menu`が403(X-Active-Roleがrolesクレームに含まれない)または通信エラーで失敗した場合は、エラーメッセージ+再試行ボタンを表示する(iteration 1レビューR-06フォロー)。

### 3. 一覧画面(検索+一覧表示)

1. 画面表示時、`GET /api/data/{tableId}`(契約#12、X-Active-Roleヘッダー必須)をpage/size/sortとともに呼び出す。列単位のメタデータ(isSearchable・searchOperator・listOrder・accessLevel・isReadOnly・バリデーション設定・referencedTableId等、config-managementのTableConfig.columns[]由来)は、この一覧取得レスポンス自身に埋め込まれて返る(dynamic-data-access契約#2でconfig-managementから取得したTableConfigをそのままレスポンスの一部として整形する設計。専用のメタデータ取得エンドポイントは別途存在しない。iteration 1レビューR-03フォロー、詳細画面・編集画面・FK参照ポップアップ検索も同様にそれぞれの取得レスポンスへ埋め込まれる)。
2. 検索欄・フィルタ(カラムごとにTableConfig.columns[].isSearchable/searchOperatorで設定された検索条件)の指定に応じて再取得する(dynamic-data-access BR1.1。呼び出しロールについてaccessLevel=非表示のカラムは検索条件として送信しても無視される)。
3. 一覧結果は、TableConfig.columns[].listOrderに基づく表示列・カラム単位accessLevel=非表示の除外が適用済みの状態で返る(dynamic-data-access BR1.2・BR4.2)。FK列は代表表示列の値で表示する(dynamic-data-access BR1.3)。
4. 主キーを持たないテーブル・ビューの場合、「+ New」ボタンを表示せず、各行も詳細画面(4)への遷移(閲覧)のみとし、編集・削除の導線を出さない(dynamic-data-access BR3.1、requirements.md FR1.3・FR1.4)。
5. 一覧取得に失敗した場合、エラーメッセージ+再試行ボタンを表示する。テーブル単位のlist権限がない場合は403(dynamic-data-access BR4.1)。

### 4. 詳細画面

1. 一覧画面の行選択、または詳細画面へのURLから、`GET /api/data/{tableId}/{recordId}`(契約#12)を呼び出す。recordIdは一覧画面で受け取った不透明な文字列をそのまま使い、クライアント側でデコード・解釈は行わない(dynamic-data-access BR2.1、recordIdはBase64エンコードされたJSONオブジェクトだが、その内部構造はサーバ側の実装詳細である)。
2. 全カラム(表示可能なもの、カラム単位accessLevel=非表示のものは除外済み)を表示する。編集対象外(isReadOnly=true)の項目も表示のみで含める(列メタデータはこの詳細取得レスポンス自身に埋め込まれる、ワークフロー3手順1参照)。FK列は代表表示名で表示する。
3. 該当レコードが見つからない場合(404)は、一覧画面へのリンク付きエラーを表示する。閲覧権限がない場合(403)は「この操作を行う権限がありません」を表示する。
4. 主キーを持つテーブルの場合のみ「Edit」ボタンを表示し、編集画面(5)へ遷移する。

### 5. 編集(新規/更新)画面

1. 新規作成の場合、`POST /api/data/{tableId}`(契約#12)を呼び出す。カラム単位accessLevel=更新可の項目のみ入力欄を表示する(dynamic-data-access BR4.2、表示のみ・非表示の項目は入力対象外。列メタデータの出所はワークフロー3手順1と同様)。
2. 更新の場合、`PUT /api/data/{tableId}/{recordId}`を呼び出す。同一レコードへの同時編集はサーバ側で競合制御されず、後から保存された内容で上書きされる(後勝ち、dynamic-data-access BR3.3、FR3.5)。
3. TableConfig.columns[]のバリデーション設定(required・maxLength・minValue・maxValue・pattern・unique)に基づく入力検証エラー(400)は、各フィールド直下に具体的なメッセージを表示する(dynamic-data-access BR3.2)。バリデーション表示・アクセシビリティの詳細はmake-you-chic-uiの標準的な挙動に委ねる(FR3.6)。
4. FK入力項目には、テキスト入力欄の右側に検索アイコンボタンを配置する。クリックするとワークフロー6(FK参照ポップアップ検索)が開く。主キーの直接入力も引き続き可能(FR4.2)。
5. 外部キー制約違反(400、dynamic-data-access BR3.4)は、フォーム上部にエラーメッセージを表示する。
6. 保存成功後、詳細画面または一覧画面に遷移し完了メッセージを表示する。

### 6. FK参照ポップアップ検索(FkReferencePicker)

1. 編集画面(5)のFK入力項目の検索アイコンから、モーダルダイアログ(`role="dialog"` + `aria-modal="true"`)を開く。参照先が未解決(referencedTableId=null、詳細画面・編集画面取得時のTableConfigメタデータで判明済み)の場合は、この検索アイコン自体をクライアント側の判定のみで表示しない(API呼び出しは発生しない。iteration 1レビューR-05フォロー: 契約#12のfk-searchエンドポイントは404レスポンスを定義していないため、「404が返る」という前提は置かない)。
2. `GET /api/data/{tableId}/fk-search/{columnName}`(契約#12)を、自由テキスト検索・カラムごとの絞り込み条件とともに呼び出す。絞り込み条件のうち、参照先テーブルでaccessLevel=非表示と判定されるカラムは送信しても無視される(dynamic-data-access BR5.1)。
3. 検索結果は代表表示列の値と主キー(recordId生成用)の組で返る。参照先テーブルでaccessLevel=非表示のカラムは、代表表示列自体が非表示の場合を含め、応答側でも除外・代替されている(dynamic-data-access BR5.1、R-06フォロー)。
4. 一覧から行を選択すると、その値がFK入力欄に設定されモーダルが閉じる(選択自体が確定操作であり、明示的なOKボタンは不要)。Cancelボタンでも閉じられる。Escapeで閉じて編集画面へフォーカスを戻す。
5. 検索自体が失敗した場合は、エラーメッセージ+再試行ボタンを表示する。

### 7. アカウント自己サービス画面群

#### 7a. パスワード忘れ申請

1. メールアドレス入力欄に入力し、`POST /api/auth/forgot-password`(契約#11)を呼び出す。
2. 202(Accepted)が返れば、対象アドレス宛にAccountActionToken付きのメールが送信されたかどうかに関わらず「メールを確認してください」という完了メッセージを表示する(auth BR6.2、アカウント存在有無を応答から区別しない)。

### 7b. パスワード再設定(メール内URL経由)

1. メール内のURL(AccountActionTokenを含む)からアクセスすると、新しいパスワード入力欄(確認用含む)を表示する。
2. `POST /api/auth/reset-password`(契約#11)を呼び出す。トークンが無効・期限切れ(24時間、application.yml設定値)、または単回使用済みの場合は400(RFC 7807)を表示する(auth BR6.1〜BR6.2)。

### 7c. アカウント登録完了(管理者による作成後のメール内URL経由)

1. メール内のURLからアクセスすると、氏名・パスワード設定欄(確認用含む)を表示する。
2. `POST /api/auth/complete-registration`(契約#11)を呼び出す。成功するとcomplete-registrationを経由せず即座にログイン可能な状態になる。トークン無効・期限切れは400。

### 7d. 自己サービスでの氏名・パスワード変更

1. 氏名・パスワード・メールアドレスの変更フォーム(それぞれ独立したセクション)を表示する。
2. 氏名・パスワード変更は`PUT /api/me/profile`(契約#11)を呼び出す。氏名の変更はnameが空文字でないことが検証される(auth BR6.5)。パスワード変更時の現在パスワード再照合、および氏名・パスワードを同時に変更した場合の通知イベント発行仕様(名称変更・パスワード変更それぞれの通知が独立して発行されるか)は、auth Unit側の既知の繰延べ事項(functional-designステージ終了ゲートで対応予定)であり、本Unit側の画面設計はこれらの挙動がauth契約#11の範囲内で解決されることを前提とする(フォーム自体は氏名・パスワードを1つの保存操作で送信する構成のままとし、auth側の検証結果に応じたエラー表示のみを行う)。
3. メールアドレス変更は、独立したセクションから`POST /api/me/email-change-request`(契約#11)を呼び出す(ワークフロー7eへ続く)。

### 7e. メールアドレス変更確認(新アドレス宛メール内URL経由)

1. 7dで新アドレス宛に送信された確認メールのURLからアクセスすると、現在のパスワード入力欄を表示する。
2. `POST /api/me/email-change-confirm`(契約#11)を呼び出す。トークン無効・パスワード不一致の場合は400を表示する(auth BR6.4)。
3. 成功すると変更完了を表示する。

### 8. 複数ロール保有時のロール切替(Topbarのユーザーメニュー内)

1. ログイン時、`GET /api/me/roles`(契約#13)で自身が切替可能なロール一覧(直接割当+グループ経由割当の和集合、permission BR4.1)を取得する。
2. ロールが2件以上ある場合のみ、Topbarのユーザーメニューに「現在のロール」セレクト(`aria-label="現在のロール"`)を表示する。ロールが1件のみの利用者にはこの項目自体を表示しない。
3. ロール切替自体はクライアント側で完結する(サーバー呼び出しを伴わない、permission Unit Functional Design方針)。選択したroleIdを、以後のdynamic-data-access(契約#12)へのリクエストで`X-Active-Role`ヘッダーとして送る。
4. `GET /api/me/roles`の取得に失敗した場合は、ロールが1件のみとして扱い(ロール切替UIを表示しない)、トップ画面自体の表示は妨げない(iteration 1レビューR-06フォロー)。

### 9. ログアウト

1. Topbarのユーザーメニューから「ログアウト」を選択すると、`POST /api/auth/logout`(契約#11)を呼び出す。
2. クライアント側で保持しているaccessToken・refreshTokenを破棄し、ログイン画面(1)へ遷移する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T04:04:21Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-07 | Major | functional-spec.md > ワークフロー1手順6(トークンのリフレッシュ) | 手順6は`POST /api/auth/refresh`成功時に「新しいaccessTokenで元のリクエストを1回だけ再試行する」とのみ記述し、クライアント側で保持しているrefreshTokenの更新に触れていない。しかしauth Unit rules.md BR3.2・BR3.3(リフレッシュトークンローテーション)は、リフレッシュ成功のたびに新しいrefreshTokenを発行し、検証に使った古いRefreshTokenを`revoked=true`にすると規定している。frontend-coreの記述どおり(新accessTokenのみ差し替え、旧refreshTokenを保持し続ける)に実装すると、次回のアクセストークン期限切れ時に古い(既にrevoked済みの)refreshTokenで`POST /api/auth/refresh`を呼ぶことになり、BR3.1により401が返る。その結果、手順6自身が定義する「リフレッシュ失敗時はログイン画面へ強制遷移」が働き、本来は7日間(リフレッシュトークンの有効期間)有効なはずのセッションが、2回目のアクセストークン更新(通常運用では30分程度)で強制ログアウトになる。FR6.3が要求する「リフレッシュトークンによる自動延長」という設計意図と矛盾する挙動になる。 | ワークフロー1手順6に、`POST /api/auth/refresh`のレスポンスに含まれる新しいrefreshTokenで、クライアント側の保持値を必ず置き換える(ローテーション追従)旨を明記する。あわせて、contract-summary.md #11の`/api/auth/refresh` 200レスポンスの記述(「新しいaccessTokenを返す」のみ)がBR3.2の実際の発行内容(新accessToken+新refreshToken)と食い違っている点は、auth Unit側で契約記述の更新を検討する必要がある。 | New |

### Summary

iteration 1で指摘されたMajor 3件・Minor 3件はいずれも実際に修正が反映されており、修正内容と上流要件・契約(FR3.1〜FR3.6、FR4.1〜FR4.2、FR5.4、FR6.1〜FR6.3、FR6.6、契約#11・#12・#13・#20・#22・#23)・dynamic-data-access/auth/permissionの各rules.mdとの間に矛盾は見つからなかった。traceability.jsonのcoverageも要件文言と整合しており、frontend-components.mdの内容もinteraction-spec.md・design-system-mapping.mdの記述と一致している。dynamic-data-access側のBR1.2(sort識別子検証)追加についても、frontend-core側は「sortは常に受け入れられる」という誤った前提を持ち込んでいない。今回新たに、トークンリフレッシュ時のリフレッシュトークンローテーション追従漏れ(R-07、Major)を検出したが、Major 1件のみであり致命的な設計破綻ではないため、READYと判定する。次のiterationでR-07を反映することが望ましい。
