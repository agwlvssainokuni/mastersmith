<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Performance Design — data-import-export (U8)

`construction/data-import-export/nfr-requirements/performance-requirements.md`(NFR1.1〜NFR1.3)、および`nfr-design-questions.md`の確定回答に基づく、data-import-exportユニットの性能設計。

## ストリーミングI/O設計(NFR1.1〜NFR1.3対応)

- **エクスポート**: Spring DataのJDBC `ResultSet`をカーソルモードでオープンし(`fetchSize`を明示的に小さい値、例: 500に設定してドライバの一括先読みを防ぐ)、1行読み取るごとにApache Commons CSVの`CSVPrinter`へ書き込み、`HttpServletResponse`の出力ストリームへ直接ストリーミングする。中間バッファとしてアプリケーション側で全行を保持しない。
- **インポート**: Apache Commons CSVの`CSVParser`をストリーミングモード(`Iterable<CSVRecord>`)で使用し、1行ずつ読み取り→型変換→`validationRule`適用を行う。検証済みの行データ(変換後の値、`values: Map<String, Object>`)のみを`List<ValidatedRow>`(責務分割: バリデーション層の出力)としてメモリ上に蓄積する(`nfr-requirements-questions.md` Q8確定)。

## DBコネクションプール(NFR1.1関連、Q4確定)

- 既存のHikariCP(Spring Boot標準)を専用プールを設けずそのまま共有する。
- 大量データのエクスポート(カーソルオープン中は接続を保持し続ける)・インポート(1トランザクションで長時間接続を占有しうる)を踏まえ、以下をCode Generation時に確定するパラメータとして明記する:
  - `spring.datasource.hikari.connection-timeout`: エクスポート・インポートの処理時間目標(NFR1.1: 5分、NFR1.2: 2分)を考慮し、通常のWebリクエスト用の短いタイムアウトとは別に、長時間処理を許容する値を検討する
  - `spring.datasource.hikari.maximum-pool-size`: NFR3(同時接続最大50ユーザー)を踏まえ、大量データ処理用の接続占有が他のリクエストを枯渇させないよう監視する(専用プール分離はしないため、上限値の調整で対応する)

## コンポーネント分割(Q6確定)

性能に直結する処理を以下の4責務に分離する(詳細は`logical-components.md`参照):

1. **CSV I/O層**(`CsvReader`/`CsvWriter`): Apache Commons CSVのストリーミングAPIをラップ
2. **バリデーション層**(`RowValidator`): config-engineの`validationRule`を適用し、型変換エラーを検出
3. **一時バッファ管理**(`ImportRowBuffer`): 検証済み行データ(`List<ValidatedRow>`)をメモリ上に保持
4. **コミット・監査層**(`ImportCommitter`): 1トランザクションでのDB反映、`ImportExecutedEvent`発行

この分離により、性能ボトルネックが発生した場合に責務単位でプロファイリング・最適化しやすくする。

## Performance Anti-Requirements(除外事項、`performance-requirements.md`から継承)

- 無制限の行数への対応は設計しない。NFR1想定規模(最大10万行)を前提とする。
- キャッシュ層は設けない(本ユニットは1回限りの実行処理であり、繰り返し参照されるデータを持たない)。
