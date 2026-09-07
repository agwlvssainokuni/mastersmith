# Reliability Design: audit-log

## レジリエンスパターン

イベントリスナー(記録受付、ワークフロー1)は、Spring `@EventListener`(または`@TransactionalEventListener`)メソッド内で処理全体をtry-catchブロックに包み、例外を自己完結で捕捉する(BR1.2)。捕捉した例外は発行元(config-management・dynamic-data-access・auth・account-management)へ再送出しない。

```java
@EventListener
public void onAuditableActionOccurred(AuditableActionOccurredEvent event) {
    try {
        auditLogRepository.save(toEntry(event));
    } catch (Exception e) {
        log.error("audit log record failed: actionType={}, occurredAt={}", 
                  event.actionType(), event.occurredAt(), e);
    }
}
```

## リトライ・サーキットブレーカー

リトライは行わない(BR1.2)。本Unitは他Unitへの外部呼び出しを行わないため、サーキットブレーカーは設計しない。

## ヘルスチェック

Spring Boot Actuatorの標準`/actuator/health`エンドポイントに委ね、本Unit固有のカスタムヘルスインジケータは追加しない。

## バックアップ・リカバリ

内部H2データストア全体のバックアップ方針(スナップショット等)はインフラ横断の関心事であり、本Unit固有の設計は行わない。
