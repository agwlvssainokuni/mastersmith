# Reliability Requirements: config-management

自宅サーバ1台構成のため、SLA/SLO数値目標(稼働率%等)は設けない(requirements.md NFR1、team.md Deployment)。

## NFR-CONSISTENCY.1: 設定インポート時の全体拒否

設定インポート(BR5.2)は、不正形式・スキーマ不一致・必須項目欠落のいずれかを1件でも検出した場合、設定全体の適用を拒否し(部分適用を行わない)、検出した全件をerrors配列として返す。検証は最初の1件で打ち切らず最後まで行う。これにより、インポート失敗時に設定が不整合な中間状態になることを防ぐ。

## NFR-CONSISTENCY.2: キャッシュとDBの整合性

設定の作成・更新・削除の都度、DBへの書き込みに続けて該当キャッシュエントリを明示的に無効化する(BR6.2)。インポート成功時はキャッシュ全体をクリアする(BR5.3)。これにより、キャッシュがDBの内容と乖離した状態で読み取られることを防ぐ。

## NFR-FAILSAFE.1: schema-ingestion呼び出し失敗時の扱い

スキーマ取り込みからのTableConfig一括作成(BR2.1)は契約#1(config-management → schema-ingestion)の呼び出しを伴う。呼び出し自体が失敗(業務DB接続失敗等)した場合はエラーとして呼び出し元へ伝播し、500として応答する(BR2.1違反時挙動)。
