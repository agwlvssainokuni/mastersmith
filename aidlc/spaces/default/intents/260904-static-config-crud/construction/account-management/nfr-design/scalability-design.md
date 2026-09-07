# Scalability Design: account-management

## スケーリングアーキテクチャ

単一インスタンス構成(NFR2.1)。水平スケーリングは設計しない。

## データ量への対応

本Unitは永続エンティティを持たない(entities.md)ため、データ量の増加に起因するスケーラビリティ課題は生じない。AccountViewのレコード数(実体はauth・permission側)は、一覧取得のページネーション(performance-design.md)により応答は制限される。
