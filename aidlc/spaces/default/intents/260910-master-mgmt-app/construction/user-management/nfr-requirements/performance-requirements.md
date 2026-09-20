# Performance Requirements — user-management (U4)

## NFR1.1: 管理系APIの応答時間

`/api/users`系(一覧・招待・更新・無効化)、招待受諾API(`POST /api/users/invitations/{token}/accept`)、`/api/me/preferences`の応答時間は、要件のNFR1に従い**3秒以内(95パーセンタイル)**、同時アクセス最大50ユーザーの条件で満たす。

- 測定対象: HTTPリクエストの受信からレスポンス送信まで(サーバー側処理時間)。
- 例外: 招待メールを送信する招待API(`POST /api/users`)は、NFR1.4のとおりSMTPの応答時間を含むため本項を適用しない。
- `GET /api/users`はページングなしで全件を返す(functional-spec.md W7、rules.md BR4.14)。想定規模(数十名程度、NFR3)では本項の目標内に収まる。規模が想定を大きく超える場合の扱いはNFR3.2に記載する。
- 招待受諾APIはArgon2idのハッシュ計算(NFR1.2)を伴い、認証不要で最も負荷が高い。通常時は本項の3秒以内に収める。混雑して同時計算の順番待ちが2秒を超えた場合は、本項の目標を待たずに503で打ち切る(NFR1.3)。
- 要件のNFR1にある「1テーブルあたり最大10万行」は業務データ(一覧・詳細画面)向けの目標であり、ユーザー管理の対象外である。
- 検証方法: team.mdは負荷・性能テストを既定に含めない(Q7でD不採用)ため、同時50ユーザーでの負荷試験による自動検証は行わない。本項は設計目標とし、統合テストで計測した各APIの応答時間を、Build and Testの結果に記録して目視で確認する([assumption])。

## NFR1.2: パスワードハッシュ(Argon2id)の計算時間

パスワードのハッシュ計算・検証(Q1=A)は、**1回あたり300ms以下(95パーセンタイル)**を上限の目標とする。質問回答Q1=Aの「概ね100〜300ms」は想定される範囲を示す目安であり、100msを下回る(高速な環境)場合も不適合とはしない(パラメータを弱くして速くすることは認めず、下記の初期パラメータを下限とする)。

- Argon2idの初期パラメータは、OWASPが示す最小推奨構成である**メモリ約19MiB(19456KiB)・反復2回・並列度1**とする。
- パラメータは`application.yml`で変更できるようにする。上記の時間はあくまで目標であり、実装後に実機で計測して、必要ならパラメータを調整する。
- 対象処理: 招待受諾(rules.md BR4.2)、初期管理者の作成(BR4.7)、C11の`verifyPasswordHash`(BR4.13。ログイン時にauthentication-serviceから呼ばれる)。
- 計測条件: ハッシュ計算1回を並列度1・同時実行なしで繰り返し測定し(十分なウォームアップ後)、実行環境のCPUコア数・メモリを記録する。ハッシュ計測の単体ベンチマークテストを用意し、p95が300ms以下であることを確認する。基準を満たさない環境では、パラメータを弱めるのではなく、実行環境の見直しまたは同時実行上限(NFR1.3)の調整で対応する。

## NFR1.3: 同時ハッシュ計算数の制御

ハッシュ計算を同時に実行できる数に上限を設ける(Q2=A、Q2 Follow-up)。

- 上限の既定値は**CPUコア数**とし、`application.yml`で変更できる。
- 順番待ちが**2秒**を超えた場合は処理を打ち切り、503 Service Unavailable(RFC 9457形式のProblemDetails)を返す。
- C11の`verifyPasswordHash`のような内部呼び出しでは、専用の例外を返し、HTTPの503への変換は呼び出し側(authentication-service)が行う方針とする(この点はauthentication-service(U5)の機能設計で確認する)。
- 契約追補(保留): 招待受諾API(C5)への503レスポンスの追加、およびC11への専用例外の型の定義が必要になる。Q8の`locale`追補(NFR7.1)と同様に、Code Generationの計画承認までに反映する保留の追補として、tech-stack-decisions.mdの「契約追補(保留事項の一覧)」に記録する。
- 意図: 認証不要の招待受諾APIやログインに多数のリクエストが集中しても、ハッシュ計算がメモリ・CPUを使い切らないようにする(NFR3.3も参照)。

## NFR1.4: 招待メール送信の時間予算

招待メールは招待API内で**同期送信**し、SMTPの接続・送信の**合計タイムアウトを10秒**とする(Q3=B、Q3 Follow-up)。

- 10秒を超えた場合は送信失敗として扱い、rules.md BR4.16に従って招待全体を巻き戻し、503を返す。
- 自動リトライは行わない。管理者が再度招待操作(rules.md BR4.11の再招待)を行う。
- 招待APIには、NFR1.1の3秒目標を適用しない(SMTPの応答時間が支配的なため)。ただし、SMTPが正常に応答する通常時の処理時間は、メール送信を除いて3秒以内に収まること。

## 根拠

- 要件: `inception/requirements-analysis/requirements.md` のNFR1(応答3秒・同時50ユーザー)
- 機能設計: `construction/user-management/functional-design/functional-spec.md`(W1〜W8)、`rules.md`(BR4.2・BR4.7・BR4.13・BR4.14・BR4.16)
- 契約: `inception/contract-design/contract-summary.md` のC5(REST API)・C11(`verifyPasswordHash`)
- 質問回答: `nfr-requirements-questions.md` Q1・Q2・Q3(各Follow-upを含む)
