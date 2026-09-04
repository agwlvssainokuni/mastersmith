# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project needs stable, durable guidance beyond the
> team practice (for example, package-specific release checks or an additional
> regression suite for a legacy component).

## Way of Working

<!-- Project-specific specialisation. Example: -->
<!-- This monorepo requires package-scoped branch names and a package owner -->
<!-- review in addition to the team's normal merge policy. -->

## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

## Deployment

<!-- Project-specific specialisation. -->

## Code Style

<!-- Project-specific specialisation. -->

## Tech Stack

<!-- Technology choices locked for this project. -->

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- MasterSmithの課題認識は「動的解決の複雑さ・不具合の排除」ではなく「業務ごとにカスタマイズできない」という点だった。ideation段階で企画者の実際の動機(実運用障害の有無)を確認せずに複雑さ排除の課題を前提としないこと。 (learned 2026-09-04) <!-- cid:260904-static-config-crud:intent-capture:f59783c985e834a22d7ca50d2824de36713a97b610de84f435f5ccce10f8667f -->
- 「設定の保持形式」のような未決定論点は、機械的に決め打ちせずFeasibilityステージでの対話を通じてユーザーと確定させること。今回は設定DB(内部H2)+キャッシュ機構という組み合わせに落ち着いた。 (learned 2026-09-04) <!-- cid:260904-static-config-crud:feasibility:244fa83fc20b7ddd55f83ee1c1e61c0a9b38b0cab1d69d76a3d5ba0ea02d8d97 -->
- Ideationフェーズの「実装詳細を含めない」ガードレールは、Feasibilityステージでの技術スタック選定(言語・フレームワーク・SPA/MPA等の技術的実現可能性に関わる決定)までは禁止しない。技術的実現可能性の検証はFeasibilityの本来の役割であるため、そこでの技術選定は適切と判断する。 (learned 2026-09-04) <!-- cid:260904-static-config-crud:feasibility:4ee1a0c4ce0ed9ea7b4fb7cc2da2ba0c80c3223d762bf3d6374cf395cf7966b2 -->
- 権限機能のように後工程への影響が大きい機能は、ラフ案の段階でも簡略化しすぎず、実際のモデル(ロール/グループ/カラム単位の権限等)を早めにユーザーへ確認すること。 (learned 2026-09-04) <!-- cid:260904-static-config-crud:rough-mockups:f9ad63b8d4ea7408110a5ef44a2e8e7765a56b55e42d2ac0b7be16780f223f53 -->
- 「業務(テーブル)」のような表現は、複数業務ドメインを指すのか単一業務内のテーブル群を指すのか曖昧になりやすい。確定済みのアーキテクチャ制約(例: インスタンスの分割単位)と矛盾しないか、ワイヤーフレーム作成時に必ず突き合わせること。 (learned 2026-09-04) <!-- cid:260904-static-config-crud:rough-mockups:794fd5d541f96fc8a86de1802f133959be767a588de6fa563e119e9aac1e54ed -->
- アドバイザリーレビューの指摘に対応する際は、指摘を全件まとめて反映してから再レビューを依頼する方が、部分反映を繰り返すより効率的。 (learned 2026-09-04) <!-- cid:260904-static-config-crud:rough-mockups:09f63a3d368434305f611a33ccd4a0a3a3673dc61f6e874a78ced3b0aa846379 -->
