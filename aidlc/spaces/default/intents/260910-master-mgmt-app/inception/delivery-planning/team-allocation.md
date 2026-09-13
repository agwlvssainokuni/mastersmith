# Team Allocation — MasterSmith(マスタ管理アプリ)

`mob`とは、1つのBoltを担当する実装チーム(複数人が同じ作業に同時に取り組むペアプログラミングの拡張形態)を指す。本プロジェクトのスコープ(`config-driven-admin-mvp`、`mvp`系)では ideation の Team Formation ステージ(1.5)がSKIP対象であったため、`delivery-planning.md`の既定に従い、全14BoltをAI(developer-agent)が単独で実装する。

## Bolt-to-Mob 割当

| Bolt | 内容 | 担当Mob |
|---|---|---|
| 1 | Walking Skeleton | aidlc-developer-agent(AI単独) |
| 2〜14 | 各Unit完全実装 | aidlc-developer-agent(AI単独) |

## 割当の根拠

- Team Formationステージ(1.5)がSKIP対象のスコープ(`mvp`)であるため、人間のチーム編成情報(役割・スキルセット・モブ構成)は存在しない。
- `delivery-planning.md`の既定(「1.5がSKIPの場合、全BoltはAI(aidlc-developer-agent)が実行するとする」)に従う。
- Program Board相当の複数チーム調整は本プロジェクトでは不要(チーム数=1)。

## Construction 実行体制

- **実行方式**: 1セッション内でAIが順にBolt 1〜14を実行する(`aidlc-state.ts set-unit-ownership solo`相当、単独実行)。複数チームによる並行所有は行わない。
- **承認体制**: Bolt1(Walking Skeleton)は単独・ゲート付きとし、ユーザーが明示的に承認する。Bolt2〜14は`team-practices.md`のラダー回答(自律継続)に従い、Construction Autonomy Mode = autonomous のもとで自律的に直列実行される。
