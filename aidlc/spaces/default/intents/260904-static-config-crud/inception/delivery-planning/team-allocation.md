# Team Allocation: MasterSmith MVP

## 前提

Team Formation(1.5)はスコープ(mvp)上SKIPされている。開発者は一人(`constraint-register.md` OC-01)であり、Construction段階の全10 Bolt(bolt-plan.md参照)は、実装作業を担う唯一のAIエージェントである**aidlc-developer-agent**が担当する。

## Bolt別担当

| Bolt | 担当 |
|---|---|
| Bolt 1(Walking Skeleton) | aidlc-developer-agent |
| Bolt 2(audit-log) | aidlc-developer-agent |
| Bolt 3(permission) | aidlc-developer-agent |
| Bolt 4(config-management残り) | aidlc-developer-agent |
| Bolt 5(auth残り+account-management) | aidlc-developer-agent |
| Bolt 6(dynamic-data-access残り) | aidlc-developer-agent |
| Bolt 7(notification) | aidlc-developer-agent |
| Bolt 8(frontend-core残り) | aidlc-developer-agent |
| Bolt 9(frontend-admin) | aidlc-developer-agent |
| Bolt 10(packaging仕上げ) | aidlc-developer-agent |

## 並行作業・承認体制

複数チームによる並行作業(Program Board相当の調整)は本プロジェクトには該当しない。delivery-planning-questions.md Q5の回答により、各Boltは順次実行する(依存関係のないUnit同士の並行構築は行わない)。

人間(ユーザー)はteam.mdの合意どおり、Bolt 1(Walking Skeleton)完了時に明示的な承認を行い、その後の残りBoltを続けるかどうかの判断(org.mdの「ラダープロンプト」: 自律的に継続するか、Bolt毎にゲートするか)を行う。
