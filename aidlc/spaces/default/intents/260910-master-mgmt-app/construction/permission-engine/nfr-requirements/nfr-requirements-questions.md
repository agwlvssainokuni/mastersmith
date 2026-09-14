# NFR Requirements Questions — permission-engine (U3)

`inception/requirements-analysis/requirements.md`のNFR1〜NFR8、および`construction/permission-engine/functional-design/`(entities.md/rules.md/functional-spec.md)に基づき、permission-engineの非機能要件を定量化するための質問。permission-engineは`resolveEffectivePermission`がlist-engine/record-edit-engine/menu-navigation/user-management/audit-logging/config-import-exportから横断的に高頻度で呼び出されるユニットである点を踏まえる。

## Q1: `resolveEffectivePermission`単体の応答時間予算

NFR1は画面全体で応答時間3秒以内(95パーセンタイル)としているが、一覧画面はカラムごとに`resolveEffectivePermission`を呼び出しうる(例: 20カラムなら1画面で20回呼び出し)。単体呼び出しの応答時間予算はどうしますか。

- A. 単体呼び出し(DBアクセス込み)は50ms以内(95パーセンタイル)を目標とする。画面全体で数十回呼び出されても3秒予算内に収まる水準
- B. 単体呼び出しの個別目標値は設けず、画面全体の3秒予算のみで管理する
- X. Other (please specify)

[Answer]: A. 単体呼び出し(DBアクセス込み)は50ms以内(95パーセンタイル)を目標とする。画面全体で数十回呼び出されても3秒予算内に収まる水準

## Q2: 権限判定結果のキャッシュ方針

`resolveEffectivePermission`が1リクエスト内で同一(activeRoleId, scopeType, scopeRef)を複数回呼ばれる、または同一ロールへの呼び出しが短時間に集中する場合のキャッシュ方針はどうしますか。

- A. リクエストスコープ内(1回のHTTPリクエスト処理中)でのみ結果をメモ化する。RBAC設定変更(assignPermission)が即座に反映される必要があるため、リクエストを跨ぐキャッシュは行わない
- B. 短いTTL(例: 数秒〜数十秒)のプロセス内キャッシュを許容し、RBAC変更の反映に多少の遅延があってもよい
- C. キャッシュは行わず、呼び出しのたびに内部設定DBへ問い合わせる(埋め込みDBのため十分高速と想定)
- X. Other (please specify)

[Answer]: B. 短いTTL(例: 数秒〜数十秒)のプロセス内キャッシュを許容し、RBAC変更の反映に多少の遅延があってもよい

## Q3: RBAC設定データの想定規模

NFR3(スケーラビリティ)は「想定利用規模は数十名程度」としている。permission-engineが保持するRole/PrimaryPermission/AuxiliaryPermission/Group関連データの想定規模はどの程度ですか(インデックス設計・容量見積もりの根拠とする)。

- A. ロール数は数十件程度、1ロールあたりのPrimaryPermission/AuxiliaryPermission設定は対象テーブル・カラム数に比例(数百〜数千行程度)、Groupは数件〜十数件程度
- B. より小規模(ロール数十件未満、設定行数も数百行未満)を想定し、特別なインデックス設計は不要
- X. Other (please specify)

[Answer]: A. ロール数は数十件程度、1ロールあたりのPrimaryPermission/AuxiliaryPermission設定は対象テーブル・カラム数に比例(数百〜数千行程度)、Groupは数件〜十数件程度

## Q4: 権限昇格の試行・拒否の監視要否

`PermissionEscalationException`(BR3.8)の発生は、セキュリティ上意味のあるシグナル(不正な昇格の試行、または実装/設定ミス)になりうる。観測要件として扱いますか。

- A. `PermissionEscalationException`の発生をメトリクス(カウンタ)として記録し、異常な頻発時にアラート可能にする
- B. 通常のエラーログ記録のみとし、専用のメトリクス・アラートは設けない(MVPスコープでは過剰)
- X. Other (please specify)

[Answer]: A. `PermissionEscalationException`の発生をメトリクス(カウンタ)として記録し、異常な頻発時にアラート可能にする

## Q4 Follow-up: FR4.2ロール選択との切り分けの確認

初回回答で「ロール選択(自分に割り当てられた複数ロールから一つを選ぶ操作、FR4.2)は、割り当てられたものを選ぶだけであり、選べる権限範囲が狭くなっても広がってもエラーや警告として扱わない」旨のコメントがあった。これはassignPermission(管理者による新規権限付与、BR3.8の昇格判定対象)とは別論であることを確認した。

- A. 確認した通り: ロール選択(FR4.2)は常に許可される操作であり、PermissionEscalationExceptionの監視対象(Q4)には含まれない。監視対象はあくまでassignPermission呼び出し時の昇格拒否のみ
- X. Other (please specify)

[Answer]: A. 確認した通り: ロール選択(FR4.2)は常に許可される操作であり、PermissionEscalationExceptionの監視対象(Q4)には含まれない。監視対象はあくまでassignPermission呼び出し時の昇格拒否のみ

## Q5: 監査ログ(PermissionChangedイベント)配信の信頼性

`components.md`のドメインイベント発行はfire-and-forget(疎結合)方針だが、権限変更イベント(BR3.11)は監査要件(project.md Mandated)とも関係する。配信保証のレベルはどうしますか。

- A. 他ユニット(config-engine等)と同じfire-and-forget方式を踏襲する。イベント配信の信頼性保証の詳細化はNFR設計(3.3)に委ねる(`project.md`の既存学習事項と整合)
- B. permission-engineに限り、イベント発行の成功を確認してからDBコミットを確定する等、より強い配信保証を求める
- X. Other (please specify)

[Answer]: A. 他ユニット(config-engine等)と同じfire-and-forget方式を踏襲する。イベント配信の信頼性保証の詳細化はNFR設計(3.3)に委ねる(`project.md`の既存学習事項と整合)

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
