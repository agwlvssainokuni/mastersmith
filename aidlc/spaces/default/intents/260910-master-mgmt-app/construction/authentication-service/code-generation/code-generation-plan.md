# Code Generation Plan — authentication-service (U5)

authentication-serviceは、アクセストークン(JWT)とリフレッシュトークンによるトークンベース認証(FR3.1)、複数端末の同時ログイン(FR3.2)、連続ログイン失敗によるアカウントの一時ロック(FR2.7)、Sessionごとのアクティブロールの保持(FR4.2)を担う。本Boltで、認証REST API(C4: `POST /api/auth/login`・`/refresh`・`/logout`、`PUT /api/auth/active-role`)、`/api/**`全体に掛かる認証フィルタとセキュリティヘッダー、C14(`SessionContextApi.getActiveRoleId`)、中立の共有契約C15(`Operator`・`OperatorContext`)、Sessionの定期削除を実装する。あわせて、機能設計の追補8・11に従い、他ユニット(U2・U3・U4・U6・U7)の暫定の操作者取得(ヘッダー方式)を、C15を読む実装へ置き換える。

ユーザー情報(パスワードの検証・status・選択可能なロール)はuser-managementのC11から得て、ロールの実在と権限の判定はpermission-engineへ委譲する。本ユニットは、アクティブロールのID(不透明な識別子)を運ぶだけで、権限判定のロジックを持たない。

## 前提事項(Plan Approval時に確認いただきたい設計判断)

1. **契約追補・機能設計の追補を、最初の作業(Step 1)として反映する**(機能設計の追補一覧の9番・12番の実施。Contract Designの再実施はしない)。`inception/contract-design/contract-summary.md`へ、C4(リフレッシュ・ログインのレスポンス、503・413・400、ProblemDetailsの`code`)、C10(`activeRoleId`のnull・空の扱い)、C11(`findByUserId`・`dummyVerify`の追加、`revokeRefreshTokensOnDisable`の削除)、C14(未選択のnull、`SessionExpiredException`)の追補と、**新しい共有契約C15**(契約表と、共通基盤の契約の所有規則の例外)を追記する。`inception/units-generation/unit-of-work-dependency.md`の統合ポイント表へ、C15の追補を記録する。C4の「FR2.7の文言の不一致は未解消」の記述は、「要件定義書の追補で解消済み」に更新する。既存の記述は書き換えず、追補として追記する(user-managementと同じ流儀)。
2. **認証の基盤のライブラリ(`tech-stack-decisions.md`の保留3番)は、`spring-boot-starter-security`のフィルタチェーンと、Nimbus JOSE + JWT(`com.nimbusds:nimbus-jose-jwt`)の直接利用とする**。OAuth2 Resource Serverは用いない。理由: (a)署名方式をHS256だけに限定し、時計のずれの許容を0にし、`sub`とSessionの`userId`を照合し、すべての失敗を同一の401にする、という要件を、自前の認証フィルタのほうが確実に制御できる。(b)認証の成否がSession(キャッシュ経由のDB)の状態に依存するため、JWTの検証だけでは完結しない。`SecurityFilterChain`(認証の要否の規則・セキュリティヘッダー・ステートレス・CSRF無効・CORSなし、Q2=A)は、Spring Securityで実装する。Nimbusの座標・バージョンは、Spring Boot BOMでの管理の有無を実物で確認して固定する。
   - **前提事項2の補足(既存テストへの影響)**: Spring Securityの導入は、既存の`@WebMvcTest`・`@SpringBootTest`に影響する(既定で全リクエストが認証必須になる)。既存テストは、アサーション(期待する振る舞い)を変えずに、「リクエストヘッダー(`X-User-Id`・`X-Active-Role-Id`)で操作者を渡す」形から、「`OperatorContext`を差し替える(テスト用の操作者の供給)」形へ更新する。共通のテスト支援(`com.mastersmith.common.security`のテスト用クラス)を、Step 8で用意する。既存のテストを削除・緩和して通すことはしない。
3. **パッケージ**: 本体は`com.mastersmith.auth`(`entity`・`repository`・`dto`・`service`・`token`・`security`・`cache`・`web`・`config`・`exception`・`observation`)。C14の`SessionContextApi`は`com.mastersmith.auth`直下に置く(C11の`UserAccountLookupApi`と同じ流儀)。**C15(`Operator`・`OperatorContext`)は、共通基盤の契約(shared kernel)として`com.mastersmith.common.security`に置く**(`logical-components.md`保留10番の確定)。U2・U4・U6・U7・U3からの依存は、このパッケージへの読み取りだけとし、`com.mastersmith.auth`へは依存しない。
4. **設定キーは`mastersmith.auth.*`とする**(user-managementの`mastersmith.users.*`と揃える。NFR Designの`auth.*`は、この`mastersmith.`配下の相対名)。既定値はNFR Requirements・NFR Designに従う(アクセストークン10分、リフレッシュトークン30分、ロックのしきい値5回・15分、再送の猶予10秒、Sessionの削除の保持7日・実行間隔1日・1回100バッチ×1,000行、キャッシュ最大1,000件・TTL60秒)。**JWTの鍵は、環境変数`MASTERSMITH_AUTH_JWT_SECRET`から`Environment`を通して`JwtKeyProvider`が直接読み、`@ConfigurationProperties`の束縛の対象にしない**(値を例外・ログに出さないため)。リポジトリの`application.yml`には既定値も実値も置かない(未設定なら起動失敗)。テスト用の`application.yml`には、テスト専用のダミーの鍵(32バイト以上)を置く。
5. **移行スクリプトは`V5__create_authentication.sql`**(`auth_session`・`account_login_state`。列・制約・インデックスは`nfr-design/scalability-design.md` NFR3.4のとおり)。`user_id`のインデックスは設けない。JPAのDDL自動生成には委ねず、`ddl-auto: validate`のまま。
6. **リフレッシュの再送の猶予(既定10秒)は、機能設計の`[assumption]`(Q5=Aとの差異)のとおり実装する**。`mastersmith.auth.refresh-reuse-grace`(0で猶予なし=Q5=Aの元の挙動)で変更できる。この差異の最終確認は、機能設計ステージの承認ゲートで人間が行う(Unit-major反復のため、ステージの承認ゲートは、全ユニットの完了後にまとまって提示される)。本計画の承認は、この差異への承認を代替しない。
7. **他ユニットの変更は、機能設計の追補8・11の範囲に限り、既存のメソッド・既存の振る舞い・既存のアサーションは変えない**。
   - U4(user-management): C11に`findByUserId(String): Optional<UserAccount>`と`dummyVerify(String): void`を追加する(`dummyVerify`は129文字以上を計算せず、実際の検証と同じ`HashConcurrencyLimiter`を共有し、上限超過は`HashCapacityExceededException`)。暫定の`CurrentOperatorProvider`・`HeaderCurrentOperatorProvider`・`Operator`(`usermanagement.security`)を削除し、`OperatorContext`を読む実装に置き換える(`UserController`・`MePreferencesController`・`UserAuthorizer`)。`revokeRefreshTokensOnDisable`は、U4で実装済みではなく(user-managementの計画前提9)、契約から削除する追補だけを行う。
   - U2(schema-introspector)・U6(menu-navigation)・U7(audit-logging): `ActiveRoleResolver`・`HeaderActiveRoleResolver`を削除し、`OperatorContext`を読む実装に置き換える。コントローラ・サービスの入口は変えない。
   - U3(permission-engine): `canAccessScreen`・`resolveEffectivePermission`が、`activeRoleId`のnull・空を、fail closed(NONE)として扱う。RBAC設定が空の間の`config-import-export`の例外は、`activeRoleId`にかかわらず適用する。C10のテーブル駆動テストを追加する(`team.md`の「権限判定ロジックの追加合格条件」)。
8. **共通基盤のうち、本ユニットが担う部品**(`logical-components.md`「横断的な部品の所有」): `SecurityFilterChain`・セキュリティヘッダー(`Referrer-Policy`・`nosniff`・`Content-Security-Policy`・`/api/**`の`Cache-Control: no-store`)・`AuthRequestSizeLimitFilter`・`AuthApiExceptionAdvice`・`AuthCrossCuttingExceptionAdvice`。加えて、`logical-components.md`保留14・15番のうち`application.yml`で完結するもの(内部設定DBの接続取得のタイムアウト3秒、`/actuator/health`のみの公開・結果の5秒キャッシュ、H2コンソールの無効(設定済みを確認))を設定する。**本Boltの対象外**: メトリクスのエクスポート形式(Prometheus/OTLP)・トレースの実装・構造化ログ・`RequestLogFilter`・HSTS(環境の前提)。
9. **`auth.session.revoke-all-on-startup`(全Sessionの削除)は、Webサーバーがリクエストを受け付ける前に実行する**(`SmartInitializingSingleton`で実行。Webサーバーの起動より前であることを、テストで確認する)。既定はfalse。
10. **同時実行のテスト(ロックの予約・補償・リフレッシュ・ロール選択・キャッシュの無効化)は、実際のH2に対して実行し、CIの必須の合格条件とする**(`reliability-design.md` NFR4.1)。タイミングに依存する固定の`sleep`は避ける。
11. **フロントエンド・複数プロファイル横断E2Eは、本Boltの対象外**。`team.md`の必須テスト種別のうち、(a)安全失敗(鍵・設定の不正で起動失敗)・(c)認可拒否(401・403)は本Boltで実施する。(b)複数プロファイル横断E2Eは、frontend-ui(U12)・統合の担当。統合テストとして、「ログイン → ロール選択 → 認証フィルタ越しの検証用コントローラ → ログアウト」の一連の流れを、`@SpringBootTest`で確認する(Step 15)。
12. **監査ログのイベント(ログイン・ログアウト・ロック)は発行しない**(機能設計の`[assumption]`。ロックの検知はメトリクス)。**残余リスク**(ロックを悪用した締め出し・パスワードスプレー・猶予内の盗用の検知の遅れ・検知範囲の1世代の限界)は、機能設計の判断のとおり受容(MVP)とし、本Boltで新たな対策は加えない。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "custom",
  "source": "team",
  "ordering": "原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを",
  "scope": "config-driven-admin-mvp",
  "test_strategy": "comprehensive",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: custom\n- **Ordering**: 原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを\n  作成・実行する(test-after)。ただし権限判定ロジック(ロール階層継承・主権限\n  FULL/READ/NONE/指定なし・補助権限CREATE/DELETEの解決処理)に限り、実装に先立って\n  権限マトリクス(組み合わせケース)を洗い出してから実装する、test-first/ATDD寄りの\n  例外的な進め方を認める(インタビューQ4: A. 全体はtest-afterを基本方針とする。\n  Q5: A. 権限判定ロジックだけこの例外を認める)。\n- テストはすべてのBoltにおいて第一級の成果物として扱う。\n- **カバレッジフロア**: スコープ `config-driven-admin-mvp`(`mvp` 系)の既定に従い、\n  80%行カバレッジとマージ前のCI実行を課す。\n- **権限・監査ログの追加合格条件**: 権限判定ロジックおよび監査ログ記録の実装に限り、\n  80%行カバレッジに加えて「主要な権限マトリクスの組み合わせを網羅するテーブル駆動\n  テスト」を追加の合格条件とする(80%行カバレッジという基準そのものを緩めるのではなく、\n  この2箇所にのみ上乗せする)(インタビューQ6: A)。\n- **Test Strategy「Comprehensive」の具体的内容**: 単体テストに加え、統合テスト・\n  画面操作を通したE2Eテスト・設定ファイルの契約テスト(config-schemaの形式チェック)を\n  標準として含む。負荷・性能テストは既定には含めない(インタビューQ7: A, B, C選択、\n  Dは対象外)。\n- **設定駆動(config-driven)特有の必須テスト種別**: 「アプリ本体を1つのまま設定の\n  入れ替えだけで複数業務に転用できる」という成功定義を検証するため、以下を必須とする\n  (インタビューQ8: A, B, C選択)。\n  - (a) 設定ファイルが不正・不完全なときにアプリが安全に失敗する(バリデーションエラー\n    になる)ことを確認する安全失敗/バリデーションテスト\n  - (b) 実際に2種類以上の異なる業務ドメインの設定プロファイル(例: 商品マスタ用・\n    蔵書マスタ用)で同一の操作シナリオを流し、「設定を差し替えるだけで動く」ことを\n    確認する複数プロファイル横断E2Eテスト\n  - (c) 権限が不足する操作は確実に拒否されることを狙って確認する認可拒否\n    (negative-authorization)専用テスト\n  - **監査ログ完全性テスト(更新・作成・削除の全操作が監査ログへ記録されることの網羅的\n    検証)は、今回のMVPスコープでは必須としない**。これはインタビューで2度確認した上での\n    意図的なスコープ判断であり(Q8選択肢Dは不採用)、見落としではない。\n- Build and Test ステージは、定義済みカバレッジフロアと確認済み品質目標の充足を検証する。\n  これらのステップを通過させるために基準を緩めることはしない。"
    }
  ],
  "obligations": {
    "strategy": "comprehensive",
    "strategy_volume": [
      "Ten to fifteen tests per component.",
      "Unit, integration, and E2E tests.",
      "Add performance and security tests when NFRs demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "custom",
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
      "Custom ordering - 原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを",
      "Implementation and tests - preserve that exact ordering; do not convert it to layer-local TDD.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:9ff5f4a3117c43915b0647dc8da168bba8fefba2f289aa7ad964e81afab834bc",
  "contract_sha256": "sha256:4fa8d24c65e3a6e1326c42865b60dbacdbf8f2481aebedce163e1f10630b41d8"
}
```

## 対象外レイヤーの扱い

- **フロントエンド層**: 前提事項11のとおり対象外(frontend-ui(U12)への要求は、機能設計の追補7番・`logical-components.md`保留17番として記録済み)。
- **複数プロファイル横断E2Eテスト**: 前提事項11のとおり対象外。
- **共通基盤のうち本ユニットが担わない部品**: 前提事項8のとおり対象外。

## Story-to-Code Step Traceability

user-storiesステージはSKIP対象(`project.md`学習事項)のため、`requirements.md`のFR ID・`functional-design/rules.md`のBR ID・`nfr-requirements`/`nfr-design`のNFR ID単位でトレースする。

| FR/BR/NFR ID | 概要 | 対応Step |
|---|---|---|
| FR3.1, BR5.1, BR5.5 | ログイン・アクセストークン(JWT)とリフレッシュトークンの発行 | Step 6, 8, 10 |
| FR3.2, BR5.8 | 複数端末の同時ログイン・ログアウトは該当のSessionだけ | Step 4, 8, 10 |
| FR2.7, BR5.3, BR5.4 | 連続ログイン失敗のロック(予約型・原子的な更新・`application.yml`)・FR2.7の文言の追補 | Step 1, 4, 6, 8 |
| BR5.2, BR5.15 | 失敗の応答の統一・`dummyVerify`・ハッシュ計算の上限超過の503 | Step 8, 10, 12 |
| FR2.3, BR5.6, BR5.7 | リフレッシュのローテーション・再送の猶予・再使用の検知・`isDisabled`の確認 | Step 4, 8, 10 |
| FR4.2, BR5.9, BR5.10 | アクティブロールの選択・自動選択・リフレッシュ時の再確認 | Step 8, 10 |
| FR3.3, BR5.11, BR5.12 | 認証フィルタ・Operatorの共有契約C15・未選択は403・他ユニットの暫定の操作者取得の置き換え | Step 8, 10, 12 |
| BR5.13 | C14 `getActiveRoleId` | Step 8 |
| BR5.14 | 認証情報・トークン・鍵の非出力 | Step 6, 14 |
| BR5.16 | 無効化されたユーザーの扱いの分担 | Step 8, 12 |
| FR3.4 | 権限昇格の防止(既存のU3・U4の防止を弱めない) | Step 12, 13 |
| FR1.6, NFR8.1 | 業務固有のハードコード禁止・設定値の外出し | Step 2, 6, 16 |
| NFR1.2 | 認証フィルタの処理時間(キャッシュヒット時・ミス時) | Step 8, 10 |
| NFR1.3 | ハッシュ計算の待機・トランザクションの外での呼び出し | Step 8, 9 |
| NFR2.1 | 認証の要否の規則・`SecurityFilterChain`・フィルタの適用範囲 | Step 10, 11 |
| NFR2.2 | JWTの署名・検証(HS256のみ・時計のずれ0・`sub`の照合)・鍵の扱い | Step 6, 7, 10, 11 |
| NFR2.3 | リフレッシュトークン・`sid`の生成とハッシュ | Step 6, 7 |
| NFR2.4 | ロックの予約型の更新と応答の統一 | Step 4, 5, 8, 9 |
| NFR2.5 | Sessionの失効・`revoke-all-on-startup` | Step 8, 9 |
| NFR2.7 | 認証情報の非出力(ログ・スパン・メトリクス・応答) | Step 14 |
| NFR2.8 | エラー応答(ProblemDetails・`code`・`instance`・`WWW-Authenticate`) | Step 10, 11 |
| NFR2.9 | セキュリティヘッダー・`Cache-Control: no-store`・ステートレス | Step 10, 11 |
| NFR2.10 | 認証前のリクエストボディの64KiB上限(413) | Step 10, 11 |
| NFR2.11 | SPAのルート・静的ファイルの認証なしの扱い | Step 10, 11 |
| NFR3.2, NFR3.4 | Sessionのキャッシュの規模・テーブルの設計とインデックス | Step 4, 8 |
| NFR4.1 | トランザクションの境界(短いトランザクション・C11は外・接続を保持しない) | Step 8, 9 |
| NFR4.2 | 内部設定DBの障害の503への変換 | Step 4, 8, 10, 11 |
| NFR4.3 | コミット後のキャッシュの無効化 | Step 8, 9 |
| NFR4.4 | 起動時の設定検証(fail fast)・鍵の値を出さない | Step 6, 7 |
| NFR4.5 | 期限切れ・失効したSessionの定期削除 | Step 8, 9 |
| NFR4.6 | 時刻の供給(`Clock`) | Step 6, 9 |
| NFR4.7 | 全Sessionの失効の手順(`revoke-all-on-startup`) | Step 8, 9 |
| NFR5.1, NFR5.2 | メトリクス・認証の出来事のログ | Step 14 |
| NFR5.3, NFR5.4 | スパン・ヘルスチェック(専用部品なし。`/actuator/health`の設定) | Step 2, 14 |
| NFR7.1 | 認証エラーの`code`(i18nキー) | Step 10, 11 |
| NFR8.2 | テスト要件(同時実行・安全失敗・認可拒否・非露出・境界値) | Step 5, 7, 9, 11, 13, 14, 15 |

## Step 1: 契約追補・機能設計の追補(最初の作業、ドキュメントのみ)

- [ ] `inception/contract-design/contract-summary.md`へ、前提事項1のC4・C10・C11・C14・C15の追補を追記する(既存記述は書き換えない。追補であることが分かる見出しまたは注記を付ける)。C15の契約表への追加と、共通基盤の契約の所有規則の例外(変更には、authentication-serviceと、すべての読み取り側のユニットの合意を要する)を含める
- [ ] `inception/units-generation/unit-of-work-dependency.md`の統合ポイント表へ、C15(葉の共有契約、DAGは変わらない)を追記する
- [ ] `construction/authentication-service/functional-design/functional-spec.md`・`rules.md`の末尾へ、「Code Generation着手時の追補」節を追加し、前提事項2(認証ライブラリの確定)・3(パッケージ)・4(設定キー)・5(移行スクリプト)の確定と、NFR Design保留10〜16・18〜20番の扱い(実装するもの・共通基盤への要求として記録のみのもの・本Boltで実装しないもの)を記録する

## Step 2: プロジェクト構造・ビルド設定

- [ ] `backend/src/main/java/com/mastersmith/auth/`と`com/mastersmith/common/security/`配下に、パッケージ構造を作成する(前提事項3)
- [ ] `backend/build.gradle.kts`へ、`spring-boot-starter-security`と`com.nimbusds:nimbus-jose-jwt`を追加する(BOMでの管理を確認し、管理外なら最新の安定版を確認して固定する)。既存のSpotless・Checkstyle・JaCoCoの設定は緩めない。テストでSpring Securityを用いるため、必要なら`spring-security-test`を追加する
- [ ] `backend/src/main/resources/application.yml`へ、`mastersmith.auth.*`(アクセストークン・リフレッシュトークンの有効期限、ロックのしきい値・時間、再送の猶予、Sessionの削除の保持日数・実行間隔・初回の遅延、キャッシュの最大件数・TTL、`session.revoke-all-on-startup`)と、`spring.datasource.hikari.connection-timeout: 3000`、`management.endpoints.web.exposure.include: health`・`management.endpoint.health.cache.time-to-live: 5s`を追加する。JWTの鍵の既定値・実値は置かない(前提事項4)
- [ ] `backend/src/test/resources/application.yml`へ、テスト専用のダミーの鍵(実在しない値、32バイト以上)と短縮した設定値を追加し、既存の`@SpringBootTest`が起動時のfail fast検証を通って動くようにする

## Step 3: テストランナー確認

- [ ] `./gradlew :backend:test --tests "com.mastersmith.auth.*"`が本ユニットのテストを実行できることを確認する(既存ユニットと共通のテスト基盤・Flywayマイグレーション適用フローを踏襲)。Spring Security導入後も、既存の全テストが、Step 12・13の更新の前後で実行できる状態を保つ

## Step 4: データモデル層の実装(entities.md準拠、NFR2.4・NFR3.4・NFR4.2)

- [ ] `V5__create_authentication.sql`を作成する(`auth_session`・`account_login_state`。前提事項5)
- [ ] `Session`・`AccountLoginState`エンティティと`SessionStatus`(`active`・`revoked`)を実装する。リフレッシュトークンのハッシュのみを保持し、平文は保持しない
- [ ] `SessionRepository`(主キー・`refresh_token_hash`・`previous_refresh_token_hash`による検索、ローテーションの条件付きの更新(現在のハッシュが同一の場合のみ、更新件数を返す)、失効(冪等)、ロール選択の条件付きの更新、期限切れの行のバッチ削除)と`AccountLoginStateRepository`(予約の原子的な更新(なければ作成、一意制約違反はやり直し用に伝える)、しきい値到達と同時の`locked_until`の設定、世代を条件とする補償の更新、成功時のリセット)を実装する。内部設定DBの障害を`AuthStorageUnavailableException`に変換する

## Step 5: データモデル層のテスト(test-after、実H2)

- [ ] `SessionJpaTest`・`AccountLoginStateJpaTest`: 往復の永続化、`refresh_token_hash`・`previous_refresh_token_hash`の一意制約(NULLの複数行を許す)、`account_login_state`の既定値、Flywayのスクリプトとエンティティのマッピング一致(`validate`)を確認する
- [ ] `SessionRepositoryTest`・`AccountLoginStateRepositoryTest`: 条件付きの更新の更新件数(1件・0件)、同時のローテーションで1件のみ成功すること、予約の更新(しきい値未達・到達・ロック中は確保できない・ロックの自動解除後の最初の予約で回数が0に戻り世代が進む)、補償の更新(世代が同じ場合だけ枠を返す・この予約が設定したロックだけを解く・`:myLockedUntil`がnullの場合)、自己修復(しきい値以上で`locked_until`が空)、行がない状態での同時の初回の予約(一意制約違反がやり直しで解消する)、バッチ削除(1,000行ずつ・有効なSessionを削除しない)を確認する

## Step 6: 基盤部品の実装(NFR2.2・NFR2.3・NFR4.4・NFR4.6)

- [ ] `AuthProperties`(`@ConfigurationProperties`+Bean Validation+追加の整合の確認: 有効期限・しきい値・ロック時間・猶予・保持日数・実行間隔・キャッシュの最大件数・TTL・`revoke-all-on-startup`。JWTの鍵は含めない。不備があれば起動を失敗させる)を実装する
- [ ] `JwtKeyProvider`・`SecretKeyMaterial`(鍵を`Environment`から直接読み、Base64のデコードと32バイト以上の検証を起動時に行う。例外のメッセージには設定のキー名と理由だけを含め、値・断片を含めない。`toString`は伏せ字)を実装する
- [ ] `AccessTokenIssuer`・`AccessTokenVerifier`(HS256のみ。`alg`の固定、`sub`・`sid`・`iat`・`exp`の必須確認、時計のずれの許容0。ロール・メールアドレス・氏名は含めない)、`RefreshTokenGenerator`(256ビット・`SecureRandom`・Base64URL)・`RefreshTokenHasher`(SHA-256・Base64URL)・`SessionIdGenerator`(128ビット・22文字)を実装する
- [ ] `Clock`(UTC)のBean、`SecurityHeaderValues`(ヘッダーの値を1か所に持つ)、`ProblemDetailsWriter`(フィルタでのRFC 9457の応答。`code`はi18nキー、`instance`に生のパスを入れない)を実装する

## Step 7: 基盤部品のテスト

- [ ] `AuthPropertiesTest`・`JwtKeyProviderTest`(安全失敗、`ApplicationContextRunner`): 鍵の未設定・Base64として不正・32バイト未満、有効期限・しきい値・ロック時間・猶予・保持日数・実行間隔・キャッシュの設定の不正、`revoke-all-on-startup`の不正な値で起動が失敗すること、起動失敗の出力全体に鍵の値・断片が現れないこと、`SecretKeyMaterial`の`toString`が伏せ字であることを確認する
- [ ] `AccessTokenTest`(テーブル駆動): 正常な発行と検証、`alg: none`・HS256以外・署名の不正・必須の値の欠落・期限切れ(時計のずれ0の境界)が、いずれも検証の失敗になること、ロール・メールアドレス・氏名がクレームに含まれないことを確認する
- [ ] `RefreshTokenGeneratorTest`・`RefreshTokenHasherTest`・`SessionIdGeneratorTest`: 長さ・文字種・一意性・ハッシュの決定性、平文がハッシュに含まれないことを確認する
- [ ] `ProblemDetailsWriterTest`・`SecurityHeaderValuesTest`: 応答の形式(`code`・`WWW-Authenticate: Bearer`・`instance`の非包含)、フィルタ経由の応答とコントローラ経由の応答でセキュリティヘッダーが同じであることを確認する

## Step 8: ビジネスロジック層の実装(W1〜W6、BR5.1〜BR5.16、NFR1.2・NFR1.3・NFR4.1〜NFR4.7)

- [ ] `com.mastersmith.common.security`に、C15の`Operator`(userId・sessionId・activeRoleId)と`OperatorContext`(読み取り専用のインタフェース)を実装する。テスト用に`OperatorContext`を差し替える支援クラスを併せて用意する(前提事項3)
- [ ] `UserAccountClient`(C11を包むアダプタ。U4の内部設定DBの障害を`AuthStorageUnavailableException`に変換し、`HashCapacityExceededException`はそのまま伝える)を実装する。C11のインタフェースへ`findByUserId`・`dummyVerify`が追加される前提のため、Step 12のU4側の追加を先に(同一コミット範囲で)行う
- [ ] `LoginAttemptGate`(予約・補償・成功の更新。トランザクションには参加するだけ(`MANDATORY`)。時刻は`Clock`から)、`SessionService`(作成・ローテーション(再送の猶予・再使用の検知)・失効・ロール選択の更新・リフレッシュ時のロールの再確認の判定表。`MANDATORY`。無効化の契機を返す)を実装する
- [ ] `SessionCache`(Caffeine、最大件数・TTLは設定、統計あり。キーごとの原子的な読み込み、コミット後の無効化。存在しないSessionを保持しない)を実装する
- [ ] `AuthenticationApplicationService`(W1〜W4。**トランザクションの境界をこの部品だけが所有する**: 外側のトランザクションを作らず、C11をトランザクションの外で呼び、短いトランザクションを`TransactionTemplate`で区切る。予約の一意制約違反のやり直し(新しいトランザクションで最大3回)、ハッシュ計算の上限超過での補償と503、実際の検証を行わない場合の`dummyVerify`、コミット後の`SessionCache`の無効化)を実装する
- [ ] `SessionContextService`(C14`SessionContextApi.getActiveRoleId`。BR5.13の例外・nullの扱い)、`SessionCleanupJob`(`@Scheduled(fixedDelay)`、1,000行ずつ・1回100バッチ、実行中は次を始めない)、起動時の`revoke-all-on-startup`(前提事項9)を実装する

## Step 9: ビジネスロジック層のテスト(認可拒否・並行テスト含む、実H2)

- [ ] `LoginAttemptGateTest`(実H2): 同時の誤った試行が何件あっても、検証できる試行がしきい値を超えないこと、しきい値到達と同時にロックが有効になること、ロック期間を延長しないこと、ロックの自動解除後の最初の予約で回数が0に戻ること、正しいパスワードの成功でしきい値到達の試行でもロックが解けること、補償の更新、自己修復、行がない状態での同時の初回の試行、時計を差し替えた境界(NFR4.6)を確認する
- [ ] `AuthenticationApplicationServiceTest`: 失敗の応答が、原因(未登録・パスワードの誤り・ロック中・無効化済み・招待中)にかかわらず同一であること、実際の検証を行わない場合に`dummyVerify`が呼ばれること、C11をトランザクションの外で呼ぶこと、上限超過が実際・ダミーのどちらでも503になること、成功の更新とSessionの作成が一体で反映されること、DB障害(C11の呼び出しを含む)で503になり補償が試みられること、予約後の想定外の例外では補償されず500になること、`dummyVerify`の順番待ちの間に接続プールの使用中の接続が0であること(open-in-viewが無効であることの確認)を確認する
- [ ] `SessionServiceTest`(実H2、テーブル駆動を含む): ローテーションの条件付きの更新(同時の更新で1件のみ成功、負けた側はSessionを失効させない)、猶予内・猶予を超えた再使用、ログアウトが該当のSessionだけを失効させること、ロール選択(保持しないロールは403相当)、リフレッシュ時のロールの再確認(判定表の全ケース)、リフレッシュとロール選択の並行実行(古いロールで上書きされない・1回だけやり直す・やり直しも失敗した場合に401でSessionを失効させない)、失効の冪等を確認する
- [ ] `SessionCacheTest`: 更新のコミット後に無効化されること、読み込みと無効化の競合(ストレステスト)で古い値が残らないこと、TTLで解消すること、存在しないSessionを保持しないこと、ヒット・ミスの統計を確認する
- [ ] `SessionContextServiceTest`(契約テスト)・`SessionCleanupJobTest`・`RevokeAllOnStartupTest`: 不存在・有効でない場合の例外と未選択のnull、有効期限から保持日数を過ぎたSession(revokedを含む)だけが削除され有効なSessionは削除されないこと、1,000行ずつの複数回の削除、失敗が認証に影響しないこと、`revoke-all-on-startup`がWebサーバーがリクエストを受け付ける前に実行されることを確認する
- [ ] `UserAccountClientTest`: C11の呼び出しでのDB障害が`AuthStorageUnavailableException`に変換されること、`HashCapacityExceededException`がそのまま伝わることを確認する

## Step 10: API層・セキュリティ層の実装(C4・BR5.11・NFR2.1・NFR2.8〜NFR2.11)

- [ ] DTO(`LoginRequest`・`LoginResponse`(accessToken・refreshToken・roles・activeRoleId)・`RefreshRequest`・`RefreshResponse`・`ActiveRoleRequest`)を実装する。パスワード・トークンを`toString`に含めない
- [ ] `BearerAuthenticationFilter`(認証を要するパスにだけ適用し、認証不要のパスでは実行しない(`shouldNotFilter`)。署名・有効期限・Session・`sub`の一致を確認し、`Operator`をセキュリティコンテキストに設定する。原因を区別しない401、キャッシュミスでのDB障害は503)、`SecurityContextOperatorContext`(C15の実装)、`AuthSecurityConfig`(`SecurityFilterChain`。認証の要否の規則(ログイン・リフレッシュ・招待受諾・静的ファイル・`/actuator/health`は認証不要、その他のactuatorは拒否、`/api/**`の残りは認証必須)、ステートレス・CSRF無効・CORSなし、セキュリティヘッダー、`/api/**`の`Cache-Control: no-store`)を実装する
- [ ] `AuthController`(`POST /api/auth/login`・`/refresh`・`/logout`、`PUT /api/auth/active-role`)、`AuthRequestSizeLimitFilter`(`/api/auth/**`の64KiB上限、Spring Securityのフィルタチェーンより前の順序で`FilterRegistrationBean`により登録)、`AuthApiExceptionAdvice`(`AuthController`に限定、`@Order`を明記。401・403・413・503・400のProblemDetailsと`code`)、`AuthCrossCuttingExceptionAdvice`(対象の例外の型をU5の3つ(`SessionNotFoundException`・`SessionExpiredException`・`AuthStorageUnavailableException`)に限定し、他ユニットのリクエスト処理の中のC14・C11の例外を401・503に変換。`@Order`を明記)を実装する

## Step 11: API層・セキュリティ層のテスト(認可拒否専用テスト含む)

- [ ] `BearerAuthenticationFilterTest`(テーブル駆動): トークンなし・`alg: none`・HS256以外・署名の不正・必須の値の欠落・期限切れ・`sub`とSessionの`userId`の不一致・失効・期限切れのSessionが、いずれも同一の401になること、キャッシュミスでDB障害のとき503、キャッシュヒットのとき通ること、リクエストヘッダー(`X-User-Id`・`X-Active-Role-Id`)が無視されること、**認証不要のパス(ログイン・リフレッシュ・招待受諾・静的ファイル・ヘルス)で、期限切れ・不正な`Authorization`ヘッダーを付けても、フィルタで401にならないこと**、`Bearer`のスキーム名の大文字小文字を区別しないことを確認する
- [ ] `AuthSecurityConfigTest`: 認証の要否の規則(3つの認証不要のAPI・静的ファイル・`/actuator/health`・その他のactuatorの拒否・`/api/**`の残りは認証必須)、セキュリティヘッダーの値、`Cache-Control: no-store`が`/api/**`にだけ付くこと、すべての応答に`Set-Cookie`がないこと、`/api/**`・`/actuator/**`以外のパスへの`GET`・`HEAD`以外のメソッドが拒否されること、ヘルスの結果が5秒間キャッシュされることを確認する
- [ ] `AuthControllerTest`(`@WebMvcTest`): 正常系(200・204)、**認可拒否専用テスト**(未認証の401・保持しないロールの選択の403)、失敗の応答の一本化(`auth.login.failed`)、503(ハッシュ計算の上限超過・DB障害)、400・413、応答に`refreshTokenHash`・パスワード・鍵が含まれないことを確認する
- [ ] `AuthRequestSizeLimitFilterTest`・`AuthApiExceptionAdviceTest`・`AuthCrossCuttingExceptionAdviceTest`: `Content-Length`がある場合・チャンク転送の場合(`RequestBodyTooLargeException`が包まれる場合と直接伝わる場合の両方)の413と、**両方の経路で413のセキュリティヘッダーが同じであること**、認証前のボディの拒否、ProblemDetailsの形式と`code`(`instance`を含まないこと)、他ユニットのコントローラの中でのC14の`SessionNotFoundException`・`SessionExpiredException`が401、`AuthStorageUnavailableException`が503になること(U10・U11を想定した検証用のコントローラ)を確認する
- [ ] `SecurityContextOperatorContextTest`(契約テスト): 認証済みのリクエストで`Operator`を返すこと、`activeRoleId`がnullでも`Operator`が存在すること、未認証では解決できないことを確認する

## Step 12: 他ユニットの変更(追補8・11。既存の振る舞いは変えない)

- [ ] U4: `UserAccountLookupApi`へ`findByUserId`・`dummyVerify`を追加し、`UserAccountLookupService`で実装する(`dummyVerify`は129文字以上を計算せず、`HashConcurrencyLimiter`を共有する)。`CurrentOperatorProvider`・`HeaderCurrentOperatorProvider`・`usermanagement.security.Operator`を削除し、`UserController`・`MePreferencesController`・`UserAuthorizer`が`OperatorContext`を読む実装に置き換える(コントローラ・サービスの入口は変えない。操作者が解決できなければ401、`activeRoleId`がnullでも自前で401にせず、そのままC10へ渡す)
- [ ] U2・U6・U7: `ActiveRoleResolver`・`HeaderActiveRoleResolver`を削除し、`SchemaIntrospectionController`・`MenuController`・`AuditLogController`(および`MenuUnauthorizedException`の関連箇所)が`OperatorContext`を読む実装に置き換える
- [ ] U3: `PermissionEngineApiImpl`の`canAccessScreen`・`resolveEffectivePermission`が、`activeRoleId`のnull・空をfail closed(NONE)として扱うよう変更する(RBAC設定が空の間の`config-import-export`の例外は、`activeRoleId`にかかわらず適用する)。既存のメソッドのシグネチャ・既存の振る舞いは変えない

## Step 13: 他ユニットの変更のテスト(認可拒否専用テスト・テーブル駆動テスト含む)

- [ ] `PermissionEngineApiImplTest`・`PermissionEngineIntegrationTest`への追加(**`team.md`の追加合格条件のテーブル駆動テスト**): `activeRoleId`のnull・空・実在・実在しない × RBAC設定の有無 × 画面(`config-import-export`を含む)の組み合わせ。既存のテストは変更しない
- [ ] `UserAccountLookupServiceTest`への追加: `findByUserId`(存在・不存在・和集合・`passwordHash`がnull)、`dummyVerify`(129文字以上は計算しない・`HashCapacityExceededException`の伝播・許可の返却・ユーザーを指定しないこと)。`HeaderCurrentOperatorProviderTest`を、`OperatorContext`を差し替える形の`UserAuthorizerTest`の更新に置き換える
- [ ] 既存のコントローラ・サービスのテスト(`UserControllerTest`・`MePreferencesControllerTest`・`UserApiExceptionAdviceTest`・`UserApiIntegrationTest`・`UserManagementNoLeakTest`・`SchemaIntrospectionControllerTest`・`MenuControllerTest`・`AuditLogControllerTest`)を、ヘッダーで操作者を渡す形から、`OperatorContext`を差し替える形へ更新する(期待する振る舞い・アサーションは変えない。認可拒否のケースは、操作者の未解決(401)・`activeRoleId`のnull(403)・権限なし(403)を維持または追加する)。`HeaderActiveRoleResolverTest`は、対象の削除に伴い削除する
- [ ] ヘッダー方式の暫定実装が残っていないことを、コードの検索・アーキテクチャテスト(`X-User-Id`・`X-Active-Role-Id`・`HeaderCurrentOperatorProvider`・`HeaderActiveRoleResolver`の不存在)で確認する

## Step 14: 可観測性の実装とトークン・認証情報の非露出の確認(BR5.14・NFR2.7・NFR5.1〜NFR5.4)

- [ ] `AuthMetrics`(`auth.login`・`auth.login.duration`・`auth.refresh`・`auth.refresh.reuse.within.grace`・`auth.refresh.token.reuse.detected`・`auth.logout`・`auth.active-role`・`auth.account.locked`・`auth.login.hash.capacity.exceeded`・`auth.filter.duration`・`auth.filter.unauthorized`・`auth.db.unavailable`・`auth.session.cleanup`。名称・種別・ラベルは`nfr-design/observability-design.md`のとおり。ラベルに利用者・トークンの値を含めない)と`AuthEventLogger`(認証の出来事のログ。userId・sessionIdのみ。パスワード・トークン・ハッシュ・鍵・メールアドレスを出さない)を実装する。ログイン・リフレッシュ・認証フィルタに`ObservationRegistry`による観測(スパン)を付ける。属性にメールアドレス・トークン・ハッシュを含めない
- [ ] `AuthenticationNoLeakTest`: ログイン(成功・失敗・ロック中・503)・リフレッシュ(成功・再使用・猶予内)・ログアウト・認証フィルタ(401)・起動時の鍵の検証失敗の各経路で、U5のログ出力(`ListAppender`で捕捉)・メトリクスのラベル・スパンの属性・ProblemDetails・応答に、パスワード・トークン(平文・ハッシュ)・鍵・メールアドレスの実値が現れないことを確認する
- [ ] 専用のヘルスチェック部品は設けない(NFR5.4。`/actuator/health`の設定はStep 2)

## Step 15: 統合・E2Eテスト

- [ ] `AuthenticationFlowIntegrationTest`(`@SpringBootTest`、実H2・認証フィルタ越し): ログイン → 複数ロールのユーザーのロール選択 → 認証フィルタ越しの検証用コントローラ(`OperatorContext`を読み、`PermissionEngineApi`の判定を通す)での権限制御(許可・権限なし(403)・ロール未選択(403)) → リフレッシュ → ログアウト後の401、の一連の流れを確認する。複数端末の同時ログイン(ログアウトが他のSessionに影響しないこと)、ユーザーの無効化後のリフレッシュの401(Sessionの失効)を含める
- [ ] ロックの一連の流れ(誤ったパスワードをしきい値まで送る → ロック中は正しいパスワードでも同一の401 → 時計を進めて自動解除 → 成功)を、統合テストで確認する

## Step 16: 環境・ビルド設定

- [ ] 新規パッケージ・マイグレーション・依存が、既存のGradle/Spotless/Checkstyle/JaCoCo設定下でビルド・整形されることを確認する(`spotlessCheck`・`checkstyleMain`・`checkstyleTest`が本ユニットのファイルと変更した他ユニットのファイルに関して合格、既存の全テストがグリーン、行カバレッジ80%以上)。カバレッジの基準・閾値は緩めない
- [ ] リポジトリに、JWTの鍵・認証情報の実値が入っていないこと(テスト専用のダミーを除く)を確認する(NFR2.8。シークレットスキャンの観点)。業務固有のテーブル名・カラム名・業務ルールが本ユニットのコードに無いことを確認する(FR1.6・NFR8.1)

## Step 17: ドキュメント・トレーサビリティ

- [ ] 各クラス・メソッドに必要最小限のJavadoc(非自明な設計判断のみ)を付与する。生成する全ソースファイルの先頭に、Apache License 2.0の標準ヘッダー(年`2026`、著作権者`agwlvssainokuni`)を入れる。他ユニットの暫定実装の注記(「U5の実装時に差し替える」)を、削除に伴い整理する
- [ ] `code-summary.md`・`traceability.json`はオーケストレーターが実施。`source-manifest.json`はdispatch指示により開発エージェントが作成する(U5が作成・変更したアプリケーションのソースのパス。U2・U3・U4・U6・U7・共通基盤への変更分を含む)
