# エビデンス: MasterSmith Practices Discovery

> **ステータス: FINAL** — Lead Draft、独立レビュー(品質・開発者・セキュリティ)、
> 人間ヒアリングを経て確定した調査記録。

## 参照したソース

1. `aidlc/spaces/default/memory/org.md`
   — Way of Working / Walking Skeleton / Testing Posture / Deployment / Code Style
   の5セクション。本プロジェクトのteam.mdは全セクション未affirmed(空テンプレート)
   のため、これらは「確立済みのチーム事実」ではなく「提案されたデフォルト」として
   扱った。
2. `aidlc/spaces/default/intents/260904-static-config-crud/ideation/feasibility/feasibility-assessment.md`
   — 確定済み技術スタック(Java 25/Spring Boot/Gradle バックエンド、React/TSX
   フロントエンド、内部H2データストア、Twelve-Factor App原則、開発者一人体制、
   自宅サーバでの実行)。
3. `aidlc/spaces/default/intents/260904-static-config-crud/ideation/feasibility/constraint-register.md`
   — TC-01〜TC-18(技術的制約)、OC-01〜OC-03(組織的制約)、RC-01(規制制約)の
   構造化された制約一覧。

## 推論(org.mdデフォルト → プロジェクト適応)

| org.mdの項目 | 適応の要否 | 判断根拠 |
|---|---|---|
| トランク・ベース開発/squash-merge | 変更なしで踏襲 | 開発者一人体制(OC-01)でも短命ブランチ運用自体に支障はない |
| Walking Skeleton | `skeleton: on` を提案 | 業務DBスキーマ読み込み→内部H2設定保存→動的画面生成という複数レイヤー貫通の骨格に、feasibility-assessment.mdが「リスク」として明記する不確実性(RDBMS間差異、静的性とDB保持の両立)が存在する |
| Testing Posture(test-after) | ordering自体は踏襲、RDBMS差異への言及を追加提案 | raid-log.mdでJDBC DatabaseMetaData取得結果の3RDBMS間差異がリスクとして挙げられている(feasibility-assessment.md 51-54行目) |
| Deployment(ステージング自動+本番手動承認) | 環境分離の前提を見直す提案 | 実行環境が自宅サーバ一台のみ(TC-12)であり、CI/CDプラットフォームが未決定。org.mdの「ステージング/本番の分離」がそのまま当てはまるかは確認が必要 |
| Code Style | フロントエンドのみ具体化(Prettier+ESLint) | React/TSXは言語デフォルトが明確。バックエンド(Java)側のツール選定は未決定情報のため据え置き |

## 独立レビュー(品質・開発者・セキュリティ)で調査・提案された事項

- **品質担当**: test-after継続には同意しつつ、スキーマ読み込み層(JDBC
  `DatabaseMetaData`、PostgreSQL/MySQL/MariaDBの3種)についてのみ、RDBMSごとの
  期待挙動(複合主キー、主キーなしテーブル、ビューの読み取り専用扱い)を先に
  特性テストとして洗い出してから実装を固める前倒し運用を提案。あわせて、CI基盤が
  未決定でも「push/PR時のlint+単体テスト実行・失敗時マージブロック」という
  最小ゲートの要否は先に合意すべきとし、Testcontainers等によるマトリクス結合
  テストはCI環境でのDocker実行可否が確定してから条件付きで追加する方針を提案。
- **開発者担当**: 二言語モノレポ(Gradleバックエンド+`frontend/`配下の独立した
  Viteプロジェクト)をGradleの`bootWar`ライフサイクルへ組み込み単一WARを生成する
  ビルド配線、機能単位(スキーマ読み込み/内部設定管理/認証・権限/動的画面生成)
  でのパッケージ構成、内部H2の識別子命名規約(`ms_`接頭辞)、未公開ライブラリ2件の
  取り込み方式・暫定運用の明記を提案。
- **セキュリティ担当**: バックエンドの静的解析(SpotBugs/PMD等)・依存性脆弱性
  検知(OWASP Dependency-Check/Dependabot)、フロントエンドのeslint-plugin-security
  相当+`npm audit`をCI Pipelineステージでの出発点として提案。内部H2の接続情報・
  秘密鍵をリポジトリにコミットしないことを必須とする方針、および未公開ライブラリ2件は
  サプライチェーン上むしろ有利だが取り込み方式確定時にバージョン固定(コミット
  ハッシュ/タグ)を条件とすることを提案。

## 人間ヒアリングでの確定事項(practices-discovery-questions.md)

- **Q1 (Way of Working)**: トランクベース開発・短命フィーチャーブランチ・
  レビュー担当分離なしの体制を踏襲することを確認。
- **Q2 (Walking Skeleton)**: `skeleton: on` を採用。Bolt 1として、業務DBスキーマ
  読み込み→内部H2設定保存→動的画面生成という一連の流れを一通り貫通させる薄い
  バージョンを先に作ることを確認。
- **Q3 (Testing Posture)**: 基本はtest-after、ただしスキーマ読み込み層のみ
  品質担当の提案どおり特性テスト先出しの前倒し運用を採用することを確認
  (`## Testing Posture` の Methodology は custom として記録)。
- **Q4 (Deployment)**: ステージング/本番の2段階分離は不要。自宅サーバ1台構成を
  前提に、タグ+手動デプロイのシンプルな運用でよいことを確認。
- **Q5 (Code Style・バックエンド)**: フォーマッタ・リンタの具体的選定は今は決めず、
  後続のCI Pipelineステージで決めることを確認。
- **Q6 (Code Style・内部H2命名)**: 開発者担当の提案どおり、内部H2のテーブル・
  カラム名に専用接頭辞(`ms_`等)を付ける命名規約を採用することを確認。
- **Q7 (未公開ライブラリ2件)**: セキュリティ担当のバージョン固定提案について、
  「まだ決めなくてよい」との回答。取り込み方式・バージョン固定方針そのものは
  ドメイン設計以降で扱う。

## 残る不確実性(後続ステージへ持ち越し)

- **CI/CDプラットフォームの選定**: GitHub Actions/Jenkins/その他、現時点でどの
  ツールも記録がない。品質担当・セキュリティ担当が提案した各種ゲート(lint+
  単体テスト、静的解析、依存性脆弱性検知、Testcontainersマトリクス)の具体的な
  組み込みは、Construction段階のCI Pipelineステージで確定する。
- **Code Style(バックエンド)**: Spotless/Checkstyle/google-java-format等の
  具体的な採否は未確認。CI Pipelineステージで確定する(Q5)。
- **未公開ライブラリ2件(`make-you-chic-ui`・`java-mustache-processor`)の取り込み方式
  と版固定方針**: 両者ともプロトタイプ段階でnpm/Maven Central未公開
  (feasibility-assessment.md 記載)。具体的な取り込み方式(gitサブモジュール等)、
  および版固定(コミットハッシュ/タグ)の方針そのものは、人間の回答(Q7: 「まだ
  決めなくてよい」)により、ドメイン設計以降で扱う不確実性として残す。

> 人間ヒアリング(Q1〜Q7)を経て、この内容は Consolidated Summary Confirmation で確認済み。
