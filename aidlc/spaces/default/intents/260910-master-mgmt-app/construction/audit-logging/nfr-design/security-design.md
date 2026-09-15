<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Security Design — audit-logging (U7)

`construction/audit-logging/nfr-requirements/security-requirements.md`(NFR2.1〜NFR2.5)、および`nfr-design-questions.md`の確定回答に基づく、audit-loggingユニットのセキュリティ設計。

## 認可アーキテクチャ(NFR2.1対応)

`GET /api/audit-log`呼び出し時、Spring Security認証済みプリンシパルから`activeRoleId`を取得し、PermissionEngine(C10契約)の`canAccessScreen(activeRoleId, "audit-log")`を同期呼び出しで委譲する(`functional-design/rules.md` BR7.10)。クライアント側UIの出し分けのみに依存せず、サーバー側で実効権限を再検証する(project.md Mandated)。

```java
// AuditLogController内の認可チェック(概念設計)
if (!permissionEngineApi.canAccessScreen(activeRoleId, "audit-log")) {
    throw new AccessDeniedException(); // 403 Forbiddenへマッピング(RFC 9457)
}
```

予約`screenKey`"audit-log"は、permission-engineが既にBR3.10・BR3.15で予約・実装済みのものをそのまま利用し、本ユニット側で新規のscreenKey定義は行わない(`functional-spec.md` W2参照)。

## データ完全性(追記専用、NFR2.2対応)

`AuditLogEntryRepository`(仮称)には`save`(INSERT相当)・`find`系メソッドのみを定義し、UPDATE/DELETEに相当するメソッドを一切定義しない(`functional-design/rules.md` BR7.5)。DBレベルの追加防御(トリガー等)は本Boltのスコープでは実装しない。

```java
// AuditLogEntryRepositoryのメソッド設計方針(概念設計)
interface AuditLogEntryRepository {
    AuditLogEntry save(AuditLogEntry entry); // INSERT相当のみ
    Page<AuditLogEntry> findByTargetType(String targetType, Pageable pageable);
    Page<AuditLogEntry> findAll(Pageable pageable);
    // update/deleteに相当するメソッドは定義しない
}
```

## クエリパラメータの入力検証(NFR2.5関連、Q4確定)

`GET /api/audit-log`の`page`・`pageSize`・`targetType`クエリパラメータは以下のルールで検証し、範囲外・不正な値は400 Bad Request(RFC 9457形式のProblemDetails)で拒否する(サイレントクランプは行わない)。

- `page`: 1以上の整数
- `pageSize`: 1〜100の整数(既定20)
- `targetType`: `functional-design/entities.md`が定義する既知の値集合(`ConfigEngine`/`PermissionEngine`/`DataImportExport`)のいずれか、または未指定

## 機微情報の非記録・非出力(NFR2.3対応)

現行3イベント(ConfigChangedEvent・PermissionChangedEvent・ImportExecutedEvent)のactor値・target値はいずれも認証情報・個人情報を含まないため(nfr-requirements-questions.md Q5確定)、BR7.7の失敗時ログにこれらの値をそのまま含めてもproject.md Mandated(パスワード等の認証情報を平文でログ・監査ログ・エラーメッセージに出力しない)には抵触しない。AuditLogEntry自体(閲覧APIレスポンス含む)の`beforeValue`/`afterValue`は常にnullであり(`functional-design/entities.md`)、認証情報が含まれることはない。

## エラーレスポンスの情報開示制御(NFR2.5対応)

`GET /api/audit-log`のエラーレスポンス(400 Bad Request・403 Forbidden・503 Service Unavailable)は、RFC 9457形式のProblemDetailsとし、開発者向けのスタックトレースや内部実装詳細(SQL文、内部設定DBの接続情報等)を含めない。

```yaml
# ProblemDetails例(403 Forbidden)
type: about:blank
title: Forbidden
status: 403
detail: この操作を実行する権限がありません。
```

## Security Anti-Requirements(除外事項、`security-requirements.md`から継承)

- 本ユニット固有の追加CIゲートはない。team.md既定(SAST・シークレットスキャンをマージ前のCIブロッキングチェック)をそのまま適用する。
- 依存関係の脆弱性スキャンは意図的に対象外(team.md既定)。
