# Scalability Requirements — permission-engine (U3)

## NFR3.1: データ規模の想定(Q3由来)

- Role: 数十件程度
- PrimaryPermission / AuxiliaryPermission: 対象テーブル・カラム数に比例し、数百〜数千行程度
- Group / GroupMembership / GroupRole: 数件〜十数件程度

この規模はNFR3(想定利用規模: 数十名程度)と整合する。特別な水平分割・シャーディングは不要と判断する。

## NFR3.2: インデックス設計方針

PrimaryPermission・AuxiliaryPermissionの検索キーは(roleId, scopeType, scopeRef)であり(entities.md参照)、この複合キーに一意インデックスを張ることで、BR3.4/BR3.5のスコープ階層探索(最大3回のルックアップ)を各O(1)〜O(log n)で処理できる規模である。

## NFR3.3: 同時アクセス

NFR1(同時アクセス最大50ユーザー)と整合する範囲であり、`resolveEffectivePermission`はステートレスな読み取り主体の処理のため、追加的なロック機構やスロットリングは不要。`assignPermission`(書き込み)はconfig-import-exportの一括インポート実行時のみに限定され、同時実行数は運用上ごく少数(管理者操作)に留まる。

## NFR3.4: キャッシュによるスケーラビリティ確保(Q2由来)

`resolveEffectivePermission`結果は短いTTL(例: 数秒〜数十秒、具体的な秒数はCode Generationで確定)のプロセス内キャッシュを許容する(Q2=B)。RBAC変更(assignPermission)の反映には最大でTTL分の遅延が生じうるが、この規模のMVPでは許容するトレードオフとして採用する。キャッシュキーは(activeRoleId, scopeType, scopeRef)の組。

## NFR3.5: 将来の成長への対応

現状の想定規模を大きく超える成長(業務テーブル数・ユーザー数の大幅増)が生じた場合は、キャッシュTTLの調整や読み取りレプリカの追加等で線形にスケールできる設計とする(NFR3の「線形にリソースを追加することで対応できる設計」との整合)。本MVPスコープでの具体的な実装は不要。
