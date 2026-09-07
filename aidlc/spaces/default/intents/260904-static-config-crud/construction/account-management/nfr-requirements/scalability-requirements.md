# Scalability Requirements: account-management

## NFR2.1: 単一インスタンス・単一業務前提

requirements.md NFR2(1インスタンス=1業務、マルチテナンシーなし)を踏襲する。AccountViewのレコード数(実体はauth側のAccountおよびpermission側のロール割り当て)は、当該業務インスタンスの登録利用者数に比例する。自宅サーバ1台・個人利用中心という想定運用規模では、一覧取得のページネーション(NFR1.2)と合わせて性能上の課題になることはない。
