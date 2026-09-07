# Performance Requirements: permission

## NFR1.1: 応答速度の一般方針

requirements.md NFR1を踏襲する。厳密な数値目標は設けない。

## NFR1.2: 権限確認呼び出しの前提

dynamic-data-accessの一覧・詳細・作成・更新の各操作は、契約#3経由でpermissionへの権限確認呼び出しを同期的に伴う。permissionの応答が遅延すると業務データ操作全体の応答時間に直接波及するため、この呼び出しは同一JVM内のプロセス内呼び出し(ネットワーク境界を越えない)であることを前提とする。ネットワーク越しの別プロセス呼び出しへの変更は、性能上の再検討を要する。
