# NFR Requirements Questions: frontend-core

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、frontend-core固有に新たな数値目標は追加しない。frontend-coreはUI Unit(kind: ui)のため、本ステージで作成する成果物はperformance-requirements.md・security-requirements.md・tech-stack-decisions.md・traceability.jsonの4件のみとする。functional-design(functional-spec.md、9ワークフロー)で既に確定済みの内容(トークンリフレッシュ、recordIdの不透明性、アカウント存在有無の非開示)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

frontend-core Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし)を踏襲。一覧画面はバックエンド側のページネーション(page/size)を利用する(ワークフロー3)。

**security-requirements.md**: recordId(不透明な文字列)をクライアント側でデコード・解釈しない(ワークフロー4手順1、dynamic-data-access BR2.1のサイドチャネル対策をクライアント側でも尊重する)。パスワード忘れ申請は、対象アドレス宛の送信有無に関わらず同一の完了メッセージを表示し、アカウント存在有無を応答から区別しない(ワークフロー7a、auth BR6.2)。X-Active-Roleヘッダーによるロール切替はクライアント側で完結するがサーバー呼び出しを伴わず、実際の認可判定はサーバー側(dynamic-data-access BR4.1)で行われる(クライアント側のロール切替UI自体は認可境界ではない)。React/TSXの標準エスケープに従う(dangerouslySetInnerHTML等は用いない)。**既知の繰延べ事項(Major、R-07)**: ワークフロー1手順6(トークンリフレッシュ)は、リフレッシュ成功時に新しいaccessTokenのみを差し替え、authのリフレッシュトークンローテーション(BR3.2)で同時発行される新しいrefreshTokenへのクライアント側追従が明記されていない。functional-designステージ終了ゲートで既にMajorとして記録済みの未解消事項であり、本ステージでは隠蔽せず明示的に記録する(修正はスコープ外)。

**tech-stack-decisions.md**: React/TSX + 自作デザインシステムmake-you-chic-ui、Prettier + ESLintを踏襲する。

**traceability.json**: upstream_ids = NFR1, NFR3(OK、部分的)。NFR2・NFR4〜NFR9はN/A。

[Answer]: Looks correct
