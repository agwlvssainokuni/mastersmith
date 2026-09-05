# Delivery Planning Questions: MasterSmith MVP

団の実行順序(**Bolt**、Construction段階で1回分のビルドサイクルとして扱う、1つ以上のUnitのまとまり)を決めるための質問。units-generation(2.7)が出したUnit依存関係(トポロジー)を土台に、どのUnitをどのBoltにまとめ、どの順で作るかという経済的な判断を、ここで確定する。

team.md(practices-discovery)で既に「Bolt 1はWalking Skeleton(業務DBスキーマ読み込み→内部H2設定保存→動的画面生成という一連の流れを一通り貫通させる薄いバージョン)とし、単独実行・ゲート付きで、残りのBoltを続けるかはユーザーが承認する」と確定済みのため、Q1〜Q3ではこのBolt 1の具体的なUnit構成のみを詰める。

## Q1. Bolt 1(Walking Skeleton)に認証を含めるか

Walking Skeletonが実際のブラウザ操作で一気通貫することを証明するには、スキーマ取り込み画面・設定管理画面が管理者ゲーティング(FR5.5/FR5.6、ADR-002によりスキーマ取り込みも対象)であるため、何らかの認証機構が必要です。

- A. Bolt 1に最小限の認証(ログイン+isAdminクレームを含むJWT発行のみ。パスワード忘れ・登録完了・自己サービス変更・通知メール等の自己サービス系フローは含めない)を含める。これによりBolt 1は実際のログインを経た本物の管理者ゲーティングを貫通させて証明できる(推奨)
- B. Bolt 1では認証を作り込まず、開発時のみ有効な認証バイパス(スタブ)で貫通を確認する。実際のログイン機能はBolt 2以降でauth(U5)として独立に構築する
- X. Other (please specify)

[Answer]: A

## Q2. Bolt 1の具体的なUnit構成

Q1の回答を踏まえ、Bolt 1に含めるUnitと、その中での実装範囲(スキーマ取り込み→内部H2設定保存→動的画面生成を貫通させるための最小限の範囲)を確定します。

- A. 以下の5〜6 Unitの最小サブセットを束ねる(Q1でAを選んだ場合は6 Unit、Bを選んだ場合はU5を除く5 Unit): **U1 schema-ingestion**(全機能、後続Boltでの追加実装なし)/ **U2 config-management**(最小: DB接続先設定+スキーマ取り込み結果の取り込み保存のみ。メニュー構成・検索条件・バリデーション・エクスポート/インポート等はBolt 3以降)/ **U4 dynamic-data-access**(最小: 一覧・詳細のみ。新規作成・更新・FK検索はBolt 6以降)/ **U5 auth**(Q1でAの場合のみ、最小: ログイン+トークン発行のみ)/ **U9 frontend-core**(最小: ログイン画面(Q1でAの場合)+一覧・詳細画面のみ)/ **U11 packaging**(最小: フロントエンド成果物を単一WARへ組み込み実行できる最小限の配線。CI連携等の作り込みはBolt 10)(推奨)
- B. 異なるUnit構成・実装範囲を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q3. Bolt 1後(Bolt 2以降)の並び順の考え方

Bolt 1の後、残りのUnit(および各Unitの未実装部分)をどう並べるかの基本方針を決めます。

- A. 「土台を先に」の考え方を採る: unit-of-work-dependency.mdの依存レベル(レベル0: schema-ingestion(完了済)・permission・audit-log、レベル1: config-management(残り)・auth(残り)、レベル2: dynamic-data-access(残り)・account-management、レベル3: notification・frontend-core(残り)・frontend-admin、レベル4: packaging(仕上げ))にほぼ沿って進める。開発者一人体制のため、正式なWSJFスコアリングは行わず、依存関係を満たす順に淡々と進めることを優先する(推奨)
- B. リスクの高い部分を先に着手する(具体的にどの部分が最もリスクが高いと考えるか教えてください)
- C. 価値の高い部分を先に着手する(具体的にどの部分を優先するか教えてください)
- X. Other (please specify)

[Answer]: A

## Q4. Bolt 2以降の具体的な束ね方(ドラフト)

Q3の「土台を先に」という方針に基づく、Bolt 2以降の具体的なUnitの束ね方のドラフトです。

- A. 以下の9 Boltで残り10 Unitの未実装部分をすべてカバーする(推奨): **Bolt 2**=audit-log(全機能)/ **Bolt 3**=permission(全機能)/ **Bolt 4**=config-management(残り: メニュー構成・検索条件・一覧表示項目・編集対象外項目・バリデーション・フォーム部品・論理表示名・エクスポート/インポート・キャッシュ管理)/ **Bolt 5**=auth(残り: パスワード忘れ・登録完了・自己サービス変更・ログイン試行制限)+account-management(全機能。Accountスキーマを共有するため同じBoltで束ねる)/ **Bolt 6**=dynamic-data-access(残り: 新規作成・更新・FK検索・後勝ち制御)/ **Bolt 7**=notification(全機能)/ **Bolt 8**=frontend-core(残り: 自己サービス画面・ロール切替UI)/ **Bolt 9**=frontend-admin(全機能)/ **Bolt 10**=packaging(仕上げ: CI連携を見据えた本格的なビルド配線)
- B. 異なる束ね方を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q5. Bolt間の並行実行は可能か

開発者が一人(AI駆動、team-formationはスコープ上SKIP)であるため、実際の着手は基本的に順次になりますが、複数Unitを同時にAIが並行して構築する運用(自律的なConstructionスワーム機構)を使うかどうかを確認します。

- A. 各Boltは順次(1つずつ)実行する。Bolt完了ごとにユーザーが確認できる方が、開発者一人体制での状況把握がしやすい(推奨)
- B. 依存関係のないUnit同士(例: レベル0のaudit-log・permission)は並行して構築してよい
- X. Other (please specify)

[Answer]: A

## Q6. チーム外の要因で待たされるものはあるか

各Boltを妨げうる外部要因を特定します。

- A. 以下の2点を外部依存として記録する(推奨): **(1) 3種RDBMS(PostgreSQL/MySQL/MariaDB)の動作確認用インスタンス**: schema-ingestion(Bolt 1)の実装・特性テスト(team.mdのTesting Posture前倒し方針)には、実際に接続確認できる3種のRDBMSインスタンスが必要。開発者自身が用意する想定だが、用意が遅れるとBolt 1がブロックされる。**(2) 未公開の自作ライブラリ2件(`make-you-chic-ui`、`java-mustache-processor`)の取り込み方式**: practices-discoveryから持ち越された未確定事項(gitサブモジュール等)。前者はfrontend-core(Bolt 1・8)を、後者はnotification(Bolt 7)をブロックしうる
- B. 他にも外部要因がある(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q7. 最も懸念している点(早期に着手して確認したいこと)

- A. スキーマ読み込み層(U1)におけるRDBMS間の型差異・複合主キー・主キーなしテーブル・ビューの扱いの差異。team.mdのTesting Postureで前倒しの特性テスト運用が既に確定しており、Bolt 1で最初に着手する対象と一致するため、追加の懸念事項はない(推奨)
- B. 他に懸念している点がある(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Bolt 1(Walking Skeleton)は最小限の認証(ログイン+isAdminクレームJWT)を含む、U1(全機能)+U2(最小)+U4(最小)+U5(最小)+U9(最小)+U11(最小配線)の6 Unit構成
- Bolt 2以降は依存レベル順(土台を先に)に並べ、正式なWSJFスコアリングは行わない
- 具体的な束ね方: Bolt2=audit-log/ Bolt3=permission/ Bolt4=config-management残り/ Bolt5=auth残り+account-management(共有スキーマのため同一Bolt)/ Bolt6=dynamic-data-access残り/ Bolt7=notification/ Bolt8=frontend-core残り/ Bolt9=frontend-admin/ Bolt10=packaging仕上げ
- 各Boltは順次実行(並行構築は行わない)
- 外部依存: 3種RDBMS(PostgreSQL/MySQL/MariaDB)の動作確認環境、未公開ライブラリ2件(make-you-chic-ui・java-mustache-processor)の取り込み方式未定
- 最大の懸念事項: スキーマ読み込み層のRDBMS間差異(既にteam.mdで前倒し特性テスト運用が確定済み)

- Looks correct
- Request changes

[Answer]: Looks correct
