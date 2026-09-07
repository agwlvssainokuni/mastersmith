# Scalability Requirements: notification

## NFR2.1: 単一インスタンス・単一業務前提

requirements.md NFR2(1インスタンス=1業務、マルチテナンシーなし)を踏襲する。EmailDispatchは永続化されず、SMTP送信後は破棄される(entities.md)ため、蓄積によるスケーラビリティ課題は生じない。
