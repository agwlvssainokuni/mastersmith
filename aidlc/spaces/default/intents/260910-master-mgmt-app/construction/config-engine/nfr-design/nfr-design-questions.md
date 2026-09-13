# NFR Design Questions — config-engine (U1)

`construction/config-engine/nfr-requirements/`の確定内容に基づき、config-engineユニットのNFR具体設計を確定するための質問。大半の設計判断（キャッシュ戦略・fail-fast検証・楽観ロック対象外・技術選定）はnfr-requirementsステージで既に確定済みのため、ここでは未決定の論点のみを問う。

## Q1: ヘルスインジケータへの反映

起動時のfail-fast検証（設定読み込み完了）を、Spring Boot Actuatorのヘルスチェックエンドポイントに反映させますか。

- A. 反映させる。設定読み込みが成功した場合のみconfig-engine由来のヘルスインジケータをUPとし、fail-fastで異常検知した場合はアプリ起動自体が中断するため、起動後のヘルスチェックには通常到達しない（起動失敗そのものが最も強いシグナル）が、念のため専用インジケータを用意する
- B. 専用インジケータは設けない。fail-fast検証はアプリ起動を中断させるため、起動後のヘルスチェックに独自の項目を追加する必要はないと判断する（アプリ全体の標準ヘルスチェックのみで十分）
- X. Other (please specify)

[Answer]: B. 専用インジケータは設けない。fail-fast検証はアプリ起動を中断させるため、起動後のヘルスチェックに独自の項目を追加する必要はないと判断する（アプリ全体の標準ヘルスチェックのみで十分）

## Q2: config-engine内部のロジカルコンポーネント分割

config-engineパッケージ内部を、責務ごとのサブコンポーネント（設定モデル保持・キャッシュ管理・fail-fast検証・翻訳エントリ管理等）に論理分割して設計しますか、それとも単一の凝集したコンポーネントとして扱いますか。

- A. 責務ごとに論理分割する（例: ConfigModelStore, ConfigCache, ConfigValidator, TranslationStore）。各サブコンポーネントは単一責任を持ち、障害時の影響範囲（blast radius）を局所化する
- B. 単一の凝集したConfigEngineコンポーネントとして扱う（内部クラス分割はCode Generationで実装詳細として決定し、NFR Design段階では論理分割を明示しない）
- X. Other (please specify)

[Answer]: A. 責務ごとに論理分割する（ConfigModelStore, ConfigCache, ConfigValidator, TranslationStore等）。各サブコンポーネントは単一責任を持ち、障害時の影響範囲（blast radius）を局所化する

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
