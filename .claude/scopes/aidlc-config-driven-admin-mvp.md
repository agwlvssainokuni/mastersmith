---
name: config-driven-admin-mvp
depth: Comprehensive
keywords: []
description: Config-driven master-data admin app, composed MVP
skeleton: on
---

# config-driven-admin-mvp scope

A composed scope for a greenfield config-driven master-data admin
application. The task arrived with reference material describing the
intended user experience but no existing codebase, so the grid runs a
Comprehensive-depth ideation-through-construction spine while skipping the
stages that only pay off against code that already exists or an operational
surface that does not exist yet.

## Why these stages, why skip those

Ideation runs intent-capture, feasibility, scope-definition, and
rough-mockups to turn the supplied reference material into a resolved
intent and a first visual pass, then approval-handoff gates the move into
inception — kept EXECUTE (unlike `mvp`) because the reference-material intake
and the config-driven approach both need an explicit human sign-off before
design work builds on them. market-research and team-formation stay SKIP: an
internal admin tool has no market to research and no multi-team coordination
to form.

reverse-engineering is SKIP because the project is greenfield — there is no
existing codebase to map — while practices-discovery still runs to establish
conventions and test tooling from scratch. requirements-analysis and
refined-mockups run to pin the functional spec and the UI before design;
user-stories is SKIP because the personas are a single internal admin
audience already covered by requirements-analysis and the mockups. The full
component-modeling spine (domain-design, units-generation, contract-design,
delivery-planning, functional-design) runs because the config-driven
generation pattern needs real architectural decisions and inter-unit
contracts. nfr-requirements and nfr-design both run since the config-driven
approach raises non-obvious NFR questions (validation rules, extensibility)
that a single requirement line would not resolve; infrastructure-design is
SKIP — the target infrastructure is not changing.

code-generation, build-and-test, and ci-pipeline run to build, verify, and
gate the implementation. The entire operation phase (deployment-pipeline,
environment-provisioning, deployment-execution, observability-setup,
incident-response, performance-validation, feedback-optimization) is SKIP,
matching `mvp`: this scope proves the admin app, it does not yet carry
production operations weight.

## Membership

No keyword triggers — select with `--scope config-driven-admin-mvp`.
Initialization, the reduced ideation set (minus market-research and
team-formation), the full inception pass minus reverse-engineering and
user-stories, the construction pass minus infrastructure-design, and no
operation stages make up the grid.
