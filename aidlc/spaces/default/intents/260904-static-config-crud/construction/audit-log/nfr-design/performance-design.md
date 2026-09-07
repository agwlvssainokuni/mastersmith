# Performance Design: audit-log

## インデックス設計

参照・絞り込み(BR2.1・BR2.2)は、actionType・actorAccountId・occurredAt(範囲検索)・targetDescription(部分一致)を条件に持つ。AuditLogEntryテーブルに以下のインデックスを設ける。

- `occurredAt`への単一インデックス(期間フィルタ・ソート双方に使用)
- `actorAccountId`への単一インデックス
- `actionType`への単一インデックス

targetDescriptionの部分一致検索(BR2.2)はLIKE演算(前方一致に限定しない)のため、インデックスは効果が限定的であり付与しない。想定運用規模(自宅サーバ1台)ではテーブルスキャンでも許容範囲と判断する。

## キャッシュ

キャッシュ層は設けない。監査ログは追記主体で、参照は管理者による低頻度アクセスに限られるため、キャッシュ導入のコスト(キャッシュ無効化ロジックの複雑化)に見合わない。

## ページネーション

一覧取得(BR2.1)はSpring Data JPAの`Pageable`を用い、page/sizeパラメータでDB側のOFFSET/LIMITに変換する(アプリケーション層での全件取得後の絞り込みは行わない)。
