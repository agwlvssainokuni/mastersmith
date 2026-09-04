# Intent Statement: MasterSmith

## Problem Statement

MasterMeister（動的にDB接続情報からスキーマを読み込み、実行時に振る舞いを決定する方式）を実際に開発・運用する中で、汎用的すぎて業務ごとにカスタマイズできない点が課題として見えてきた [Q1][Q4]。動的スキーマ解釈そのものの複雑さや不具合が引き金だったわけではなく、開発を通じた気づきが動機である [Q1][Q4]。

## Target Customer

差し当たり開発者自身（個人プロジェクト）[Q2][Q5][Q6]。使いながらブラッシュアップし、将来的には他の利用者にも展開することを見据えている [Q2][Q5]。現時点で具体的な想定利用者(自分以外)は未定である [Q2]。

## Success Metrics

実際に自分自身の複数の業務でMasterSmithを使えていることを成功の目安とする。同じアプリケーションでありながら異なるテーブル構成の業務のマスタ管理に対応でき、あたかも各業務向けに個別開発したかのように見えることを理想とする [Q3]。定量的な指標は現時点で定義されていない [Q3]。

## Initiative Trigger

MasterMeisterを開発する過程で「業務に合わせてカスタマイズできた方が良い」と感じたこと [Q4]。MasterMeisterは廃止・置換されず継続し、MasterSmithはこれとは独立した別プロジェクトとして並行して存在する [Q8]。

## Initial Scope Signal

- **Workflow-selected scope** [scope, workflow-selected]: `mastersmith-mvp` — 設定スキーマ定義・設定ローダー・一覧画面・詳細/編集画面を対象とするMVP範囲(本番デプロイ・環境構築・監視・運用は対象外)。
- **User-confirmed product boundary** [Q11][Q12][Q13]: 上記のMVP範囲でよいことが確認された。デプロイ・環境構築・監視・障害対応は対象外のままでよい。一方、利用者ごとの権限をテーブル単位・操作単位で定義できる枠組み(権限の"設定"の枠組み)は、後からの手戻りを避けるためMVP範囲に含める [Q12]。権限を適用するには利用者を識別できる必要があるため、シンプルなログイン機能(ID/パスワード認証)もMVP範囲に含める。外部IdP連携などフルの認証基盤は範囲外のまま [Q13]。

対象RDBMSはPostgreSQL/MySQL/MariaDB(MasterMeisterと同様)とし、JDBCドライバはアプリケーションに内包する [Q9]。進め方に明確な締切はなく、じっくり検証しながら進める [Q10]。

## Assumptions & Open Questions

None.

## Review

**Verdict:** READY
**Reviewer:** aidlc-product-lead-agent
**Date:** 2026-09-04T13:38:32Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/260904-static-config-crud/ideation/intent-capture/intent-statement.md > Success Metrics | 「実際に自分自身の複数の業務でMasterSmithを使えていること」「あたかも各業務向けに個別開発したかのように見えること」は定性的な理想像であり、「定量的な指標は現時点で定義されていない」と明記されたまま。今回の改訂ではQ13(認証追加)のみが扱われ、この指摘には触れられていない。QAやレビューアが「成功したか」を判定できる基準がない。 | 例えば「自分が運用する業務用テーブルのうちN件以上をMasterSmithで管理画面化し、それぞれで個別の設定変更(再デプロイなし)のみで対応できている」のような、件数・期間・再デプロイ有無などで測れる代理指標を最低1つ定義する。 | Unresolved |
| R-02 | Major | aidlc/spaces/default/intents/260904-static-config-crud/ideation/intent-capture/intent-statement.md > Initial Scope Signal | (改訂前)権限定義の枠組み(テーブル単位・操作単位)をMVPに含める一方、認証・ログインはMVP範囲外とされており、「誰に対して権限を適用するのか」を識別する手段がなかった。今回の改訂で、Q13の回答(①案)に基づき「シンプルなログイン機能(ID/パスワード認証)もMVP範囲に含める。外部IdP連携などフルの認証基盤は範囲外のまま」という一文が本文に追加され、`intent-capture-questions.md`にも`## Requested Changes Feedback`としてR-02への対応が明記されている。ログイン機能により「誰がログインしているか」を識別した上で権限を適用できる、という筋は通っており、整合性の欠落は解消されたと判断する。 | 対応不要(解消済み)。後続のドメイン設計・契約設計では、ログイン(認証)と権限定義(認可)の適用範囲・関係(例: パスワードポリシー、セッション管理の要否、権限定義の実際の適用タイミングがMVP内かどうか)を具体化すること。 | Resolved |
| R-03 | Minor | aidlc/spaces/default/intents/260904-static-config-crud/ideation/intent-capture/stakeholder-map.md > Key Stakeholders(将来の利用者) | 「将来の利用者」の行は「現時点で具体的な人物像は未定」とだけ記載され、ペルソナが未定義のまま。今回の改訂ではこの点への言及はない。 | 未定義であること自体は許容範囲だが、後続フェーズ(スコープ定義など)で具体化する旨を一言添えるか、Open Questionsに明示するとより追跡しやすい。 | Unresolved |
| R-04 | Minor | aidlc/spaces/default/intents/260904-static-config-crud/ideation/intent-capture/intent-statement.md > Initial Scope Signal(末尾) | 対象RDBMS制約(PostgreSQL/MySQL/MariaDB、JDBCドライバ内包)と進め方の期限(締切なし)に関する文が、User-confirmed product boundaryの直後に地の文として混在しており、スコープ確認事項と性質の異なる情報(NFR・進行管理)が同一セクションに同居している。今回の改訂で変更はない。 | 見出しを分けるか、少なくとも別の箇条書き項目として独立させ、スコープ確認の記述と混同しないようにする。 | Unresolved |

### Summary

Q13の追加(ログイン機能をMVP範囲に含める決定)により、R-02が指摘していた「権限設定はあるが誰に適用するかを識別できない」という整合性の欠落は解消された。ログイン(認証)と権限定義(認可)の対象範囲が本文に明記され、外部IdP連携は範囲外のままという境界も明確である。一方、R-01(成功指標が定性的なままで測定可能なプロキシ指標がない)はこの改訂で意図的に手つかずのままであり、依然としてゲートに持ち越すべきMajor事項である。R-03・R-04のMinor事項も未着手。本レビューはアドバイザリー(単発)パスであり、これらの所見は人間の承認判断のための参考情報であってブロックはしない。
