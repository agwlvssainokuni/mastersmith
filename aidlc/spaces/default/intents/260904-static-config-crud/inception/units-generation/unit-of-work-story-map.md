# Unit Story Map: MasterSmith MVP

user-storiesステージはスコープ上SKIPされており`stories.md`が存在しないため、requirements.mdの各FRをUnitへマッピングする(domain-design/traceability.jsonの前例に倣う)。バックエンドの業務ロジックを担うUnitを「実装Unit」、対応する画面を担うUnitを「UI Unit」として分けて記載する。

## マッピング

| FR ID | 実装Unit | UI Unit | 備考 |
|---|---|---|---|
| FR1.1 | U1 schema-ingestion | U10 frontend-admin | — |
| FR1.2 | U1 schema-ingestion | U10 frontend-admin | — |
| FR1.3 | U1 schema-ingestion | U10 frontend-admin | — |
| FR1.4 | U1 schema-ingestion | U10 frontend-admin | — |
| FR1.5 | U1 schema-ingestion | U10 frontend-admin | — |
| FR1.6 | U1 schema-ingestion | (なし) | JDBCドライバ内包はビルド時の関心事(U11 packagingにも関連) |
| FR2.1 | U2 config-management | U10 frontend-admin | — |
| FR2.2 | U2 config-management | U10 frontend-admin | — |
| FR2.3 | U2 config-management | U10 frontend-admin | — |
| FR2.3.1 | U2 config-management | U10 frontend-admin | — |
| FR2.4 | U2 config-management | U10 frontend-admin | — |
| FR2.5 | U2 config-management | (なし) | 内部データストア(H2)への保存方針 |
| FR2.6 | U2 config-management | (なし) | キャッシュ管理 |
| FR3.1 | U4 dynamic-data-access | U9 frontend-core | — |
| FR3.2 | U4 dynamic-data-access | U9 frontend-core | — |
| FR3.3 | U4 dynamic-data-access | U9 frontend-core | — |
| FR3.4 | U2 config-management(MenuItem) | U9 frontend-core | メニュー構成自体はU2が保持、遷移導線はU9が実装 |
| FR3.5 | U4 dynamic-data-access | (なし) | 後勝ちの競合制御 |
| FR3.6 | U9 frontend-core | U9 frontend-core | バリデーション表示・アクセシビリティはUI層(make-you-chic-ui)の関心事 |
| FR4.1 | U4 dynamic-data-access | U9 frontend-core | — |
| FR4.2 | U4 dynamic-data-access | U9 frontend-core | — |
| FR5.1 | U3 permission | U10 frontend-admin | — |
| FR5.2 | U3 permission | U10 frontend-admin | — |
| FR5.3 | U3 permission | U10 frontend-admin | — |
| FR5.4 | U3 permission | U9 frontend-core(ロール切替のユーザーメニュー) | 複数ロール保有者のロール切替はどの画面からも行うため頻繁に使う側(U9)にも実装 |
| FR5.5 | U5 auth | (横断) | isAdminクレームの発行元。U2/U1/U8/U6/U10が判定に用いる |
| FR5.6 | U5 auth | U10 frontend-admin | 管理者専用画面のアクセス制御全般(横断的関心事) |
| FR6.1 | U5 auth | U9 frontend-core | — |
| FR6.2 | U5 auth | (なし) | トークン発行 |
| FR6.3 | U5 auth | (なし) | リフレッシュトークン |
| FR6.4 | U7 notification | (なし) | メールフロー本体の描画・送信 |
| FR6.4.1 | U6 account-management | U10 frontend-admin | 通知メール送信はU7へイベント連携 |
| FR6.4.2 | U6 account-management | U10 frontend-admin | — |
| FR6.4.3 | U6 account-management | U10 frontend-admin | — |
| FR6.4.4 | U6 account-management | U10 frontend-admin | — |
| FR6.5 | U7 notification | (なし) | Mustacheテンプレート描画 |
| FR6.6 | U5 auth | U9 frontend-core | ロック中のエラー表示 |
| FR7.1 | U8 audit-log | (なし) | 設定変更の記録受付 |
| FR7.2 | U8 audit-log | (なし) | 業務データ操作の記録受付 |
| FR7.3 | U8 audit-log | U10 frontend-admin | — |
| FR7.4 | U8 audit-log | U10 frontend-admin | — |
| FR7.5 | U8 audit-log | U10 frontend-admin | — |

## 複数Unitにまたがる横断的関心事

- **FR5.5 / FR5.6(管理者ゲーティング)**: U5(auth)がisAdminクレームを発行し、U1・U2・U6・U8・U10の各Unitがそれぞれ自身の画面・操作へのアクセス制御としてローカルに判定する(ADR-002)。単一のUnitに閉じない横断的関心事である。
- **FR1.6(JDBCドライバ内包)**: U1(schema-ingestion)がドライバを利用するが、実際にアプリケーションへドライバを組み込むのはU11(packaging)のビルド配線の役割である。
- **auth/account-managementの共有スキーマ**: U5とU6は同一のAccountエンティティの永続化スキーマを共有する(unit-of-work.md参照)。

## Unit別カバレッジ確認

| Unit | 割り当てられたFR数 |
|---|---|
| U1 schema-ingestion | 6(FR1.1〜FR1.6) |
| U2 config-management | 8(FR2.1〜FR2.6、FR2.3.1、FR3.4) |
| U3 permission | 4(FR5.1〜FR5.4) |
| U4 dynamic-data-access | 6(FR3.1〜FR3.3、FR3.5、FR4.1〜FR4.2) |
| U5 auth | 6(FR5.5、FR5.6、FR6.1〜FR6.3、FR6.6) |
| U6 account-management | 4(FR6.4.1〜FR6.4.4) |
| U7 notification | 2(FR6.4、FR6.5) |
| U8 audit-log | 5(FR7.1〜FR7.5) |
| U9 frontend-core | 8(FR3.1〜FR3.4、FR3.6、FR4.1〜FR4.2、FR5.4、FR6.1、FR6.6の一部と重複あり) |
| U10 frontend-admin | 多数(管理者専用画面すべて。UI Unit列参照) |
| U11 packaging | 0(業務要件でなくビルド配線の関心事) |

全41件のFRがいずれかの実装Unitに割り当てられており、未割り当て(GAP)は存在しない。
