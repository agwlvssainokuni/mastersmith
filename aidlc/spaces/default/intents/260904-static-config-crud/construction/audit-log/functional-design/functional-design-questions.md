# Functional Design Questions: audit-log

requirements.md・units-generation・contract-designで未確定のまま残っている、audit-log固有の業務ルールを2点確定する。

## Q1. 監査ログの保持日数(n)

FR7.5は「記録からn日以上経過した監査ログを削除できること(具体的な保持日数nは後続段階で確定)」としている。functional-designで具体的な日数を確定する。

- A. 365日(1年)とする。個人利用中心のプロジェクトであり、法令上の保持義務はないため、直近1年分の操作履歴が追えれば十分(推奨)
- B. 異なる日数を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: B。固定値ではなく設定値とする。標準値(デフォルト)は365日。管理者が変更できる設定項目として扱う。

## Q2. 監査ログのエクスポート形式

FR7.4は「監査ログを管理画面からエクスポートできること。同等の操作をAPI経由でも行えること」としている。エクスポートファイルの形式を確定する。

- A. CSV形式のみとする。表計算ソフトでの二次利用を主眼とし、追加の形式は設けない(推奨)
- B. CSV・JSON両方の形式をサポートする
- X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

- 監査ログの保持日数は固定値ではなく設定値とし、標準値(デフォルト)365日、管理者が変更可能な設定項目として扱う
- エクスポート形式はCSV・JSON両方をサポートする

- Looks correct
- Request changes

[Answer]: Looks correct
