# RAID Log: MasterSmith

## Risks

| ID | Risk | Likelihood | Impact | Mitigation | Source |
|---|---|---|---|---|---|
| R-01 | make-you-chic-uiがプロトタイプ段階でnpm未公開のため、MasterSmithへの取り込み方式(gitサブモジュール等)が未決定 | Medium | Low | ドメイン設計フェーズで取り込み方式を具体化する | [Q11] |
| R-02 | PostgreSQL/MySQL/MariaDBの3種類にまたがって、複合主キー・ビュー等のJDBC DatabaseMetaData取得結果に差異がある可能性 | Medium | Medium | 3つのテスト用スキーマ(家電EC・ポイント管理・蔵書管理)による検証で早期に差異を洗い出す | [Q1][Q5] |
| R-03 | 設定を内部DB(H2)に保持することが「静的設定駆動」という設計思想とやや緊張関係にある | Low | Medium | キャッシュ機構(起動時読み込み+明示的クリア/expireでのみ再読み込み)により実行時の動的解釈を回避する | [Q16] |
| R-04 | 成功指標が定性的なままで、測定可能な代理指標が未定義(intent-captureステージのレビュー指摘R-01がUnresolvedのまま持ち越されている) | Low | Low | 要求分析(requirements-analysis)ステージで測定可能な代理指標を定義する | intent-capture review R-01 |
| R-05 | java-mustache-processorがMaven Central等未公開のプロトタイプ段階のため、MasterSmithへの取り込み方式が未決定 | Medium | Low | ドメイン設計フェーズで取り込み方式を具体化する | [Q18] |
| R-06 | アカウントライフサイクルに伴うメール送信フロー(6種類)が、当初のログイン機能の想定より大きく、MVPスコープを拡張する可能性がある | Medium | Medium | 次のScope Definitionステージで、どの範囲までMVPに含めるかを明示的に確認する | [Q18] |

## Assumptions

| ID | Assumption | Validation Status | Source |
|---|---|---|---|
| A-01 | AI生成のダミースキーマ(家電EC・ポイント管理・蔵書管理)が、実際の多様な業務ドメインを検証するのに十分代表的である | Unvalidated | [Q1] |
| A-02 | MasterMeisterの権限モデルがMasterSmithの「テーブル単位・操作単位」の権限定義の枠組みに十分参考になる | Unvalidated | [Q8] |
| A-03 | 自宅サーバのリソースが、Java(Spring Boot)+内部H2 DBの実行に十分である | Unvalidated | [Q9] |

## Issues

None. (Feasibility段階のため、まだ顕在化した問題はない)

## Dependencies

| ID | Dependency | Detail | Source |
|---|---|---|---|
| D-01 | make-you-chic-ui(外部リポジトリ) | https://github.com/agwlvssainokuni/make-you-chic-ui — フロントエンドのUIコンポーネント一式を提供する外部資産。プロトタイプ段階 | [Q11] |
| D-02 | 対象RDBMS用JDBCドライバ | PostgreSQL/MySQL/MariaDB用のJDBCドライバをアプリケーションに内包する | [Q9(intent-capture)] |
| D-03 | H2データベース | 内部DB(設定・アカウント・権限)として使用 | [Q15] |
| D-04 | java-mustache-processor(外部リポジトリ) | https://github.com/agwlvssainokuni/java-mustache-processor — メールテンプレート(HTML、`<title>`をSubjectとする)のレンダリングに使用する外部資産。プロトタイプ段階 | [Q18] |

## Assumptions & Open Questions

None.
