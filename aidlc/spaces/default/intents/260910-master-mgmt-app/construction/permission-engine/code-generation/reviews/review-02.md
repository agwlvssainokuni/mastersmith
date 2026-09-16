## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-16T22:44:01Z
**Iteration:** 2

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Critical | backend/src/main/java/com/mastersmith/permission/service/PermissionEngineApiImpl.java > afterAssignment / PermissionChangedEvent.java | Per-call `PermissionChangedEvent` publication violated BR3.11's per-import-run summary-event granularity. Verified fix: `PermissionEngineApiImpl.java` no longer references `ApplicationEventPublisher`/`eventPublisher` anywhere (grep confirms zero hits in main sources); `afterAssignment()` only calls `permissionCache.invalidateAll()`. `PermissionChangedEvent.java` is retained (needed by already-built `audit-logging`'s `AuditLogEventMapper`/`PermissionChangedEventListener`) with corrected Javadoc stating the emitter is config-import-export (not yet built). `functional-spec.md`'s `## Assumptions & Open Questions` (line 124) explicitly discloses the deferral and the resulting no-event design of `assignPermission`/`assignAuxiliaryPermission`. Accepted side-effect (audit-logging's listener temporarily unreachable) is correctly disclosed, not re-litigated. | None — verified fixed. | Resolved |
| R-02 | Critical | inception/contract-design/contract-summary.md > C10 | C10 YAML omitted `assignPermission`'s `actorRoleId` param, the `assignAuxiliaryPermission` method, and `getGroupDerivedRoleIds`. Verified: C10 now lists `assignPermission` with `actorRoleId` first in params, `assignAuxiliaryPermission` with matching params (`actorRoleId, targetRoleId, scopeType: schema\|table, scopeRef, createAllowed, deleteAllowed`), and `getGroupDerivedRoleIds(userId) -> List<string>` — all three match `PermissionEngineApi.java`'s actual public interface signatures. | None — verified fixed. | Resolved |
| R-03 | Major | backend/src/main/java/com/mastersmith/permission/PermissionEngineApi.java (Javadoc) + contract-summary.md C10 consumers | Fabricated "NFR Designレビュー指摘R-02対応" citation and unbacked schema-introspector consumer claim. Verified: `PermissionEngineApi.java`'s class Javadoc no longer cites that fabricated reference (current addendum bullets cite legitimate, checkable sources: functional-spec.md, entities.md, config-engine precedent). Separately, `schema-introspector`'s own `rules.md` BR2.8 (lines 117-133) genuinely requires `canAccessScreen(activeRoleId, "config-import-export")` re-verification, and contract-summary.md's C10 `consumers:` list now includes `schema-introspector` with a properly-justified, non-fabricated addendum note citing BR2.8 correctly. | None — verified fixed. | Resolved |
| R-04 | Major | contract-summary.md > C9 | permission-engine (and data-import-export) added `getTableConfigById`/`findColumnConfigById` to `ConfigEngineApi` without reflecting them in C9. Verified: C9 now lists both methods with signatures matching the actual `ConfigEngineApi.java` (`getTableConfigById(String): TableConfig throws TableConfigNotFoundException`; `findColumnConfigById(String): Optional<ColumnConfig>`, confirmed present in `ConfigEngineApi.java` at lines 53/61), with an addendum note explaining the reflection gap. | None — verified fixed. | Resolved |
| R-05 | Minor | PermissionEngineApiImpl.java > assignAuxiliaryPermission | Missing rejection of `ScopeType.COLUMN` for auxiliary permissions. Verified: `validateAuxiliaryScopeType(ScopeType)` now throws `IllegalArgumentException` for `COLUMN` and is invoked in `assignAuxiliaryPermission` before the escalation check; test `assignAuxiliaryPermissionRejectsColumnScopeType` (PermissionEngineApiImplTest.java lines 327-339) verifies the exception is thrown and that `escalationChecker`/`auxiliaryPermissionRepository` are never invoked in that case. | None — verified fixed. | Resolved |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `./gradlew :backend:checkstyleMain :backend:checkstyleTest :backend:compileJava :backend:compileTestJava` | PASS (no violations, no compile errors) | Code style and compilation clean across the unit's changes. |
| `./gradlew :backend:test --tests "com.mastersmith.permission.*"` | PASS (no test failures reported) | Confirms R-01/R-05 behavioral fixes and existing permission-engine suite remain green. |
| `./gradlew :backend:test :backend:jacocoTestCoverageVerification` (full backend bundle) | PASS (no coverage violations reported) | The 80% line-coverage floor (team.md Testing Posture) holds across the full backend build, not just this unit. |

### Summary

All five iteration-1 findings (2 Critical, 2 Major, 1 Minor) were independently re-verified against current source: the per-call event publication was fully removed with a correctly-scoped Javadoc explanation and disclosed open question, contract-summary.md's C9/C10 now accurately mirror the actual Java interfaces (including the cross-unit `schema-introspector` consumer addition, checked against schema-introspector's own BR2.8), and the COLUMN-scope rejection is implemented and tested. Checkstyle, compilation, unit tests, and the full-backend coverage gate all pass. No new defects found.
