# Reliability Requirements — audit-logging (U7)

## NFR4.1: データ完全性(改ざん・削除不可)

AuditLogEntryは追記専用(append-only)とし、アプリケーション層にUPDATE/DELETE経路を持たない(functional-design rules.md BR7.5)。project.md Mandated「監査ログは改ざん・削除ができないようにする」を満たす。

## NFR4.2: 記録失敗時の許容(Q3=B)

イベント購読後の内部設定DBへの書き込みが失敗した場合、構造化ログ(ERRORレベル)へ記録するのみとし、発行元ユニットの処理へは一切影響を与えない(例外の再送出・リトライなし、rules.md BR7.7)。監査記録の欠落は許容されるMVPスコープの制約とする。専用のメトリクス・アラートは設けず、通常のログ記録のみとする(nfr-requirements-questions.md Q3確定)。

## NFR4.3: 無期限保持

AuditLogEntryは無期限に保持する(FR8.3)。削除・アーカイブ機能は本MVPスコープでは実装しない(project.md Out of Scope)。

## NFR4.4: 内部設定DB接続断時の閲覧APIの挙動(Q6=A)

`GET /api/audit-log`呼び出し時に内部設定DBが利用不可の場合、503 Service Unavailable(RFC 9457形式のProblemDetails)を返す。専用のフォールバック(キャッシュ等)は設けず、他の内部設定DB依存エンドポイントと同様の一般的な障害処理に従う。

## NFR4.5: イベント発行元の疎結合(可用性への影響遮断)

audit-loggingの内部設定DB書き込み処理の遅延・失敗が、イベント発行元(config-engine・permission-engine・data-import-export)の可用性・応答性に影響を与えないことを、fire-and-forget発行(NFR4.2)により保証する。

## NFR4.6: 監査記録パイプライン全断の検知手段に関する既知の残存リスク(アーキテクチャレビュー指摘R-03対応、意図的なスコープ判断)

NFR4.2(記録失敗時は構造化ログのみ、専用メトリクス・アラートなし、Q3=B)とobservability-requirements.md NFR5.1(内部処理時間の計装対象外、Q4=B)を組み合わせると、audit-loggingの記録処理そのものが完全に停止した場合(例: 内部設定DBへの接続が恒久的に失われた、イベントリスナー登録自体が何らかの理由で機能しなくなった等)、これを自動的に検知する手段が本Boltには存在しない。唯一の検知経路は、構造化ログ(BR7.7)をオペレーターが手動で確認する運用である。

project.md Mandated「監査ログは... 少なくとも操作者・操作対象・操作種別・日時・変更前後の値を記録する」という要件に対し、記録パイプライン自体の完全停止が長期間見過ごされるリスクが残る。これは見落としではなく、Q3・Q4で明示的に確認済みの意図的なスコープ判断(専用の監視基盤導入は本MVPスコープでは過剰と判断)による**受容された残存リスク(accepted risk)**として、ここに明記する。将来、この残存リスクが許容できないと判断された場合は、最小限の安全策(例: AuditLogEntry書き込み成功をカウントするだけの軽量なメトリクス1つの追加)を再検討事項とする。
