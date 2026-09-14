# NFR Design Questions — permission-engine (U3)

`construction/permission-engine/nfr-requirements/`の確定要件(応答時間予算50ms・短いTTLキャッシュ+能動的無効化・想定データ規模・権限昇格の監視・fire-and-forgetイベント配信)を、具体的な実装アーキテクチャに落とし込むための質問。

## Q1: キャッシュ実装方式

NFR3.4(短いTTLキャッシュ)・NFR2.8(assignPermission成功時の能動的無効化)を実装する方式はどうしますか。

- A. プロセス内インメモリキャッシュ(Java標準の`ConcurrentHashMap`+定期パージ、または軽量ライブラリ)を用い、キャッシュキーは(activeRoleId, scopeType, scopeRef)の3項組とする。無効化は、assignPermissionが対象とした(roleId, scopeType, scopeRef)に一致するキーのみをピンポイントで削除する(NFR2.8のレビュー指摘R-05: roleId成分を含めることで対象範囲を明確化)
- B. Caffeine等の専用インメモリキャッシュライブラリを追加導入し、TTL・サイズ上限・無効化APIを標準機能で賄う
- X. Other (please specify)

[Answer]: B. Caffeine等の専用インメモリキャッシュライブラリを追加導入し、TTL・サイズ上限・無効化APIを標準機能で賄う

## Q2: 認証・認可アーキテクチャの責務分界

`security-requirements.md` NFR2.1により、permission-engineはサーバー側での実効権限再検証の唯一の窓口です。認証(トークン検証)自体はauthentication-serviceの責務であり、permission-engineは認証済みの`activeRoleId`を引数として受け取る、という理解でよいですか。

- A. その通り。permission-engineは認証(トークン検証・セッション管理)を一切行わず、呼び出し元がC14(SessionContextApi)経由で取得した`activeRoleId`を信頼する。多層防御として、`activeRoleId`が実在するRoleかどうかの存在検証のみ行う(不正な/削除済みロールIDが渡された場合はNONE扱い)
- X. Other (please specify)

[Answer]: A. その通り。permission-engineは認証(トークン検証・セッション管理)を一切行わず、呼び出し元がC14(SessionContextApi)経由で取得した`activeRoleId`を信頼する。多層防御として、`activeRoleId`が実在するRoleかどうかの存在検証のみ行う(不正な/削除済みロールIDが渡された場合はNONE扱い)

## Q3: 暗号化(保存時・通信時)

RBAC設定データ(Role/PrimaryPermission/AuxiliaryPermission/Group関連)の暗号化要件はどうしますか。

- A. 内部設定DB全体の暗号化方針(ディスク暗号化等、インフラ層で対応)に従う。RBAC設定データ自体に個人情報や機密情報(パスワード等)は含まれないため、アプリケーション層での追加のフィールド暗号化は不要
- X. Other (please specify)

[Answer]: A. 内部設定DB全体の暗号化方針(ディスク暗号化等、インフラ層で対応)に従う。RBAC設定データ自体に個人情報や機密情報(パスワード等)は含まれないため、アプリケーション層での追加のフィールド暗号化は不要

## Q4: 入力検証・インジェクション対策の実装方式

NFR2.4(scopeRefは不透明な文字列として扱う)を踏まえ、内部設定DBへのクエリ実装における入力検証・インジェクション対策はどうしますか。

- A. JPA/Spring Data等のORM経由のバインド変数化(パラメータ化クエリ)を徹底し、`scopeRef`等の文字列を生SQL文字列結合に用いない。文字列長の上限(例: 255文字)のみアプリケーション層で検証し、内容自体の形式検証(config-engine実在チェック等)は行わない(BR3.14と整合)
- X. Other (please specify)

[Answer]: A. JPA/Spring Data等のORM経由のバインド変数化(パラメータ化クエリ)を徹底し、`scopeRef`等の文字列を生SQL文字列結合に用いない。文字列長の上限(例: 255文字)のみアプリケーション層で検証し、内容自体の形式検証(config-engine実在チェック等)は行わない(BR3.14と整合)

## Q5: 障害時のフォールバック方針

内部設定DBへの接続障害等で`resolveEffectivePermission`が例外を投げた場合、フォールバック(例: デフォルト拒否)はどうしますか。

- A. 例外はそのまま呼び出し元に伝播させ、安全側のデフォルト拒否(NONE相当)は行わない。呼び出し元(list-engine等)がエラーとして扱い、利用者にはエラー画面を表示する(黙って権限NONE扱いにすると「なぜ操作できないか」が利用者に伝わらず、また可用性障害を権限問題と誤認させるリスクがあるため)。サーキットブレーカー等の特別な耐障害パターンは本ユニットには導入しない(内部設定DBは同一プロセス内の埋め込みDBであり、外部サービス呼び出しのような障害モードを持たないため)
- X. Other (please specify)

[Answer]: A. 例外はそのまま呼び出し元に伝播させ、安全側のデフォルト拒否(NONE相当)は行わない。呼び出し元(list-engine等)がエラーとして扱い、利用者にはエラー画面を表示する(黙って権限NONE扱いにすると「なぜ操作できないか」が利用者に伝わらず、また可用性障害を権限問題と誤認させるリスクがあるため)。サーキットブレーカー等の特別な耐障害パターンは本ユニットには導入しない(内部設定DBは同一プロセス内の埋め込みDBであり、外部サービス呼び出しのような障害モードを持たないため)

## Q6: ログの相関ID伝播

NFR5.3(分散トレーシング)に関連し、`resolveEffectivePermission`等の呼び出しにおける相関ID(トレースID)の伝播方式はどうしますか。

- A. 呼び出し元(list-engine等)から伝播されるOTELのトレースコンテキスト(同一プロセス内呼び出しのため、スレッドローカルまたはSpring管理のコンテキスト伝播)にそのまま乗せる。permission-engine自身が新たな相関ID生成・伝播の仕組みを持つ必要はない
- X. Other (please specify)

[Answer]: A. 呼び出し元(list-engine等)から伝播されるOTELのトレースコンテキスト(同一プロセス内呼び出しのため、スレッドローカルまたはSpring管理のコンテキスト伝播)にそのまま乗せる。permission-engine自身が新たな相関ID生成・伝播の仕組みを持つ必要はない

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
