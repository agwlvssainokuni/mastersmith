# Performance Design: auth

## 応答速度の一般方針

requirements.md NFR1(厳密な数値目標なし)を踏襲する。

## Argon2の計算コスト

サービスコンポーネントはSpring SecurityのArgon2PasswordEncoderをデフォルトパラメータ(メモリ・反復回数・並列度)で使用する。ログイン処理(BR1.1)の応答時間にはArgon2照合の計算コストが含まれ、他の操作より遅くなること自体は意図的なトレードオフとして許容する(セキュリティ上のブルートフォース耐性を優先)。パラメータのチューニングはcode-generation段階で必要に応じて行う。

## キャッシュ

キャッシュ層は設けない。Account・RefreshToken・AccountActionTokenはいずれも認証・セッション状態そのものであり、キャッシュ導入は不整合リスク(ロック状態・トークン失効の反映遅延)を招くため見送る。
