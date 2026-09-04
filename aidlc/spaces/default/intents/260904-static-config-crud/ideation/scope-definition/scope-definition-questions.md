# Scope Definition & Prioritization — Questions

Grounded in `intent-statement.md`・`feasibility-assessment.md`・`constraint-register.md`。技術的な実現可能性(何をサポートできるか)はFeasibilityで確認済み。ここでは「MVPとして最初に何を届けるか」という優先順位・範囲(in/out)を確認する。

## Q1. MVPとして最初に届ける最小価値は何ですか?(これができれば「使える」と言える最小の状態)

A. 1つの業務ドメイン(例: 蔵書管理)で、一覧・詳細・編集画面が設定投入だけで動くこと
B. 3つのテスト業務ドメイン(家電EC・ポイント管理・蔵書管理)すべてで一覧・詳細・編集画面が動くこと(「異なる業務でも個別開発したように見える」ことまで確認したいため)
C. Not yet defined
X. Other (please specify)

[Answer]: B — 3つのテスト業務ドメインすべてで一覧・詳細・編集画面が動くところまでを最初のMVPとする。
**Timestamp:** 2026-09-04T15:19:22Z
**Mode:** chat

## Q2. 権限機能(テーブル単位・操作単位の権限定義)は、MVPのMust-haveですか、それとも後回しにできるShould-have/Could-haveですか?

A. Must-have — MVPに必須(feasibilityで手戻りを避けるために先に組み込むと決めた経緯があるため)
B. Should-have — 一覧・詳細・編集画面が動いた後で組み込みたい
C. Not yet defined
X. Other (please specify)

[Answer]: A — Must-have。
**Timestamp:** 2026-09-04T15:19:22Z
**Mode:** chat

## Q3. アカウントライフサイクルのメールフロー(アカウント作成通知・登録完了・情報変更通知・パスワード変更通知・パスワード忘れ対応・メールアドレス変更リクエストの6種類)は、すべてMVPのMust-haveですか、それとも一部に絞りますか?

A. 6種類すべてMust-have
B. 一部(例: アカウント作成通知・パスワード忘れ対応)のみMust-have、残りはShould-have/Could-have
C. Not yet defined
X. Other (please specify)

[Answer]: A — 6種類すべてMust-have。
**Timestamp:** 2026-09-04T15:19:22Z
**Mode:** chat

## Q4. 設定のエクスポート/インポート機能は、MVPのMust-haveですか?

A. Must-have — 最初から必要
B. Should-have — 一覧・詳細・編集画面が動いた後でよい
C. Not yet defined
X. Other (please specify)

[Answer]: A — Must-have、最初のMVPに含める。
**Timestamp:** 2026-09-04T15:19:22Z
**Mode:** chat

## Q5. スキーマ対応範囲(複合主キー対応・ビューの表示専用サポート)は、MVPの最初のバージョンから必要ですか、それとも単純な単一主キーのテーブルだけで動くものを先に届け、複合主キー・ビューは後続で追加しますか?

A. 複合主キー・ビュー対応も含めてMVPに含める
B. まず単一主キーのテーブルのみで動くものを届け、複合主キー・ビュー対応は後続に回す
C. Not yet defined
X. Other (please specify)

[Answer]: A — 複合主キー・ビュー対応も最初のMVPに含める。
**Timestamp:** 2026-09-04T15:19:22Z
**Mode:** chat

## Q6. 機能間の依存関係を踏まえると、最初に着手すべきはどこだと考えていますか?

A. スキーマ読み込み→設定(内部H2 DB)→一覧画面、という順で積み上げるのが自然
B. 権限・ログインまわりを先に固めてから、CRUD画面に進みたい
C. Not yet defined(後続のUnits Generation/Delivery Planningで機械的に決めてよい)
X. Other (please specify)

[Answer]: A — スキーマ読み込み→設定(内部H2 DB)→一覧画面、という順で積み上げる。
**Timestamp:** 2026-09-04T15:19:22Z
**Mode:** chat

## Q7. 進め方(シーケンシング)の好みはありますか?

A. リスクの高いところ(スキーマ読み込みの3RDBMS差異など)から早めに着手したい
B. まず動く価値(一覧・詳細・編集画面)を早く届けたい
C. Not yet defined(後続のDelivery Planningで決めてよい)
X. Other (please specify)

[Answer]: A — リスクの高いところ(3種類のRDBMS間でのスキーマ読み込みの差異など)から早めに着手する。
**Timestamp:** 2026-09-04T15:19:22Z
**Mode:** chat

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
