## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-16T22:31:12Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Critical | `backend/src/main/java/com/mastersmith/permission/event/PermissionChangedEvent.java`(Javadoc「発行粒度」)/ `code-summary.md`「計画からの逸脱」 | `rules.md` BR3.11は「config-import-exportの1回のインポート実行につき1件のサマリイベント(実行者・変更件数・日時)。個々の変更前後の値は含めない」ことを明示的に要求している。しかし実装は`assignPermission`/`assignAuxiliaryPermission`の**呼び出しごとに**`PermissionChangedEvent(targetRoleId, scopeType, scopeRef, actor, occurredAt)`という**個々の変更内容を含む**イベントを発行し、実行単位への集約(件数の合算)を、まだ存在しないconfig-import-exportユニットの責務として一方的に付け替えている。これはBR3.11そのものと矛盾する設計変更であり、かつ`code-summary.md`の「計画からの逸脱」1〜5番のいずれにも記載がない(Javadocとplan.md Step8の注記にのみ埋もれている)。監査ログ(audit-logging)がBR3.11のサマリ形状を前提に購読設計されている場合、N件の生イベントを受け取ることになり、後続の未着手ユニットの機能設計・契約に予期せぬ手戻りを生む。 | BR3.11自体を改訂するか(functional-spec.md/rules.mdの正式な追補として)、実装をBR3.11通りのサマリイベント発行に戻す。いずれにせよ、この逸脱を`code-summary.md`「計画からの逸脱」に明記し、影響を受けるaudit-logging/config-import-exportの機能設計側に申し送る。 | New |
| R-02 | Critical | `backend/src/main/java/com/mastersmith/permission/PermissionEngineApi.java`(クラスJavadoc consumers列挙) / `inception/contract-design/contract-summary.md` C10 | C10契約(`contract-summary.md`)には存在しない3つの逸脱(`assignPermission`への`actorRoleId`引数追加、`assignAuxiliaryPermission`の新設、`getGroupDerivedRoleIds`の追加)が、本ユニットのJavadocにのみ記録され、契約の一次情報である`contract-summary.md`のC10セクションには一切反映されていない。C10は「各契約はプロバイダー側ユニットが所有する」「加法的変更はプロバイダー単独の判断で行ってよい」とされているが、その加法的変更が契約書自体に反映されないままでは、未着手のコンシューマー(config-import-export、user-management等)がC10のYAML(`params: {roleId, scopeType, scopeRef, level}`、`actorRoleId`なし)のみを見て実装した場合、必須引数の欠落により統合時に破綻する。 | `contract-summary.md` C10セクションを本ユニットの実装に合わせて更新する(`assignPermission`の`actorRoleId`引数、`assignAuxiliaryPermission`メソッド、`getGroupDerivedRoleIds`メソッドを追記)。 | New |
| R-03 | Major | `backend/src/main/java/com/mastersmith/permission/PermissionEngineApi.java`(クラスJavadoc consumers列挙に`schema-introspector`を追加し「NFR Designレビュー指摘R-02対応」と根拠付け) | 検証の結果、この根拠は事実と一致しない。(1) `nfr-design/security-design.md`のR-02は「Caffeineキャッシュのインスタンスローカル性・複数インスタンス構成での`invalidateAll()`未到達」という**キャッシュの問題**であり、consumer一覧やschema-introspectorとは無関係。(2) `inception/units-generation/unit-of-work-dependency.md`のDAG・依存表・`unit-of-work.md`のいずれにも、schema-introspectorからpermission-engineへの依存(sync/event問わず)は存在しない。(3) `contract-summary.md` C10のconsumers列挙にも`schema-introspector`は含まれない。存在しないレビュー指摘を根拠として契約の対象範囲(consumers)を無断で拡張しており、追跡可能性(traceability)を損なう。 | Javadocから「schema-introspectorの追加はNFR Designレビュー指摘R-02対応」という誤った帰属を削除する。schema-introspectorが実際に本ユニットへ依存する設計上の必要があるなら、`unit-of-work-dependency.md`/`contract-summary.md`への正式な追補を経てから記載する。 | New |
| R-04 | Major | `backend/src/main/java/com/mastersmith/config/store/ConfigEngineApi.java`(`findColumnConfigById`追加、本ユニットの前提修正) | C9は「config-engine」が所有する契約であり、`contract-summary.md`「Contract Ownership Rules」は「各契約はプロバイダー側(提供側)ユニットが所有する」と明記する。しかし本コミットは、コンシューマー側であるpermission-engineが直接config-engineの契約実装ファイル(`ConfigEngineApi.java`/`ConfigCache.java`/`ConfigModelStore.java`)を編集してメソッドを追加しており、プロバイダー側の合意プロセスを経た形跡がない(`code-summary.md`は「前提修正」「軽微な拡張」とだけ述べ、config-engineユニット側の承認を確認していない)。加えて、この追加(`findColumnConfigById`)は`contract-summary.md` C9セクションのYAML(`methods`列挙)にも反映されていない。data-import-exportによる`getTableConfigById`追加も同様に未反映であり、C9は既に実装から乖離した状態にある。 | 少なくとも`contract-summary.md` C9セクションに`findColumnConfigById`(および既存の`getTableConfigById`)を追記する。今後のコンシューマー側からの契約拡張は、プロバイダーユニットとの合意記録(レビューまたはQ&A)を伴う手順に改める。 | New |
| R-05 | Minor | `backend/src/main/java/com/mastersmith/permission/service/PermissionEngineApiImpl.java`(`assignAuxiliaryPermission`) | `entities.md`はAuxiliaryPermission.scopeTypeの`allowed_values`を`[SCHEMA, TABLE]`(COLUMN除外)と定義しているが、`assignAuxiliaryPermission`実装はscopeTypeの値を検証しておらず、呼び出し元が誤って`ScopeType.COLUMN`を渡した場合もそのままDBへ永続化されてしまう(`PermissionResolver.resolveAuxiliaryField`はCOLUMN段を読み飛ばすため、以後恒久的に参照されない無効データとして残る)。construction/phases.mdの「Validate and sanitize all inputs at system boundaries」に照らし、境界での防御が欠けている。 | `assignAuxiliaryPermission`の冒頭で`scopeType == ScopeType.COLUMN`を`IllegalArgumentException`等でfail fastに拒否する。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `./gradlew :backend:checkstyleMain :backend:checkstyleTest :backend:compileJava :backend:compileTestJava` | BUILD SUCCESSFUL(違反なし) | コードスタイル・コンパイルは問題なし |
| `./gradlew :backend:test --tests "com.mastersmith.permission.*"` | BUILD SUCCESSFUL(失敗テストなし) | ユニット固有のテストは全てpass |
| `./gradlew :backend:test :backend:jacocoTestCoverageVerification` | BUILD SUCCESSFUL | プロジェクト全体のテスト・80%カバレッジフロアともに通過。今日のconfig-engine側の変更(BR1.8修正等)と組み合わせても回帰なし |
| traceability.json全OK対象ファイルの実在確認 | 全て実在を確認(`PermissionResolver`, `PermissionEngineApiImpl`, `PermissionEscalationChecker`, `BootstrapStateChecker`, entity各種) | 参照切れなし |
| 必須セクション(≥2 H2)の確認 | `code-generation-plan.md`・`code-summary.md`とも複数のH2見出しを含み充足 | 問題なし |

### Summary

ビルド・テスト・カバレッジ等の機械的検証はすべて通過しているが、契約(C9/C10)と実装の間に看過できない乖離が2件(R-01: BR3.11のイベント形状そのものと矛盾する未申告の設計変更、R-02: C10契約書が実装の逸脱を反映せず未着手コンシューマーの実装を破綻させうる)あり、加えて存在しないレビュー指摘を根拠にした契約範囲の拡張(R-03)とプロバイダー契約への越境編集(R-04)を確認した。これらはいずれも本ユニット単体のコードは正しく動作していても、システム全体の契約整合性・追跡可能性を損なう構造的な問題であり、READY判定はできない。
