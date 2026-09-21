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

package com.mastersmith.configio.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.document.ConfigDocument.ColumnEntry;
import com.mastersmith.configio.document.ConfigDocument.TableEntry;
import com.mastersmith.configio.document.ConfigDocument.TranslationItem;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.permission.entity.ScopeType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 性能の確認(NFR1.1・NFR1.2・NFR1.3。JUnitのタグ{@code nfr-performance}。通常の{@code test}タスクでは、実行しない。{@code
 * ./gradlew :backend:nfrPerformanceTest --tests
 * "com.mastersmith.configio.performance.*"}で実行する。code-generation-plan.md Step
 * 21、nfr-design/performance-design.md「性能の確認の設計」)。
 *
 * <ul>
 *   <li>想定規模の上限(NFR1.3: テーブル100・カラム約3,000・翻訳約6,000・ロール50・主権限約5,000)を、{@link
 *       ConfigDocumentFactory}が、連番から機械的に生成する(業務固有の名前を使わない。NFR8.1)。
 *   <li>環境: 内部設定DBは、H2のファイルモード(一時ディレクトリ)・単一プロセス。実行環境のCPU・メモリを、結果と一緒に出力する。
 *   <li>ウォームアップ5回(捨てる)→計測30回(同時に実行するのは1件)。判定は、30回の95パーセンタイル(最近順位法。小さい方から{@code
 *       ceil(0.95×30)}=29番目)が、目標(エクスポート3秒・インポート10秒)以内。
 *       インポートは、毎回、内容を変えて(すべての翻訳の本文・すべてのテーブルとカラムの表示順が変わる。約9,000件の更新)、反映(更新)まで含める。最初の1回(空のDBへの、全件の追加)は、別に1回、計測する。
 *   <li>2件の同時(エクスポート2件・インポート1件+エクスポート1件)は、別に3回ずつ実行し、<b>3回とも目標以内</b>を合格とする(3回では、p95を定義できないため)。
 *   <li>取り込み直後の最初の読み取り(キャッシュの再読み込み。Q1=C)を10回計測し、<b>最大値が、3秒以内</b>を合格とする。
 * </ul>
 *
 * <p>位置づけ: 高負荷・性能テストではなく、{@code
 * team.md}の「確認済み品質目標の充足を検証する」に基づく、NFRの確認である。単一の利用者による繰り返しの実行であり、負荷の掛け方は含めない。
 */
@Tag("nfr-performance")
class ConfigImportExportPerformanceTest extends ConfigIoIntegrationTestBase {

  private static final long EXPORT_TARGET_MS = 3_000;
  private static final long IMPORT_TARGET_MS = 10_000;
  private static final long FIRST_READ_TARGET_MS = 3_000;
  private static final int WARMUP = 5;
  private static final int MEASURED = 30;

  private static Path databaseDirectory;

  @DynamicPropertySource
  static void fileModeDatabase(DynamicPropertyRegistry registry) throws IOException {
    databaseDirectory = Files.createTempDirectory("mastersmith-nfr-perf");
    registry.add(
        "spring.datasource.url",
        () -> "jdbc:h2:file:" + databaseDirectory.resolve("perf") + ";AUTO_SERVER=FALSE");
  }

  @Autowired private ConfigEngineApi configEngineApi;
  @Autowired private PermissionEngineApi permissionEngineApi;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  private long timeMillis(Callable<Integer> action, int expectedStatus) throws Exception {
    long started = System.nanoTime();
    int status = action.call();
    long elapsed = (System.nanoTime() - started) / 1_000_000;
    assertThat(status).isEqualTo(expectedStatus);
    return elapsed;
  }

  private Callable<Integer> exportCall() {
    return () -> mockMvc.perform(get(EXPORT)).andReturn().getResponse().getStatus();
  }

  private Callable<Integer> importCall(String json) {
    return () ->
        mockMvc
            .perform(post(IMPORT).contentType(MediaType.APPLICATION_JSON).content(json))
            .andReturn()
            .getResponse()
            .getStatus();
  }

  /** 最近順位法のp95(小さい方から{@code ceil(0.95×n)}番目)。 */
  private static long p95(List<Long> samples) {
    List<Long> sorted = new ArrayList<>(samples);
    Collections.sort(sorted);
    return sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
  }

  /** すべての翻訳の本文・すべてのテーブルとカラムの表示順を変えた、同じ規模の設定(約9,000件の更新になる)。 */
  private static ConfigDocument variant(ConfigDocument base, int shift) {
    List<TableEntry> tables =
        base.schema().tables().stream()
            .map(
                t ->
                    new TableEntry(
                        t.schemaName(),
                        t.tableName(),
                        t.displayOrder() + shift,
                        t.optimisticLockColumn(),
                        t.columns().stream()
                            .map(
                                c ->
                                    new ColumnEntry(
                                        c.columnName(),
                                        c.displayOrder() + shift,
                                        c.format(),
                                        c.editorType(),
                                        c.validationRule(),
                                        c.visibility(),
                                        c.isPrimaryKey(),
                                        c.choiceOptions(),
                                        c.fkReference()))
                            .toList()))
            .toList();
    List<TranslationItem> translations =
        base.schema().translations().stream()
            .map(t -> new TranslationItem(t.i18nKey(), t.locale(), t.text() + "_v" + shift))
            .toList();
    return new ConfigDocument(
        base.formatVersion(),
        base.exportedAt(),
        base.appVersion(),
        new ConfigDocument.SchemaSection(tables, translations),
        base.menu(),
        base.rbac());
  }

  @Test
  void exportAndImportAtTheUpperBoundMeetTheTargets() throws Exception {
    ConfigDocument base =
        ConfigDocumentFactory.withAdministrator(
            ConfigDocumentFactory.mechanical(Spec.upperBound()), ADMIN_ROLE);
    String jsonA = ConfigDocumentFactory.toJson(variant(base, 0)).toString();
    String jsonB = ConfigDocumentFactory.toJson(variant(base, 1)).toString();
    StringBuilder report = new StringBuilder();
    report.append(
        "environment: cpus=%d maxMemoryMb=%d os=%s/%s java=%s db=H2(file, single process)%n"
            .formatted(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                System.getProperty("java.version")));
    report.append(
        "scale: tables=100 columns=3000 translations=6000 roles=51 primaryPermissions=~5010 auxiliaryPermissions=~510 bodyBytes=%d%n"
            .formatted(jsonA.length()));

    // (0) 空のDBへの、最初の取り込み(全件の追加。ブートストラップ状態)。
    long coldAdd = timeMillis(importCall(jsonA), 200);
    report.append("import cold-add (empty DB, all rows added): %d ms%n".formatted(coldAdd));
    actAs("perf-admin", ADMIN_ROLE);

    // (1) エクスポート: ウォームアップ5回 → 計測30回。
    for (int i = 0; i < WARMUP; i++) {
      timeMillis(exportCall(), 200);
    }
    List<Long> exports = new ArrayList<>();
    for (int i = 0; i < MEASURED; i++) {
      exports.add(timeMillis(exportCall(), 200));
    }
    long exportP95 = p95(exports);
    report.append(
        "export x%d (warmup %d): p95=%d ms max=%d ms median=%d ms (target %d ms)%n"
            .formatted(
                MEASURED,
                WARMUP,
                exportP95,
                Collections.max(exports),
                median(exports),
                EXPORT_TARGET_MS));

    // (2) インポート: 毎回、内容を変える(約9,000件の更新)。ウォームアップ5回 → 計測30回。
    int flip = 0;
    for (int i = 0; i < WARMUP; i++) {
      timeMillis(importCall(flip++ % 2 == 0 ? jsonB : jsonA), 200);
    }
    List<Long> imports = new ArrayList<>();
    for (int i = 0; i < MEASURED; i++) {
      imports.add(timeMillis(importCall(flip++ % 2 == 0 ? jsonB : jsonA), 200));
    }
    long importP95 = p95(imports);
    report.append(
        "import x%d (warmup %d, ~9000 updates each): p95=%d ms max=%d ms median=%d ms (target %d ms)%n"
            .formatted(
                MEASURED,
                WARMUP,
                importP95,
                Collections.max(imports),
                median(imports),
                IMPORT_TARGET_MS));

    // (3) 2件の同時: エクスポート2件×3回、インポート1件+エクスポート1件×3回。3回とも、目標以内。
    List<Long> concurrentExports = new ArrayList<>();
    List<Long> concurrentImports = new ArrayList<>();
    List<Long> concurrentMixedExports = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Future<Long> a = executor.submit(() -> timeMillis(exportCall(), 200));
      Future<Long> b = executor.submit(() -> timeMillis(exportCall(), 200));
      concurrentExports.add(Math.max(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS)));
      String json = flip++ % 2 == 0 ? jsonB : jsonA;
      Future<Long> imp = executor.submit(() -> timeMillis(importCall(json), 200));
      Future<Long> exp = executor.submit(() -> timeMillis(exportCall(), 200));
      concurrentImports.add(imp.get(120, TimeUnit.SECONDS));
      concurrentMixedExports.add(exp.get(120, TimeUnit.SECONDS));
    }
    report.append(
        "concurrent 2 exports x3: max of each round=%s ms (target %d ms)%n"
            .formatted(concurrentExports, EXPORT_TARGET_MS));
    report.append(
        "concurrent import+export x3: import=%s ms (target %d ms), export=%s ms (target %d ms)%n"
            .formatted(
                concurrentImports, IMPORT_TARGET_MS, concurrentMixedExports, EXPORT_TARGET_MS));

    // (4) 取り込み直後の最初の読み取り(キャッシュの再読み込み)を、10回計測する(config-engine・permission-engineの読み取り)。
    List<Long> firstReads = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      timeMillis(importCall(flip++ % 2 == 0 ? jsonB : jsonA), 200);
      long started = System.nanoTime();
      configEngineApi.getTableConfig("schema_0000", "table_0000");
      String roleId = roleRepository.findByName(ADMIN_ROLE).orElseThrow().getRoleId();
      permissionEngineApi.canAccessScreen(roleId, "config-import-export");
      permissionEngineApi.resolveEffectivePermission(roleId, ScopeType.SCHEMA, "schema_0000");
      firstReads.add((System.nanoTime() - started) / 1_000_000);
    }
    long firstReadMax = Collections.max(firstReads);
    report.append(
        "first read after import x10 (config cache reload + permission load): max=%d ms values=%s (target %d ms)%n"
            .formatted(firstReadMax, firstReads, FIRST_READ_TARGET_MS));

    System.out.println("NFR_PERF_REPORT\n" + report);
    Path out = Path.of("build", "nfr-performance-result.txt");
    Files.createDirectories(out.getParent());
    Files.writeString(out, report.toString());

    assertThat(exportP95).as("export p95").isLessThanOrEqualTo(EXPORT_TARGET_MS);
    assertThat(importP95).as("import p95").isLessThanOrEqualTo(IMPORT_TARGET_MS);
    assertThat(concurrentExports).as("concurrent exports").allMatch(v -> v <= EXPORT_TARGET_MS);
    assertThat(concurrentImports).as("concurrent import").allMatch(v -> v <= IMPORT_TARGET_MS);
    assertThat(concurrentMixedExports)
        .as("concurrent export with import")
        .allMatch(v -> v <= EXPORT_TARGET_MS);
    assertThat(firstReadMax)
        .as("first read after import")
        .isLessThanOrEqualTo(FIRST_READ_TARGET_MS);
  }

  private static long median(List<Long> samples) {
    List<Long> sorted = new ArrayList<>(samples);
    Collections.sort(sorted);
    return sorted.get(sorted.size() / 2);
  }
}
