# Bolt Plan: MasterSmith MVP

**Bolt**とは、Construction段階で1回分のビルドサイクルとして扱う、1つ以上のUnitのまとまりを指す(1つのUnitを完全に作ることもあれば、複数Unitを束ねることもある)。全10 Boltで、units-generationが定めた11 Unit(unit-of-work.md)をすべてカバーする。開発者一人(AI駆動)のため、Boltは順次実行する(delivery-planning-questions.md Q5)。

## Bolt 1: Walking Skeleton(業務DBスキーマ読み込み→内部H2設定保存→動的画面生成の貫通)

**Walking Skeleton** — 単独実行・ゲート付きで、ユーザーの明示的な承認を経てから残りのBoltへ進む最初のBolt(team.md、org.md参照)。アーキテクチャ上の最大の不確実性(複数レイヤーを貫通する骨格が実際に成立するか)を最初に検証する。

- **含まれるUnit**:
  - U1 schema-ingestion(全機能)
  - U2 config-management(最小: DB接続先設定のCRUD+スキーマ取り込み結果をTableConfigの初期値として保存するのみ。メニュー構成・検索条件・一覧表示項目・編集対象外項目・バリデーション・フォーム部品・論理表示名・エクスポート/インポート・キャッシュ管理はBolt 4)
  - U4 dynamic-data-access(最小: 一覧・詳細画面の表示のみ。新規作成・更新・FK検索・後勝ち制御はBolt 6)
  - U5 auth(最小: ログイン+isAdminクレームを含むアクセストークン発行のみ。リフレッシュ・パスワード忘れ・登録完了・自己サービス変更・ログイン試行制限はBolt 5)
  - U9 frontend-core(最小: ログイン画面+業務データ一覧・詳細画面のみ。自己サービス画面・ロール切替UIはBolt 8)
  - U11 packaging(最小: フロントエンドのビルド成果物を単一実行可能WARへ組み込み、`./gradlew build`で実行できる最小配線。CI連携を見据えた本格的な配線はBolt 10)
- **Definition of Done**:
  - スキーマ読み込み層の特性テスト(PostgreSQL/MySQL/MariaDBそれぞれの複合主キー構成・主キーなしテーブルの扱い・ビューの読み取り専用扱いの差異吸収)が実装に先立って洗い出され、テストとして存在する(team.md Testing Posture前倒し方針)
  - 管理者としてログインし、業務DB接続先を設定し、スキーマ取り込みを実行し、取り込んだテーブルのうち少なくとも1件の一覧・詳細画面が実際に表示されることを、3つのダミースキーマ(家電ECサイト・ポイント管理・蔵書管理)のいずれか1つで確認できる
  - `./gradlew build`で単一WARが生成され、そのWARを起動して上記の一連の操作がブラウザから実行できる
- **Confidence Hypothesis**: 業務DBのスキーマ探索→内部H2への設定保存→動的な一覧・詳細画面生成という複数レイヤーを貫通する骨格が、実際のログイン・管理者ゲーティングを経た状態で一気通貫して動作する。
- **Expected Demo**: 管理者としてログイン→DB接続先設定→スキーマ取り込み実行→取り込まれたテーブル(例: 蔵書管理の「書籍」テーブル)の一覧・詳細画面が表示される、という一連の流れをブラウザ操作で実演する。

## Bolt 2: audit-log(全機能)

- **含まれるUnit**: U8 audit-log(全機能)
- **Definition of Done**: FR7.1〜FR7.5(記録受付・条件絞り込み参照・エクスポート・保持期間超過分削除)が実装され、既存の自動テストスイートが通り、スコープの80%ラインカバレッジ floor を満たす
- **Confidence Hypothesis**: 監査ログの記録受付・参照・エクスポート・削除が単体で正しく動作する(Bolt 4〜6で各Unitからのイベント発行元が実際に接続されるまでは、記録はAPI直接投入で検証する)。
- **Expected Demo**: 監査ログAPIへ直接いくつかの記録を投入し、条件絞り込み・エクスポート・保持期間超過分の削除が動作することを確認する。

## Bolt 3: permission(全機能)

- **含まれるUnit**: U3 permission(全機能)
- **Definition of Done**: FR5.1〜FR5.4(ロール・グループ定義、テーブル/カラム単位権限、ロール割り当てと切り替え、自身のロール一覧取得API)が実装され、既存の自動テストスイートが通る
- **Confidence Hypothesis**: ロール・グループ・テーブル/カラム権限のCRUDと、自身に割り当てられたロール一覧取得APIが単体で正しく動作する(dynamic-data-access(Bolt 6)からの実際の権限確認呼び出しはBolt 6で接続される)。
- **Expected Demo**: 管理者がロールを作成し、テーブル単位・カラム単位の権限を設定し、ユーザへ割り当てる操作を一通り実演する。

## Bolt 4: config-management(残り全機能)

- **含まれるUnit**: U2 config-management(Bolt 1で未実装のメニュー構成・検索条件・一覧表示項目・編集対象外項目・バリデーション・フォーム部品・論理表示名・エクスポート/インポート・キャッシュ管理)
- **Definition of Done**: FR2.1〜FR2.6・FR2.3.1・FR3.4(MenuItem)がすべて実装され、監査ログ(Bolt 2)への設定変更記録が実際に接続される
- **Confidence Hypothesis**: 設定管理のフルCRUD・エクスポート/インポート(不整合時の全体拒否含む)・明示的キャッシュクリアが、静的設定駆動の原則(FR2.6)を満たしたまま正しく動作する。
- **Expected Demo**: 設定管理画面の各タブ(基本・メニュー・検索条件・一覧表示・編集対象外・バリデーション・フォーム部品・論理表示名)を編集・保存し、エクスポート→インポートが往復できることを確認する。

## Bolt 5: auth(残り全機能)+ account-management(全機能)

authとaccount-managementはAccountエンティティの永続化スキーマを共有する(contract-summary.md #4・#19)ため、同じBoltで束ねる。

- **含まれるUnit**: U5 auth(Bolt 1で未実装のリフレッシュトークン、パスワード忘れ・登録完了・自己サービスでの氏名/パスワード/メールアドレス変更、ログイン試行回数制限)、U6 account-management(全機能)
- **Definition of Done**: FR6.1〜FR6.6・FR6.4.1〜FR6.4.4が実装され、監査ログ(Bolt 2)への記録が実際に接続される。この時点でaccount-management/authが発行するアカウントライフサイクルイベント(contract-summary.md #9・#10)はpublishされるが、購読側のnotification(Bolt 7)がまだ存在しないため、実際のメール送信は行われない(イベント発行と購読の疎結合設計により、後からBolt 7で接続しても両者に変更は不要)
- **Confidence Hypothesis**: authとaccount-managementが共有スキーマ経由の呼び出し(contract #4)で正しく連携し、パスワード忘れ・登録完了・自己サービス変更・ログイン試行制限・管理者によるアカウント管理(新規作成・一覧・編集・無効化)が動作する。
- **Expected Demo**: 管理者がアカウントを新規作成し(通知メールはまだ届かない旨を明示した上で)、対象者が発行されたトークンで登録完了・パスワード忘れの一連の操作ができることを確認する。

## Bolt 6: dynamic-data-access(残り全機能)

- **含まれるUnit**: U4 dynamic-data-access(Bolt 1で未実装の新規作成・更新・FK参照ポップアップ検索・後勝ちの同時編集制御)
- **Definition of Done**: FR3.3・FR3.5・FR4.2が実装され、permission(Bolt 3)への権限確認呼び出し・監査ログ(Bolt 2)への記録が実際に接続される
- **Confidence Hypothesis**: 設定駆動の新規作成・更新・FK参照ポップアップ検索が、権限確認(permission)・監査ログ記録と統合して正しく動作する。
- **Expected Demo**: 一覧・詳細に加え、新規作成・更新・FK入力欄からのポップアップ検索が動作することを確認する。

## Bolt 7: notification(全機能)

- **含まれるUnit**: U7 notification(全機能)
- **Definition of Done**: FR6.4・FR6.5が実装され、auth・account-management(Bolt 5)が発行する6種のライフサイクルイベントすべてを実際に購読し、Mustacheテンプレートで描画したメールが送信される
- **Confidence Hypothesis**: Bolt 5で発行されているだけだったライフサイクルイベントが、実際に購読・メール送信まで一気通貫する。
- **Expected Demo**: 管理者がアカウントを新規作成すると、実際に通知メールが届き、記載URLから登録完了操作ができることを確認する。

## Bolt 8: frontend-core(残り全機能)

- **含まれるUnit**: U9 frontend-core(Bolt 1で未実装の自己サービス画面群(パスワード忘れ・登録完了・情報変更・メールアドレス変更確認)、複数ロール保有時のロール切替UI)
- **Definition of Done**: FR5.4(ロール切替)・FR6.1〜FR6.3・FR6.6の残り画面が実装され、Bolt 5の自己サービスAPI・Bolt 3のロール一覧取得APIと実際に接続される
- **Confidence Hypothesis**: 自己サービスのバックエンドAPIとロール切替APIが、実際のUIから問題なく使える。
- **Expected Demo**: 利用者が自己サービス画面から氏名・パスワード・メールアドレスを変更し、複数ロールを保有する利用者がTopbarからロールを切り替えると業務データ画面の見え方(権限)が変わることを確認する。

## Bolt 9: frontend-admin(全機能)

- **含まれるUnit**: U10 frontend-admin(全機能)
- **Definition of Done**: 設定管理・スキーマ取り込み・権限管理(ロール/グループ)・アカウント管理・監査ログのすべての管理画面が実装され、対応するバックエンドAPI(Bolt 1〜7で揃っている)と実際に接続される
- **Confidence Hypothesis**: Bolt 1〜7で揃った全ての管理者専用バックエンドAPIが、管理画面から一通り操作できる。
- **Expected Demo**: 管理者が設定管理・スキーマ取り込み・権限管理・アカウント管理・監査ログの各画面を一通り操作する。

## Bolt 10: packaging(仕上げ)

- **含まれるUnit**: U11 packaging(Bolt 1の最小配線を、CI連携を見据えた本格的なビルド配線へ仕上げる。具体的なCI/CDプラットフォームの選定自体はConstruction段階のCI Pipelineステージで別途確定する)
- **Definition of Done**: `./gradlew build`一発で、frontend/配下の最新のフロントエンド変更を含む単一実行可能WARが再現性高く生成される。MVPの成功指標(3つのダミースキーマすべてでコード変更なし・設定変更のみで一覧/詳細/編集画面が生成できること)を、生成されたWARで確認する
- **Confidence Hypothesis**: 二言語モノレポの構成のまま、単一のビルドコマンドで一貫した単一デプロイ可能物を再現性高く生成できる。
- **Expected Demo**: クリーンな環境から`./gradlew build`を実行し、生成されたWARを起動して、3つのダミースキーマすべてでMVPの成功指標を満たすことを確認する。
