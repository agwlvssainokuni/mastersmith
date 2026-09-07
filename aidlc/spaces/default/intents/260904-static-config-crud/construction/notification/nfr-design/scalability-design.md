# Scalability Design: notification

## スケーリングアーキテクチャ

単一インスタンス構成(NFR2.1)。水平スケーリングは設計しない。

## データ量への対応

EmailDispatchは永続化されず、SMTP送信後は破棄される(entities.md)ため、蓄積によるスケーラビリティ課題は生じない。
