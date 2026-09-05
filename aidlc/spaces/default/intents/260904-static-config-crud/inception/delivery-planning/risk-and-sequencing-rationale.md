# Risk and Sequencing Rationale: MasterSmith MVP

## 採用した順序付けの考え方

**Bolt**(1つ以上のUnitをまとめたConstruction段階での1回分のビルド単位)の並び順は、units-generation(2.7)が出したUnit依存関係(トポロジー)そのものではなく、経済的な判断で決める(delivery-planning-questions.md Q3・Q4)。

本プロジェクトでは以下の2段構えの考え方を採用した:

1. **Walking Skeleton(Bolt 1)を最初に置く**(Cockburn, *Crystal Clear*): team.md(practices-discovery)で既に「業務DBスキーマ読み込み→内部H2設定保存→動的画面生成という複数レイヤーを貫通する骨格が成立するかどうかが、アーキテクチャ上の最大の不確実性である」と確定しているため、この骨格を最初に一気通貫で証明する。これはトポロジカル順序からの意図的な逸脱であり、通常ならレベル0(schema-ingestion・permission・audit-log)から順に積み上げるところを、Bolt 1ではレベル0(schema-ingestion)からレベル4(packaging)までの複数レベルにまたがる薄いスライスを先取りして構築する。
2. **Bolt 2以降は「土台を先に」(foundation-first)の考え方で、unit-of-work-dependency.mdの依存レベルにほぼ沿って進める**: 開発者一人体制のため、正式なWSJF(Reinertsen, *Principles of Product Development Flow*; SAFe)スコアリングによる価値・緊急度・規模の重み付けは行わず、依存関係を満たす順に淡々と進めることを優先した(delivery-planning-questions.md Q3)。

## トポロジカル順序からの逸脱とその正当化

| 逸脱 | 内容 | 正当化 |
|---|---|---|
| Bolt 1がレベル0〜4を横断 | schema-ingestion(L0)・config-management/auth(L1)・dynamic-data-access(L2)・frontend-core(L3)・packaging(L4)の最小サブセットを1つのBoltにまとめる | Walking Skeletonとして、アーキテクチャ上の最大の不確実性(複数レイヤー貫通)を最初に検証するため。team.mdで既に合意済みの方針であり、本ステージで具体的なUnit構成に落とし込んだ |
| Bolt 5がauth(残り)とaccount-managementを束ねる | 両者は本来別Unit(U5・U6)だが、Accountエンティティの永続化スキーマを共有し(contract-summary.md #4・#19)、account-managementはauthが公開するリポジトリ/サービス経由でのみアクセスする | 共有スキーマの整合性を保ったまま両者を実装・検証する方が、スキーマ変更のたびに2つのBoltを往復するより効率的。1 Unit = 1 Boltという原則より、実際の結合度を優先した |

## 監査ログ(Bolt 2)と権限(Bolt 3)の順序

両者はいずれもレベル0(依存なし)に属し、どちらを先にしても依存関係上の問題はない。監査ログを先に置いたのは、Bolt 4(config-management)・Bolt 5(auth+account-management)・Bolt 6(dynamic-data-access)の3つのBoltがいずれも監査ログへの記録呼び出しを持つのに対し、権限確認を実際に呼び出すのはBolt 6(dynamic-data-access)のみであり、監査ログを先に完成させる方がより多くの後続Boltを早くブロック解除できるためである。

## リスク登録

| リスク | 起こりうる可能性 | 影響度 | 対応 |
|---|---|---|---|
| スキーマ読み込み層(U1)のRDBMS間差異(型表記・複合主キーの列順序報告等)を見落とす | 中 | 高(Bolt 1のWalking Skeletonそのものが証明できなくなる) | team.mdのTesting Postureにより、実装に先立って3種RDBMSの期待挙動を特性テストとして洗い出す前倒し運用を既に採用(delivery-planning-questions.md Q7で追加の懸念なしと確認済み) |
| 未公開の自作ライブラリ2件(`make-you-chic-ui`・`java-mustache-processor`)の取り込み方式が未確定のままBolt 1・7に到達する | 中 | 中(Bolt 1のfrontend-core、Bolt 7のnotificationがブロックされうる) | external-dependency-map.mdで追跡し、各Bolt着手前に取り込み方式を確定する |
| Walking Skeleton(Bolt 1)にauthの最小実装を含めたことで、Bolt 1のスコープが本来の「スキーマ読み込み→設定保存→画面生成」より広がる | 低 | 低(Bolt 1の作業量がやや増える程度) | delivery-planning-questions.md Q1で、実際のログインを経た本物の管理者ゲーティングを証明する価値がスコープ拡大のコストを上回ると判断し、明示的に合意済み |
| 開発者一人体制のため、Bolt完了の確認が遅れると後続Bolt全体が停滞する | 低 | 中 | 各Boltは順次実行(delivery-planning-questions.md Q5)し、Walking Skeleton完了後にorg.mdの「ラダープロンプト」(自律継続かBolt毎ゲートか)で運用モードを確定する |
