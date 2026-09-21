/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastersmith.schema.service;

import com.mastersmith.config.dto.ColumnDraftEntry;
import com.mastersmith.config.dto.TableConfigDraft;
import com.mastersmith.config.dto.TableDraftEntry;
import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.schema.dto.RdbmsTableMetadata;
import com.mastersmith.schema.dto.SchemaIntrospectionRequest;
import com.mastersmith.schema.dto.SchemaIntrospectionResult;
import com.mastersmith.schema.exception.SchemaDraftValidationException;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import com.mastersmith.schema.rdbms.RdbmsMetadataReader;
import com.mastersmith.schema.rdbms.RdbmsSchemaSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * W1(スキーマからドラフト生成、FR1.4、C8)のビジネスロジックを実装する(BR2.2, BR2.5, BR2.6, BR2.7, BR2.9)。
 *
 * <p>{@link RdbmsMetadataReader}が読み取った{@link RdbmsSchemaSnapshot}をconfig-engineのC9契約型{@link
 * TableConfigDraft}へ変換し、{@link ConfigEngineApi#writeTableConfigDraft}を1回だけ呼び出す。読み取りに失敗した場合は{@link
 * RdbmsMetadataReader}が送出する{@link SchemaIntrospectionException}がそのまま伝播し、{@code
 * writeTableConfigDraft}は一切呼び出されない(BR2.9、全テーブルの読み取りが完了してから1回だけ書き込みを行う設計のため部分書込みは発生しない)。
 *
 * <p>{@code writeTableConfigDraft}が{@link
 * ConfigValidationException}(config-engineの正規化表にない型のカラムがある場合など)を送出した場合は、{@link
 * SchemaDraftValidationException}(422、フィールド単位のエラー付き)へ変換する(BR2.9、レビュー指摘R-02)。{@code
 * writeTableConfigDraft}は全体が1つのトランザクションのため、この場合は、どのテーブルも取り込まれない。それ以外の実行時例外(DBの一意制約違反など)は、変換せずに伝播する(500)。
 *
 * <p>楽観ロック対象列に相当する情報(BR2.6)・NULL可否(BR2.10)は{@link
 * ColumnDraftEntry}に対応フィールドが存在しないため、変換時には設定しない。外部キー参照カラムの特別扱いも行わない(BR2.5)。
 *
 * <p>observability-design.md準拠のメトリクス(NFR5.1)を記録する: {@code
 * schema_introspection_duration_seconds}(本メソッド全体の所要時間)、{@code
 * schema_introspection_tables_total}(読み取り対象としたテーブル数)、{@code
 * schema_introspection_generated_total}(新規生成されたTableConfig件数)、{@code
 * schema_introspection_failures_total}(メタデータ読み取り・書込み失敗、403の計上はコントローラ層が担う。読み取り失敗・config-engineの拒否・書込み時の予期しない例外のいずれも計上する)。
 *
 * <p>{@link RdbmsMetadataReader}と同じ条件({@code
 * mastersmith.business-datasource.enabled=true})でのみBean化する (data-import-exportの{@code
 * CsvExportService}と同じ方針。既定値のenabled=falseのままでは、業務データ用RDBMSの 接続先が未確定の開発環境でも他Unitの起動に影響を与えない)。
 */
@Service
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class SchemaIntrospectionService {

  private final RdbmsMetadataReader metadataReader;
  private final ConfigEngineApi configEngineApi;
  private final Timer durationTimer;
  private final Counter tablesCounter;
  private final Counter generatedCounter;
  private final Counter failuresCounter;

  public SchemaIntrospectionService(
      RdbmsMetadataReader metadataReader,
      ConfigEngineApi configEngineApi,
      MeterRegistry meterRegistry) {
    this.metadataReader = metadataReader;
    this.configEngineApi = configEngineApi;
    this.durationTimer =
        Timer.builder("schema_introspection_duration_seconds")
            .description("Duration of one POST /api/config/schema-introspection execution (NFR1.1)")
            .register(meterRegistry);
    this.tablesCounter =
        Counter.builder("schema_introspection_tables_total")
            .description("Number of tables read from the target business database (cumulative)")
            .register(meterRegistry);
    this.generatedCounter =
        Counter.builder("schema_introspection_generated_total")
            .description(
                "Number of newly generated TableConfig rows (cumulative, excludes skipped tables)")
            .register(meterRegistry);
    this.failuresCounter =
        Counter.builder("schema_introspection_failures_total")
            .description("Number of failed schema-introspection attempts (403/422, cumulative)")
            .register(meterRegistry);
  }

  /**
   * 対象RDBMSからスキーマを読み取り、config-engineへ初期ドラフトを書き込む(W1)。
   *
   * @throws SchemaIntrospectionException 接続失敗・メタデータ読み取り失敗・方言判定不可の場合(BR2.9)。この場合{@code
   *     writeTableConfigDraft}は呼び出されない
   * @throws SchemaDraftValidationException
   *     config-engineが、読み取った内容を初期ドラフトとして受け付けなかった場合(BR2.9)。何も書き込まれない
   */
  public SchemaIntrospectionResult introspect(SchemaIntrospectionRequest request) {
    return durationTimer.record(() -> doIntrospect(request));
  }

  private SchemaIntrospectionResult doIntrospect(SchemaIntrospectionRequest request) {
    RdbmsSchemaSnapshot snapshot;
    try {
      snapshot = metadataReader.readSchema(request.schemaName(), request.tableNames());
    } catch (SchemaIntrospectionException e) {
      failuresCounter.increment();
      throw e;
    }
    tablesCounter.increment(snapshot.tables().size());
    TableConfigDraft draft = toDraft(snapshot);
    List<String> generatedIds;
    try {
      generatedIds = configEngineApi.writeTableConfigDraft(draft);
    } catch (ConfigValidationException e) {
      failuresCounter.increment();
      throw new SchemaDraftValidationException(e.getFieldErrors(), e);
    } catch (RuntimeException e) {
      failuresCounter.increment();
      throw e;
    }
    generatedCounter.increment(generatedIds.size());
    return new SchemaIntrospectionResult(generatedIds);
  }

  private static TableConfigDraft toDraft(RdbmsSchemaSnapshot snapshot) {
    List<TableDraftEntry> tables =
        snapshot.tables().stream().map(SchemaIntrospectionService::toEntry).toList();
    return new TableConfigDraft(snapshot.dialect(), tables);
  }

  private static TableDraftEntry toEntry(RdbmsTableMetadata table) {
    // BR2.5: rawTypeNameを正規化せずそのまま渡す。BR2.6: 楽観ロック対象列は設定しない
    // (TableDraftEntryに対応フィールドが無い)。BR2.10: nullableはColumnDraftEntryへ伝搬しない。
    List<ColumnDraftEntry> columns =
        table.columns().stream()
            .map(c -> new ColumnDraftEntry(c.columnName(), c.rawTypeName(), c.isPrimaryKey()))
            .toList();
    return new TableDraftEntry(table.schemaName(), table.tableName(), columns);
  }
}
