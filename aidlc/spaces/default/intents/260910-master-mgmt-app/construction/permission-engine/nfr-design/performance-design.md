# Performance Design — permission-engine (U3)

## NFR1.1/NFR1.2設計: `resolveEffectivePermission`/`canAccessScreen`の高速化

- スコープ階層探索(BR3.4: カラム→テーブル→スキーマ、最大3段のPrimaryPermission検索)は、(roleId, scopeType, scopeRef)複合インデックスを用いた単純な等値検索の連鎖として実装する(詳細はscalability-design.md参照)。
- 補助権限(BR3.5: テーブル→スキーマ、最大2段)も同様。
- いずれもN+1クエリを避けるため、1回の判定呼び出しにつき最大3(主権限)+2(補助権限)=最大5回のDB問い合わせに収める設計とし、単体50ms予算(NFR1.1)内に収まることを想定する。

## キャッシュアーキテクチャ(Q1由来)

- ライブラリ: Caffeine(Java向け高性能インメモリキャッシュライブラリ)を採用する(Q1=B)。
- キャッシュキー: `(activeRoleId, scopeType, scopeRef)`の3項組(NFR3.4のレビュー指摘R-05を踏まえ、roleId成分を明示的にキーへ含める)。
- キャッシュ値: `EffectivePermission { level, canCreate, canDelete }`(C10の戻り値そのもの)。
- TTL: 短時間(具体的な秒数はCode Generationで設定可能な値として実装し、既定値は数秒〜数十秒のオーダーとする)。
- 無効化(レビュー指摘R-01対応、旧設計からの改訂): `assignPermission`成功時、対象キーのみのピンポイント削除ではなく、Caffeineの`Cache.invalidateAll()`(引数なし)でキャッシュ全体を無効化する。

  **改訂理由**: `resolveEffectivePermission`はスコープ階層(BR3.4: カラム→テーブル→スキーマ、BR3.5: テーブル→スキーマ)でフォールバック解決を行うため、あるロールのCOLUMNレベルのキャッシュ済みエントリが、実際にはより上位のTABLE/SCHEMAレベルのPrimaryPermission/AuxiliaryPermissionから導出された値である場合がある。TABLE/SCHEMAレベルの変更時に、そのキー(roleId, scopeType, scopeRef)のみをピンポイント無効化すると、フォールバック経由で解決済みの下位スコープのキャッシュエントリ(例: (role, COLUMN, c1))は無効化されずTTL経過まで古い実効権限を返し続け、NFR2.8(降格の即時反映)の主目的を損なう。

  対象スコープを特定して部分無効化する設計(TABLE変更時に配下の全COLUMNキーを特定する等)は、config-engineのスキーマ構造への追加の問い合わせを要し複雑化するため、本MVPスコープでは採用しない。`assignPermission`はconfig-import-exportの一括インポート時にのみ呼ばれる低頻度の管理操作であり(NFR1.3参照)、キャッシュ全体の無効化によるDB問い合わせの一時的な増加は許容範囲と判断する。

## 20カラム同時呼び出しの性能予算(NFR1.1/NFR1.4)

一覧画面の初回描画(全列キャッシュミス)では、20回 × 50ms = 最大1,000msの直列実行を想定する。NFR1(3秒・95パーセンタイル)の予算内に収まる(`nfr-requirements/performance-requirements.md` NFR1.1参照)。2回目以降の描画(同一ユーザー・同一セッションの再訪問等)はキャッシュヒットにより実測はこれを下回る。

C10契約にバッチ解決APIは存在しない。本MVPスコープでは追加しないが、将来のカラム数増加時の改善候補として`tech-stack-decisions.md`に記録する。
