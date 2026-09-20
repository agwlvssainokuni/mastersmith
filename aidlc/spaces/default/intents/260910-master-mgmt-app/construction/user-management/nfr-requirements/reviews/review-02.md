## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-20T00:57:06Z
**Iteration:** 2

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/reliability-requirements.md > NFR4.2 招待の整合性(メール送信・競合) | 反復1の指摘(トランザクション保持の上限、同一email同時招待の応答コード、再招待と受諾・取消の競合)は解消した。NFR4.2にトランザクション保持の上限(約12秒)、招待の同時実行数の上限5と超過時の503、正規化後email単位の直列化、一意制約違反の422、ロック待ちタイムアウトの503、再招待が受諾・取消と競合した場合の422、補償方式との比較と採用理由が記載された。残る穴は再招待の進行中に受諾・取消・更新の側が待たされる経路で、新規指摘R-14として扱う。 | なし(残余はR-14) | Resolved |
| R-02 | Major | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.10 招待トークンのURL露出の抑止 | NFR2.10が新設され、アプリケーションログ・リクエストログ、アクセスログ、リバースプロキシのログ、OTELスパン、メトリクスのURIラベル、フロントエンドの受諾画面を列挙してルートのテンプレートへの置換またはマスクを要件化した。NFR5.3とNFR5.1にも同じ要件が反映され、NFR8.2に検証テストがある。 | なし | Resolved |
| R-03 | Major | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.11 初期管理者の資格情報の供給 | NFR2.11が新設され、環境変数等での外部注入、リポジトリへ既定値を置かないこと、ログ・監査ログへ非出力、起動時のfail fast(NFR4.4)が明記された。初回ログイン後のパスワード変更の要否は、機能設計に該当要件がないためU5の未解決事項として記録されている。 | なし | Resolved |
| R-04 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/tech-stack-decisions.md > NFR8.2 / performance-requirements.md NFR1.1・NFR1.2 | NFR8.2にセキュリティ検証テスト(資格情報の非出力、パスワード長の境界値、404の一本化、HTMLエスケープと件名、413)が追加された。NFR1.2は「p95で300ms以下」の上限表現と計測条件に改められ、NFR1.1は自動検証をしない設計目標と明記された。 | なし | Resolved |
| R-05 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.7 | 招待リンクのベースURLを設定値から生成しリクエストヘッダーから導出しないこと、本番相当でHTTPS必須、設定不備は起動時にfail fastすることが追記された。NFR8.2の安全失敗テストの対象にも含まれている。 | なし | Resolved |
| R-06 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.7(件名の抽出) | 文字参照のデコード後の最終文字列で改行を検査すること、改行は除去せず拒否すること、最大長200文字、非ASCIIの符号化が確定した。 | なし | Resolved |
| R-07 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.6・残余リスク5 | 監査ログへの氏名・メールアドレスの複製とC6経由の閲覧経路、将来の匿名化要件が及ばない制約が明記され、残余リスク5に記録された。区分名には[assumption]が付いている。 | なし | Resolved |
| R-08 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/performance-requirements.md NFR1.1・NFR1.3 / tech-stack-decisions.md 契約追補 | NFR1.1に招待受諾APIが含められ(待機2秒の打ち切りとの関係も記載)、C5の受諾APIへの503とC11の専用例外の型が契約追補の一覧の2番と3番に記録された。 | なし | Resolved |
| R-09 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > 残余リスク | 残余リスクの表が新設され、無効化後の既発行トークン、別emailへの招待によるロール付与、招待トークンの平文・無期限保存を、受容の判断と見直し先つきで記録した。STRIDEの権限昇格の行にも残余リスクへの参照がある。 | なし | Resolved |
| R-10 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/observability-requirements.md > NFR5.1・NFR5.2 | アラートの暫定閾値が[assumption]として置かれ(5分間に3回・10回・3回)、NFR5.2の失敗ログは操作者のuserIdとリクエストIDで相関し、巻き戻されるUserのIDを使わないことが明記された。 | なし | Resolved |
| R-11 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/reliability-requirements.md > NFR4.1(PUTの同時更新)・NFR4.3 | 部分的に解消した。イベントをコミット成功後に発行する順序はNFR4.3に明記された。一方、PUTの後勝ち方針には2点の穴が残る。(1)beforeValueを更新と同一トランザクション内で読むと明記されているが、組込みDBの既定の分離レベルでは行ロックなしの読み取りは、並行する2件の更新が同じbeforeValueを読むことを防がない。このため「beforeValueが不正確にならない」という記述の根拠が足りない。(2)requirements.mdのNFR4は「同時更新の競合は楽観ロックで検出する(FR6.3参照)」とするが、NFR4.1はこの記述に触れず、FR6.3の対象が業務データ編集画面でありC5のPUTに版数の項目がないことを理由に示していない。 | NFR4.1に、beforeValueの正確性を担保する手段(対象行の更新前ロックなど)を追記するか、正確性の保証を弱めた記述にする。あわせて、要件のNFR4の楽観ロックの記述との関係(FR6.3は業務データの画面向けであり、Userには適用しない理由)を一文で明記する。 | Unresolved |
| R-12 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.3 / scalability-requirements.md NFR3.3 | パスワード上限のverifyPasswordHashでの事前検査、リクエストボディの上限64KiB(413)、タイミング差のリスクの受容と残余リスク4、ヒープに上限×19MiBを別途見込む注記が追加された。 | なし | Resolved |
| R-13 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/tech-stack-decisions.md > NFR7.2 / traceability.json | NFR7.2が新設され、422のerrors[].messageをi18nキーで表す方針が[assumption]つきで記録され、契約追補の一覧の6番があり、traceability.jsonのNFR7の対象にもNFR7.2が加わった。ただし、翻訳の担当に関する記述の根拠はR-17で扱う。 | なし | Resolved |
| R-14 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/reliability-requirements.md > NFR4.1・NFR4.2 | 再招待のトランザクションは、SMTP送信の完了(NFR4.2のとおり最大約12秒)までUserの行を未コミットのまま更新している。このため同じUserに対する受諾・取消(DELETE)・更新(PUT)の条件付き更新は、行ロックの解放を待つことになる。NFR4.2のロック待ちタイムアウトの503は同一email招待の項にのみ書かれており、受諾・DELETE・PUT側の応答(組込みDBの既定のロック待ち時間はSMTPの保持時間より短くなりうる)が未規定で、500になりうる。さらにNFR4.1の条件付き更新の条件はstatus=invitedのみで、招待トークンの一致を含まない。受諾が旧トークンで行を読んだ後に再招待がコミットすると、旧トークンでの受諾が成立し、再招待が旧トークンを無効にするというNFR2.4・BR4.11の保証が崩れる。加えて、同一emailの待機者が上限5の枠を占有するかどうか(直列化との適用順序)も未規定である。 | NFR4.2のロック待ちタイムアウトの503を、Userを変更するすべてのエンドポイントに適用すると明記する(受諾は既存の追補の一覧の2番と併せて整理する)。NFR4.1の条件付き更新の条件に、招待トークンの一致を含める。直列化と同時実行数の上限の適用順序を一文で定める。 | New |
| R-15 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.7(件名) / tech-stack-decisions.md 契約追補の7番 | 件名が200文字を超える場合と改行を含む場合は、送信失敗として扱われ503と失敗のカウンタ(user_invitation_mail_failed_total)へ計上される。しかしBR4.15はnameの最大長を定めておらず、契約追補の7番は制御文字の検証のみを追加する。テンプレートの`<title>`へnameが入る場合、長いnameは何度再試行しても決定的に失敗し、入力に起因する問題が「一時的に処理できない」503として返り、SMTP障害のアラート(NFR5.1の3回以上)の誤報も招く。 | 契約追補の7番に、nameの最大長の検証(422)を追加する。または、入力起因の件名の拒否は503ではなく422(name)とし、失敗のカウンタにはSMTPの失敗のみを計上する、と分けて定める。 | New |
| R-16 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/security-requirements.md > NFR2.10(フロントエンド)・NFR2.11・NFR2.3 | 他ユニットや環境へ引き渡す事項が、成果物内に分散したままである。受諾画面の`Referrer-Policy: no-referrer`はレスポンスヘッダーであり、SPAを配信するのがSpring Bootである以上、フロントエンドのコードではなくサーバー側の設定(パッケージング側)の責務に当たりうるが、U12の責務と書かれている。ほかにも、リバースプロキシのマスク設定(tech-stack-decisionsの確認事項8)、初回ログイン後のパスワード変更とタイミング差の緩和(U5)、C11専用例外のHTTP 503への変換(U5)がある。契約追補の一覧は契約と機能設計の追補に限られ、これらの引き渡しの一覧がない。 | Referrer-Policyの設定の担当(U12かサーバー設定か)を明記する。他ユニット・環境への引き渡し事項を、宛先ユニットと出典つきで一つの表にまとめる(契約追補の一覧の隣でよい)。 | New |
| R-17 | Minor | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/user-management/nfr-requirements/tech-stack-decisions.md > NFR7.2 | 「フロントエンドがconfig-engineが管理する翻訳リソースを用いて翻訳する」と事実のように記述しているが、[assumption]の注記が及ぶのは「サーバーが翻訳するかフロントエンドが翻訳するか」の点のみである。翻訳リソースの管理主体は、要件(FR10.2)にも渡された契約(C5)にも定めがなく、project.mdの学習事項は翻訳リソースをビルド成果物と扱っている。根拠を確認できない他ユニットの責務を断定している。 | 「config-engineが管理する」の部分を[assumption]に含めるか、管理主体を特定せず「フロントエンドが翻訳リソースを用いて翻訳する」に改める。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| traceability.json のJSON構文チェック | PASS(パース成功、coverage 8件) | 構文上の問題なし |
| coverage対象のNFRx.y見出しの存在確認 | PASS(欠落なし) | NFR2.10・NFR2.11はsecurity-requirements.md、NFR7.2はtech-stack-decisions.mdに見出しとして実在する。NFR1.1-1.4、2.1-2.11、3.1-3.4、4.1-4.5、5.1-5.5、7.1-7.2、8.1-8.2がすべて見出しと一致 |
| 要件NFR1〜NFR8の網羅 | PASS | 8件すべてがcoverageに列挙され、NFR6のN/Aの理由も妥当 |
| 質問回答Q1〜Q8と機能設計・契約との整合 | 概ね整合 | Q1〜Q8の確定内容と矛盾する記述は無い。契約追補の一覧は保留として正直に記録されている。共有契約C5・C11と矛盾する確定表現は無く、不足分はすべて追補として記載されている。残る穴はR-11・R-14・R-15 |

### Summary

反復1のMajor 3件(R-01からR-03)と、Minor 10件のうち9件は解消した。R-11は一部が残るためUnresolvedとした。新規のR-14からR-17はいずれもMinorで、Critical・Majorは無く、開発者が追加の設計判断なしに実装に着手できる水準にあるため、READYとする。R-11・R-14・R-15は、承認前後の軽微な追記で解消することを推奨する。
