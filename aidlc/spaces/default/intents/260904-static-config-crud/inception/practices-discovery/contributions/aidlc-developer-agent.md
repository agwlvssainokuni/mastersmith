**Collaborator:** aidlc-developer-agent

## Contribution
team-practices.md のCode Style節は言語ごとの命名規則(Java: camelCase、TSX: camelCase/PascalCase)とツール選定(フロントエンドはPrettier+ESLint)には触れているが、Java+TypeScriptの二言語モノレポを単一WARに束ねるという本プロジェクト特有の構成に関する規約が抜けている。具体的には、バックエンド(Gradleモジュール、`src/main/java`)とフロントエンド(`frontend/`配下の独立したpackage.json・Viteプロジェクト)のディレクトリ分割、およびフロントエンドのビルド成果物をGradleの`bootWar`ライフサイクルに組み込んで`./gradlew build`一発で単一デプロイ可能物を生成する規約を、Code Style/Tech Stack欄に明示することを提案する。この配線を決めないままConstruction段階に進むと、開発者ごとに場当たり的なビルド手順が生まれるリスクがある。

パッケージ構成については、コード生成ガイド(code-generation-patterns.md)が推奨する「レイヤー単位ではなく機能単位」の原則を、本プロジェクトのドメインに当てはめると、(a) スキーマ読み込み(JDBC DatabaseMetaData、RDBMS差異吸収)、(b) 内部設定管理(DB接続先・メニュー・検索条件・一覧項目・編集除外・バリデーション・フォーム部品・論理表示名のCRUD)、(c) 認証・権限(ロール/グループ、テーブル単位・カラム単位権限)、(d) 動的画面生成、という4つの機能境界でJavaパッケージ/TSXディレクトリの両方を揃えることを提案する。特に(a)は複数RDBMS対応の抽象化層になるため、Repository層とService層を明確に分離し、RDBMS固有ロジックをRepository実装側に閉じ込める設計が望ましい。

命名規則には、内部H2データストアが「業務DBのテーブル名・カラム名そのもの」を設定値(文字列)として保持するという特殊事情への配慮が抜けている。内部H2側のテーブル/カラムには`ms_`等の一貫したプレフィックスまたは専用スキーマを与え、設定値として格納される業務DB由来の識別子文字列と、MasterSmith自身のスキーマオブジェクト名とを、コード上・ログ上で混同しないようにする規約を追加することを推奨する。

最後に、make-you-chic-uiとjava-mustache-processorという2つの自作ライブラリがnpm/Maven Central未公開のプロトタイプ段階であり、取り込み方式(gitサブモジュール/ローカルパッケージ等)が未決定という点(feasibility-assessment.md記載)は、単なる技術選定ではなくビルド構成・ディレクトリ構造そのものに影響するコードスタイル上の懸案である。Code Style/Tech Stack節で「取り込み方式が確定するまでの暫定運用」を明記しておかないと、ドメイン設計以降で手戻りが生じる可能性がある。

## Positions
- AGREE: フロントエンドのPrettier+ESLint採用と言語慣用の命名規則には同意する。加えて、二言語モノレポのディレクトリ構成・単一WARへのビルド配線・内部H2の識別子命名規約・未公開ライブラリ2件の暫定取り込み方針をCode Style/Tech Stack節に追記することを提案する。
