# Feasibility & Constraint Analysis — Questions

Grounded in `intent-statement.md`(Q1〜Q13)。市場調査(market-research)はスコープ判断により未実施のため、競合分析・市場動向・build-vs-buyの入力はなし。

## Q1. 統合が必要な既存システムはありますか?(最初に接続する具体的なDB・テーブルはすでに決まっていますか、それともまずテスト用スキーマで検証を始めますか)

A. すでに自分が実運用している特定の業務DB・テーブル群がある
B. まずはテスト用・サンプル用のスキーマで検証を始める
C. 既存のMasterMeisterが接続していたDBと同じものを流用する
D. Not yet defined
X. Other (please specify)

[Answer]: B — まずはテスト用スキーマで検証する。家電ECサイト・ポイント管理・蔵書管理という3つの異なる業務ドメインのダミースキーマ+ダミーデータをAIに作ってもらい、使い勝手を確認する。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q2. MasterSmith自体の実装言語・技術スタックはどう考えていますか?(MasterMeisterと同じ言語基盤を使う想定ですか)

A. MasterMeisterと同じ言語・フレームワークを使う(JDBC前提のため恐らくJava系)
B. 異なる言語・フレームワークを新たに採用する
C. Not yet defined
X. Other (please specify)

[Answer]: X. Other — バックエンドはJava 25 / Spring Boot / Gradle。フロントエンドはReact(TSX)によるSPA(自作デザインシステムmake-you-chic-uiを活用)。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q3. MasterMeisterの既存コード(特にJDBC DatabaseMetaDataによるスキーマ読み込み部分)を再利用する想定ですか、それとも新規に書き起こしますか?

A. スキーマ読み込み部分は既存コードをベースに再利用・改修する
B. 設計思想が異なるため、新規に書き起こす
C. Not yet defined
X. Other (please specify)

[Answer]: B — 新規に書き起こす。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q4. 「同じアプリで異なるテーブル構成の複数業務に使える」という成功像について、実行時の形はどちらに近いですか?

A. 1つの実行インスタンス(1プロセス)が複数のDB接続・設定を同時に切り替えて扱う(マルチテナント的な単一デプロイ)
B. 業務ごとに個別の設定を投入して個別にデプロイ・起動する(1インスタンス=1業務、コードとアプリケーションは共通)
C. Not yet defined(どちらもあり得ると考えている)
X. Other (please specify)

[Answer]: B — 実行インスタンスは業務ごとに分ける(1インスタンス=1業務)。コード・実行モジュールは共通。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q5. 対象RDBMS(PostgreSQL/MySQL/MariaDB)にまたがってDatabaseMetaDataで扱いたい、あるいは除外したいスキーマ要素はありますか?(複合主キー、ビュー、ストアドプロシージャ、DB固有の独自型など)

A. 単純な単一主キーのテーブルのみを主対象とし、ビュー・ストアドプロシージャ等は当面除外する
B. 複合主キーやビューなど、ある程度複雑な構造にも対応したい
C. Not yet defined
X. Other (please specify)

[Answer]: X. Other — 複数カラム(複合)主キーのテーブルも対象としたい。主キーのないテーブルはUPDATE/DELETE対象外(表示・検索は可)。ビューは表示専用でサポート(更新不可)。ストアドプロシージャは除外。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q6. 規制・コンプライアンス要件はありますか?(業務データに個人情報等の機微な情報が含まれる可能性はありますか。PCI-DSS/HIPAA等は関係ないと考えてよいですか)

A. 個人利用の範囲であり、PCI-DSS/HIPAA等の規制は関係ない
B. 業務データに個人情報を含む可能性があり、多少の配慮は必要
C. Not yet defined
X. Other (please specify)

[Answer]: A — 個人利用の範囲であり、PCI-DSS/HIPAA等の規制は関係ない。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q7. シンプルなログイン機能(ID/パスワード認証、Q13で確定)について、パスワードの扱いやセッション管理に関する希望・制約はありますか?

A. 一般的なベストプラクティス(ハッシュ化・適切なセッション管理)に従ってもらえればよく、特別な指定はない
B. 特定のライブラリ・方式を使いたい
C. Not yet defined
X. Other (please specify)

[Answer]: X. Other — バックエンドはステートレスにしたいため、セッションではなくアクセストークン方式を採用する。具体的なトークン形式・ライブラリ選定は後続フェーズ(NFR設計等)で検討する。パスワードのハッシュ化は一般的なベストプラクティスに従う。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q8. 権限定義(テーブル単位・操作単位、Q12で確定)について、MasterMeisterや他のプロジェクトで使った権限モデルの前例はありますか?

A. 前例はなく、新規に設計する
B. MasterMeisterや他プロジェクトに参考にしたい権限モデルがある
C. Not yet defined
X. Other (please specify)

[Answer]: B — MasterMeisterの権限モデルを参考にしたい。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q9. MasterSmithを実際にどこで動かす想定ですか?(現時点でのイメージで構いません)

A. 自分のPC・自宅サーバ上で動かす
B. クラウド上の仮想マシン・コンテナで動かす
C. Not yet defined(この段階では未定)
X. Other (please specify)

[Answer]: A — 自宅サーバで動かす。アプリケーション構成はTwelve-Factor Appの原則に従いたい(設定の外部化・ステートレスプロセス等)。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q10. 組織的な障害・競合する優先事項(他の仕事や他プロジェクトとの兼ね合いなど)はありますか?

A. 個人の趣味プロジェクトのため、特に組織的な制約はない
B. 他の仕事・プロジェクトとの兼ね合いで作業時間が制約される
C. Not applicable
X. Other (please specify)

[Answer]: A — 個人の趣味プロジェクトのため、特に組織的な制約はない。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q11. (follow-up) MPA(サーバーサイドレンダリング)かSPAか、という相談の結論を確認します。

[Answer]: SPA — Spring Boot(Java 25 / Gradle)によるREST APIバックエンドと、React(TSX)によるSPAフロントエンドという構成に確定。フロントエンドは自作デザインシステム make-you-chic-ui (https://github.com/agwlvssainokuni/make-you-chic-ui) を活用する。同リポジトリは必要なフォーム部品(TextInput/Textarea/Select/Checkbox/Switch/RadioGroup)・Table部品・List View/Detail Viewの参考実装・4軸テーマ機能(ライト/ダーク、ブランドカラー、フォント、文字サイズ)を備えるが、プロトタイプ段階でnpm未公開のため取り込み方式(gitサブモジュール等)は後続フェーズで検討が必要。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q12. (follow-up) ビルド・パッケージングの方式を確認します。

[Answer]: フロントエンドとバックエンドは別々に開発するが、ビルド成果物としては単一の実行可能WARにパッケージングする。フロントエンドのビルド成果物をバックエンドの静的コンテンツとして取り込む形。認証はステートレスなアクセストークン方式(セッションではない)。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q13. (follow-up) 可観測性(オブザーバビリティ)・ログについて確認します。

[Answer]: OTel(OpenTelemetry)に設定だけで対応できるように備えておきたい(実装は後続フェーズだが、対応可能な構成にしておく)。ログは構造化ログとする。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q14. (follow-up) フロントエンド/バックエンドの開発時・実行時のサーバ構成を確認します。

[Answer]: 開発時: フロントエンド(Vite)のdevサーバのproxy設定でバックエンドAPIへ転送する。実行時: ビルドした単一の実行可能WARが、フロントエンドの静的コンテンツ配信とAPI提供の両方を兼ねる。APIはSPA配布元と同一オリジンへのリクエストになるため、本番実行時にCORS対応は不要。バックエンドがSPAの静的コンテンツを配信する際、SPA側のクライアントサイドルーティングに対応するため、該当しないパスは `/index.html` へフォールバックさせる。
**Timestamp:** 2026-09-04T14:09:52Z
**Mode:** chat

## Q15. (follow-up) 内部DBの位置づけを確認します。

[Answer]: 接続先の業務DB(PostgreSQL/MySQL/MariaDB)とは別に、MasterSmith自身の内部DBとしてH2を使用する。内部DBには利用者アカウント・権限設定に加えて、設定全体(DB接続先設定・メニュー構成・検索条件・一覧表示項目・編集対象外項目・バリデーション・フォーム部品・論理表示名の9項目すべて)を保持する。H2はローカルファイルシステムへの永続化・サーバモード運用・インメモリ(テスト用)のいずれにも対応できるため採用。
**Timestamp:** 2026-09-04T14:26:44Z
**Mode:** chat

## Q16. (follow-up) 設定をDBに保持することと「静的設定駆動」という設計思想の両立方法を確認します。

[Answer]: 設定はキャッシュを介して静的に扱う。起動時(または初回アクセス時)にDBから設定を読み込みキャッシュし、以降はリクエストのたびにDBを見に行かない。明示的なキャッシュクリア操作、またはキャッシュ設定のexpireによってのみ再読み込みされる。
**Timestamp:** 2026-09-04T14:26:44Z
**Mode:** chat

## Q17. (follow-up) 設定のバックアップ・可搬性について確認します。

[Answer]: 内部DB(H2)に格納した「設定」は、設定ファイルとしてエクスポートできるようにする。また、エクスポートした設定ファイルをインポートして設定を復元できるようにする。
**Timestamp:** 2026-09-04T14:28:38Z
**Mode:** chat

## Q18. (follow-up) メール送信・テンプレートエンジンについて確認します。

[Answer]: システムからメールを送る場合、文面テンプレートは自作のMustacheエンジン java-mustache-processor (https://github.com/agwlvssainokuni/java-mustache-processor) を使う。HTML形式とし、`<title>`要素をメールのSubjectとする。同リポジトリもMaven Central等未公開のプロトタイプ段階。

想定するメール送信場面(いずれもログイン機能(Q13)の具体化):
- アカウント作成通知: 管理者がアカウントを作成するとメールが送られ、記載されたURLへアクセスして氏名・パスワードを設定するとアカウント登録が完了する
- アカウント登録完了通知
- アカウント情報変更通知(氏名・パスワード・メールアドレスの変更)
- パスワード変更通知
- パスワード忘れ対応(リセットURLの送付等)
- メールアドレス変更リクエスト: 新アドレス宛にメールが送られ、記載されたURLへアクセスして現在のパスワードを入力するとアドレス変更が完了する

これはQ13で確定した「シンプルなログイン機能」の範囲をかなり具体化・拡張するものであり、MVPに含めるべき範囲かどうかは次のScope Definitionステージで改めて確認する。
**Timestamp:** 2026-09-04T14:40:55Z
**Mode:** chat

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
