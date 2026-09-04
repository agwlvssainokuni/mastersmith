# Constraint Register: MasterSmith

## Technical Constraints

| ID | Constraint | Detail | Source |
|---|---|---|---|
| TC-01 | 対象RDBMS | PostgreSQL / MySQL / MariaDB。JDBCドライバはアプリケーションに内包 | [Q9(intent-capture)] |
| TC-02 | スキーマ対応範囲 | 複合主キーのテーブルも対象。主キーなしテーブルはUPDATE/DELETE対象外(表示・検索のみ)。ビューは表示専用。ストアドプロシージャは対象外 | [Q5] |
| TC-03 | スキーマ読み込みコード | MasterMeisterのコードは再利用せず新規に書き起こす | [Q3] |
| TC-04 | バックエンド技術スタック | Java 25 / Spring Boot / Gradle | [Q2] |
| TC-05 | フロントエンド技術スタック | React(TSX)のSPA。自作デザインシステム make-you-chic-ui を利用(プロトタイプ段階、npm未公開) | [Q2][Q11] |
| TC-06 | 認証方式 | ステートレスなアクセストークン方式(セッションではない) | [Q7][Q12] |
| TC-07 | ビルド・パッケージング | フロントエンド/バックエンドは別々に開発し、単一の実行可能WARにパッケージング。フロントエンドのビルド成果物はバックエンドの静的コンテンツとして取り込む | [Q12] |
| TC-08 | 開発時・実行時のサーバ構成 | 開発時はViteのdevサーバproxyでバックエンドAPIへ転送。実行時は単一WARがSPA配信とAPI提供を兼ね同一オリジンとなるためCORS不要。SPAのクライアントサイドルーティング対応のため未一致パスは`/index.html`へフォールバック | [Q14] |
| TC-09 | 可観測性・ログ | OTelに設定だけで対応できる構成。ログは構造化ログ | [Q13] |
| TC-10 | アプリケーション構成原則 | Twelve-Factor Appの原則に従う | [Q9] |
| TC-11 | 実行インスタンスの単位 | 業務ごとに個別インスタンス(1インスタンス=1業務)。コード・実行モジュールは共通 | [Q4] |
| TC-12 | 実行環境 | 自宅サーバ | [Q9] |
| TC-13 | 内部データストア | 業務DBとは別に、MasterSmith自身の内部DBとしてH2を使用。利用者アカウント・権限・設定全体(9項目)を保持 | [Q15] |
| TC-14 | 設定の静的性の担保 | 設定はキャッシュ経由で扱う。起動時読み込み+キャッシュ、明示的クリアまたはexpireでのみ再読み込み | [Q16] |
| TC-15 | 設定の可搬性 | DB格納の設定は設定ファイルとしてエクスポート/インポート可能とする | [Q17] |
| TC-16 | 権限モデルの参照元 | MasterMeisterの権限モデルを参考にする | [Q8] |
| TC-17 | メールテンプレートエンジン | 自作Mustacheエンジン java-mustache-processor を使用。HTML形式、`<title>`要素をSubjectとする | [Q18] |
| TC-18 | メール送信フロー | アカウント作成通知・登録完了通知・アカウント情報変更通知・パスワード変更通知・パスワード忘れ対応・メールアドレス変更リクエストの各メールを送信する | [Q18] |

## Organizational Constraints

| ID | Constraint | Detail | Source |
|---|---|---|---|
| OC-01 | 開発体制 | 開発者一人がすべて(企画・実装・利用)を担う | [Q6(intent-capture)] |
| OC-02 | 期限 | 明確な締切なし。趣味プロジェクトとしてじっくり進める | [Q10(intent-capture)] |
| OC-03 | 組織的障害 | 個人プロジェクトのため組織的な制約はない | [Q10] |

## Regulatory Constraints

| ID | Constraint | Detail | Source |
|---|---|---|---|
| RC-01 | 適用規制 | PCI-DSS/HIPAA等は関係ない(個人利用の範囲) | [Q6] |

## Assumptions & Open Questions

None.
