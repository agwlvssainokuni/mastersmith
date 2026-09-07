# Security Requirements: permission

permission Unitはプロジェクトの業務データ権限モデルそのものを実装するため、本Unitの中核機能自体がセキュリティ要件である。

## NFR-AUTHZ.1: テーブル単位権限のデフォルト拒否(セキュアバイデフォルト)

あるロール・テーブルの組み合わせに対してTablePermissionレコードが1件も存在しない場合、そのロールはそのテーブルへの一覧・詳細・作成・編集・削除のすべての操作を拒否される(functional-design BR5.1)。新しいテーブルを取り込んだ際、明示的に権限を設定しない限りアクセスできない、セキュアバイデフォルトの設計とする。

## NFR-AUTHZ.2: カラム単位権限のデフォルト許可

カラム単位のアクセスレベル(ColumnPermission)は、未設定時はeditable(更新可)として扱う(BR5.2)。BR5.1のテーブル単位デフォルト拒否とは非対称な既定方針であり、実装時にこの非対称性を取り違えないことを明記する。

## NFR-AUTHZ.3: 管理者ゲーティングとの分離

permission Unitが管理する業務データ権限モデル(Role/Group/RoleAssignment/TablePermission/ColumnPermission)は、管理者専用機能へのアクセス制御(isAdminクレーム、FR5.5/FR5.6)とは独立したモデルである(BR6.1)。本Unitはアクセストークンのisadminクレームに基づくアクセス制御には関与しない。

## NFR-FAILSAFE.1: 権限確認呼び出し失敗時のフェイルセーフ

契約#3(dynamic-data-access → permission)の呼び出しが何らかの理由で失敗した場合、呼び出し元であるdynamic-data-access側の設計(BR4.1)により403として扱われる。permission自身の障害・応答不能が、誤って「許可」判定に倒れることのない設計とする。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T12:24:13Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | security-requirements.md > NFR-FAILSAFE.1(reliability-requirements.md > NFR-FAILSAFE.1にも同一記述あり) | NFR-FAILSAFE.1は「契約#3の呼び出しが何らかの理由で失敗した場合...BR4.1により403として扱われる」と述べているが、根拠として引用されているdynamic-data-access BR4.1のlogicは「IF X-Active-Roleがrolesクレームに含まれない THEN 403。IFテーブル単位の該当アクション権限がない THEN 403」であり、これは明示的な「不許可」判定のケースのみを扱う。同様にcontract-summary.md契約#3のFailure behaviorも「不許可の場合は例外とし...403として応答する」であり、不許可の判定結果を指しているに過ぎない。permission自身の内部例外・DB障害等の「予期しないエラー」は、契約-summary.mdの共通規約(Q8)で明示的に500(予期しないエラー)にマッピングされ、dynamic-data-access OpenAPI(#12)の/api/data/{tableId}系エンドポイントにも500レスポンスは定義されていない。つまりBR4.1・契約#3のいずれも、permission自身の障害・応答不能(明示的な不許可判定とは異なるケース)を403にマッピングするとは規定していない。実際にそのように振る舞うためには、dynamic-data-access側が契約#3呼び出しから飛ぶ「不許可」例外だけでなく、あらゆる例外(接続断・NPE等)を捕捉して403にマッピングする追加の設計判断が必要だが、それはBR4.1にも契約#3にも書かれていない。なお、この経路は「予期しないエラー」の場合でも500として拒否応答にはなるため、誤って「許可」に倒れる実害(認可バイパス)は生じない — 引用しているBR4.1の適用範囲についての不正確な記述という点が問題である。 | NFR-FAILSAFE.1の記述を、(a)明示的な不許可判定のケース(BR4.1が実際にカバーする範囲、403)と、(b)permission自身の未捕捉例外・応答不能のケース(現状は共通規約により500)とに分けて記述し直す。後者についても「誤った許可に倒れない」というセキュリティ特性自体は500でも維持されることを明記するか、あるいはdynamic-data-access側で契約#3からのあらゆる例外を403に正規化する設計をBR4.1に追記のうえ引用する。 | New |
| R-02 | Minor | performance-requirements.md > NFR1.1 / traceability.json > NFR1エントリ | requirements.md NFR1は「厳密な数値目標は設けない」に加え「著しい遅延の兆候(例: 主要画面の初期表示に数秒以上かかる)が確認された場合は、別途性能検証を行うこと」という追加の運用トリガーを含むが、performance-requirements.md NFR1.1は前半のみを踏襲し、後半のトリガー条項に触れていない。traceability.jsonはNFR1をstatus OKとして完全カバー扱いにしている。 | NFR1.1に「著しい遅延の兆候が確認された場合の性能検証」トリガーへの言及を追加するか、それがプロジェクト横断のプロセス方針でありpermission Unit固有の成果物には具体化しないという判断であることを明記する。 | New |

### Validation Tool Results

該当ステージにvalidation toolの指定なし(手動でのクロスリファレンス検証を実施)。

### Summary

NFR-AUTHZ.1〜.3はfunctional-design BR5.1・BR5.2・BR6.1と文言レベルで一致しており、traceability.jsonのカバレッジも概ね妥当。ただしNFR-FAILSAFE.1が引用するBR4.1・契約#3のFailure behaviorは「明示的な不許可」ケースのみを規定しており、「permission自身の障害」を403にマッピングするという主張の直接的な裏付けが上流に存在しない(実害としての認可バイパスはないが、根拠の引用が不正確)。「軽量版」の前提(数値目標なし・マルチテナントなし)自体は要件どおり正しく反映されている。Major 1件・Minor 1件でREADY基準(Critical 0件・Major 2件以下)を満たす。
