# Intent Backlog: MasterSmith MVP

優先順位はMoSCoWで整理し、着手順(Q6)・進め方(Q7: リスク先行)を反映した並びとした。すべてMVPのMust-haveとして確認済み [Q2][Q3][Q4][Q5(scope-definition)]。

## Proto-Units (Must Have)

| # | Proto-Unit | 概要 | 依存関係 | MoSCoW | Source |
|---|---|---|---|---|---|
| PU-01 | スキーマ読み込みエンジン | JDBC DatabaseMetaDataによるテーブル/カラム/型/PK(複合主キー含む)・FK/制約の取得。PostgreSQL/MySQL/MariaDBの3種にまたがる差異を早期に検証(ビューは表示専用として認識、ストアドプロシージャは対象外) | なし(最初に着手) | Must | [Q6][Q7] |
| PU-02 | 内部設定・アカウント・権限ストア | 内部DB(H2)に設定全体(9項目)・アカウント・権限を保持。キャッシュ機構による静的読み込み。設定のエクスポート/インポート | PU-01(読み込んだスキーマ情報を設定として保存する) | Must | [Q6] |
| PU-03 | メニュー・ナビゲーション | 設定に基づくメニュー構成・画面遷移 | PU-02 | Must | intent-statement.md |
| PU-04 | 一覧画面(検索+一覧表示) | 検索フォーム・一覧テーブル・ページング・ソート | PU-02, PU-03 | Must | [Q1(scope-definition)] |
| PU-05 | 詳細画面 | 全カラム表示(編集対象外項目含む) | PU-02 | Must | [Q1(scope-definition)] |
| PU-06 | 編集(新規/更新)画面 | 編集対象外項目を除いた入力フォーム、フォーム部品(text/textarea/checkbox/switch/radio/select)、バリデーション | PU-02, PU-05 | Must | [Q1(scope-definition)] |
| PU-07 | 権限定義・適用 | テーブル単位・操作単位の権限定義(MasterMeisterの権限モデルを参考)。PU-04〜PU-06の操作に適用 | PU-02, PU-08 | Must | [Q2(scope-definition)] |
| PU-08 | 認証・アカウントライフサイクル | シンプルなログイン(アクセストークン方式)、アカウント作成通知・登録完了通知・アカウント情報変更通知・パスワード変更通知・パスワード忘れ対応・メールアドレス変更リクエストの6メールフロー(java-mustache-processorによるHTMLテンプレート) | PU-02 | Must | [Q3(scope-definition)] |

## Value Stream Map(概要)

```
[スキーマ読み込み(PU-01)]
        |
        v
[内部設定・アカウント・権限ストア(PU-02)] --> [認証・アカウントライフサイクル(PU-08)]
        |                                              |
        v                                              v
[メニュー(PU-03)]                              [権限定義・適用(PU-07)]
        |                                              |
        v                                              |
[一覧(PU-04)] --> [詳細(PU-05)] --> [編集(PU-06)] <----+
```
<!-- Text fallback: スキーマ読み込み(PU-01)を起点に、内部設定・アカウント・権限ストア(PU-02)へ。そこから認証・アカウントライフサイクル(PU-08)と、メニュー(PU-03)経由で一覧(PU-04)・詳細(PU-05)・編集(PU-06)へ分岐する。権限定義・適用(PU-07)はPU-02とPU-08に依存し、一覧・詳細・編集の各操作に適用される。 -->

## Assumptions & Open Questions

None.
