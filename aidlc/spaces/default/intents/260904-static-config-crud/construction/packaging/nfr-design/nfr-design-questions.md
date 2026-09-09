# NFR Design Questions: packaging

軽量版方針で進める。packagingはkind=packagingのUnitであり、本ステージで作成する成果物はsecurity-design.md・traceability.jsonの2件のみとする(performance/scalability/reliability/observability-design.md・logical-components.mdは対象外)。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

nfr-designステージ全体のRequest Changes後の回復手続きとして、packaging Unitのnfr-design成果物を、以下の内容(iteration 2でREADY判定を受けた最終版)で内容変更なしのまま再確定します。

**security-design.md**: 単一WAR成果物への秘密情報の非同梱対象は、project.md Forbiddenの3項目(内部H2接続情報・パスワードハッシュのソルト/ペッパー・アクセストークン署名鍵)。Gradleビルド設定内で秘密情報の値を一切参照しない設計とする。「業務DB接続情報の暗号鍵」は本Unit固有の関心事ではなくconfig-management Unit側の関心事(同UnitのNFR-DATA.1)であり、上流nfr-requirements/security-requirements.mdの根拠なき4項目目記載(R-01、Major、Status: New、未解消)を本nfr-design設計では踏襲しない旨を明記済み。application.ymlはプレースホルダ(`${ENV_VAR_NAME}`形式の環境変数参照)のみを含み、実値はビルド成果物にもリポジトリにも含めない。

**traceability.json**: nfr-requirementsで確定した各NFR項目(NFR-DATA.1・NFR-DATA.2)を、上記の設計解へマッピングする。

[Answer]: Looks correct
