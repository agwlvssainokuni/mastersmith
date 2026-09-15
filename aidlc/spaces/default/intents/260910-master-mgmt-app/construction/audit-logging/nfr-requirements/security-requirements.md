# Security Requirements — audit-logging (U7)

## NFR2.1: 閲覧画面へのアクセス制御(認可)

`GET /api/audit-log`の呼び出し時、PermissionEngineの`canAccessScreen(activeRoleId, "audit-log")`(C10契約、同期呼出、functional-design rules.md BR7.10)への委譲によりサーバー側で実効権限を再検証する。クライアント側UIの出し分けのみに依存しない(project.md Mandated準拠)。アクセス不可の場合は403 Forbidden(RFC 9457形式のProblemDetails)を返す。

## NFR2.2: 改ざん・削除不可(データ完全性)

AuditLogEntryへの記録は追記(INSERT)のみとし、アプリケーション層(リポジトリ/DAOインタフェース)にUPDATE/DELETEに相当するメソッドを一切定義しない(functional-design rules.md BR7.5、Q7=A)。project.md Mandated「監査ログは改ざん・削除ができないようにする」を満たす水準として、DBレベルの追加防御(トリガー等)は本Boltのスコープでは実装しない。

## NFR2.3: 機微情報の非記録・非出力

- 現行3イベント(ConfigChangedEvent・PermissionChangedEvent・ImportExecutedEvent)のactor値(システム識別子・activeRoleId・ユーザーID)・target値(スキーマ/テーブル名相当の文字列・ロールID・テーブルID)は、いずれも認証情報(パスワード等)・個人情報を含まない(nfr-requirements-questions.md Q5確定)。したがって、BR7.7の失敗時ログにこれらの値をそのまま含めてもproject.md Mandated(パスワード等の認証情報を平文でログ・監査ログ・エラーメッセージに出力しない)には抵触しない。
- AuditLogEntry自体(閲覧APIレスポンス含む)にも、現行3イベント由来のエントリにはパスワード等の認証情報は含まれない(`beforeValue`/`afterValue`は常にnull、functional-design entities.md参照)。

## NFR2.4: セキュリティ関連CIゲート

team.mdの既定(SAST・シークレットスキャンをマージ前のCIブロッキングチェックとして導入)をそのまま適用する。本ユニット固有の追加ゲートはない。依存関係の脆弱性スキャンは意図的に対象外(team.md既定)。

## NFR2.5: エラーレスポンスの情報開示制御

`GET /api/audit-log`のエラーレスポンス(403 Forbidden、503 Service Unavailable、NFR4.4参照)は、RFC 9457形式のProblemDetailsとし、開発者向けのスタックトレースや内部実装詳細(SQL文、内部設定DBの接続情報等)を含めない。
