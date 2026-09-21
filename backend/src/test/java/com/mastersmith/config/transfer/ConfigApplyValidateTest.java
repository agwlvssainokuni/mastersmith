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

package com.mastersmith.config.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.dto.ConfigNaturalKeySet;
import com.mastersmith.config.dto.ConfigNaturalKeySet.Choice;
import com.mastersmith.config.dto.ConfigNaturalKeySet.Column;
import com.mastersmith.config.dto.ConfigNaturalKeySet.Fk;
import com.mastersmith.config.dto.ConfigNaturalKeySet.Table;
import com.mastersmith.config.dto.ConfigNaturalKeySet.Translation;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import com.mastersmith.config.validation.ConfigValidator;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * config-engineの、取り込みの検証({@code validateConfigSet}相当)と反映({@code
 * applyConfigSet}相当)の、実際の組込みH2・実際のJPAでのテスト(全置換・自然キーでの照合・内部IDの維持・isPrimaryKeyの維持・件数・イベント・
 * バッチの前提の挿入)。業務固有の名前は、連番から機械的に生成する(BR9.20)。
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ConfigApplyValidateTest {

  @Autowired private TableConfigRepository tableRepository;
  @Autowired private ColumnConfigRepository columnRepository;
  @Autowired private TranslationEntryRepository translationRepository;
  @Autowired private EntityManager entityManager;

  private final List<Object> published = new ArrayList<>();
  private ConfigTransfer transfer;
  private ConfigCache cache;

  @BeforeEach
  void setUp() {
    ConfigValidator validator =
        new ConfigValidator(Validation.buildDefaultValidatorFactory().getValidator());
    cache = new ConfigCache(tableRepository, columnRepository, translationRepository);
    transfer =
        new ConfigTransfer(
            tableRepository,
            columnRepository,
            translationRepository,
            validator,
            cache,
            published::add,
            TransactionOperations.withoutTransaction());
    transfer.setEntityManager(entityManager);
  }

  private static Column column(String name, int order) {
    return new Column(name, order, null, "text", Map.of(), "visible", List.of(), null);
  }

  private static Table table(String schema, String name, Column... columns) {
    return new Table(schema, name, 0, null, List.of(columns));
  }

  private static ConfigNaturalKeySet set(List<Table> tables, List<Translation> translations) {
    return new ConfigNaturalKeySet(tables, translations);
  }

  // ---- 検証 ----

  @Test
  void aValidSetHasNoErrors() {
    ConfigNaturalKeySet set =
        set(
            List.of(table("s_1", "t_1", column("c_1", 0))),
            List.of(new Translation("k_1", "ja", "text")));

    assertThat(transfer.validate(set)).isEmpty();
  }

  @Test
  void collectsEveryErrorWithItsPositionInsteadOfStoppingAtTheFirst() {
    ConfigNaturalKeySet set =
        set(
            List.of(
                new Table("s_1", null, null, null, List.of(column("c_1", 0))),
                table(
                    "s_2",
                    "t_2",
                    new Column(
                        "c_2", 0, null, "no_such_type", Map.of(), "visible", List.of(), null),
                    new Column("c_3", null, null, "text", Map.of(), "hidden", List.of(), null))),
            List.of(
                new Translation("k_1", "fr", "text"),
                new Translation(null, "ja", ""),
                new Translation("k_2", "ja", "ok")));

    List<ImportValidationError> errors = transfer.validate(set);

    assertThat(errors)
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactlyInAnyOrder(
            tuple("tables[0].displayOrder", ImportMessageKeys.FIELD_REQUIRED),
            tuple("tables[0].tableName", ImportMessageKeys.FIELD_REQUIRED),
            tuple("tables[1].columns[0].editorType", ImportMessageKeys.FIELD_VALUE_INVALID),
            tuple("tables[1].columns[1].displayOrder", ImportMessageKeys.FIELD_REQUIRED),
            tuple("translations[0].locale", ImportMessageKeys.FIELD_VALUE_INVALID),
            tuple("translations[1].i18nKey", ImportMessageKeys.FIELD_REQUIRED),
            tuple("translations[1].text", ImportMessageKeys.FIELD_REQUIRED));
  }

  @Test
  void anUnknownEditorTypeReportsTheAllowedValuesAsParams() {
    ConfigNaturalKeySet set =
        set(
            List.of(
                table(
                    "s_1",
                    "t_1",
                    new Column("c_1", 0, null, "nope", Map.of(), "visible", List.of(), null))),
            List.of());

    List<ImportValidationError> errors = transfer.validate(set);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).params()).containsKey("allowed");
    assertThat(((List<?>) errors.get(0).params().get("allowed")).contains("text")).isTrue();
    assertThat(((List<?>) errors.get(0).params().get("allowed")).contains("select")).isTrue();
  }

  @Test
  void selectEditorRequiresExactlyOneOfChoiceOptionsOrFkReference() {
    Fk fk = new Fk("s_1", "t_2", "id", "name");
    Column neither = new Column("c_1", 0, null, "select", Map.of(), "visible", List.of(), null);
    Column both =
        new Column("c_2", 0, null, "radio", Map.of(), "visible", List.of(new Choice("v", "k")), fk);
    Column onlyChoices =
        new Column(
            "c_3", 0, null, "select", Map.of(), "visible", List.of(new Choice("v", "k")), null);
    Column onlyFk = new Column("c_4", 0, null, "select", Map.of(), "visible", List.of(), fk);

    List<ImportValidationError> errors =
        transfer.validate(
            set(List.of(table("s_1", "t_1", neither, both, onlyChoices, onlyFk)), List.of()));

    assertThat(errors)
        .extracting(ImportValidationError::field)
        .containsExactlyInAnyOrder("tables[0].columns[0]", "tables[0].columns[1]");
    assertThat(errors)
        .allMatch(e -> e.message().equals(ImportMessageKeys.COLUMN_CHOICE_OR_FK_EXCLUSIVE));
  }

  @Test
  void aMissingOrUnknownVisibilityIsReportedOnce() {
    Column missing = new Column("c_1", 0, null, "text", Map.of(), null, List.of(), null);
    Column unknown = new Column("c_2", 0, null, "text", Map.of(), "half", List.of(), null);

    List<ImportValidationError> errors =
        transfer.validate(set(List.of(table("s_1", "t_1", missing, unknown)), List.of()));

    assertThat(errors)
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactlyInAnyOrder(
            tuple("tables[0].columns[0].visibility", ImportMessageKeys.FIELD_REQUIRED),
            tuple("tables[0].columns[1].visibility", ImportMessageKeys.FIELD_VALUE_INVALID));
  }

  @Test
  void validationNeverWritesAnything() {
    transfer.validate(set(List.of(table("s_1", "t_1", column("c_1", 0))), List.of()));

    assertThat(tableRepository.count()).isZero();
    assertThat(published).isEmpty();
  }

  // ---- 反映 ----

  @Test
  void addsEverythingWhenTheDatabaseIsEmpty() {
    ConfigNaturalKeySet set =
        set(
            List.of(
                table("s_1", "t_1", column("c_1", 1), column("c_2", 2)),
                table("s_1", "t_2", column("c_1", 1))),
            List.of(
                new Translation("k_1", "ja", "text_1"), new Translation("k_1", "en", "text_2")));

    ApplyResult result = transfer.apply(set);

    assertThat(tableRepository.count()).isEqualTo(2);
    assertThat(columnRepository.count()).isEqualTo(3);
    assertThat(translationRepository.count()).isEqualTo(2);
    assertThat(result.sections().get(ImportSections.SCHEMA)).isEqualTo(new SectionCounts(5, 0, 0));
    assertThat(result.sections().get(ImportSections.TRANSLATIONS))
        .isEqualTo(new SectionCounts(2, 0, 0));
  }

  @Test
  void replacesEverythingDeletingWhatIsNotInTheSetAndKeepingIdsOfMatchingItems() {
    transfer.apply(
        set(
            List.of(
                table("s_1", "t_1", column("c_1", 0), column("c_2", 0)),
                table("s_1", "t_gone", column("c_1", 0))),
            List.of(new Translation("k_keep", "ja", "a"), new Translation("k_gone", "ja", "b"))));
    entityManager.flush();
    entityManager.clear();
    String keptTableId =
        tableRepository.findBySchemaNameAndTableName("s_1", "t_1").orElseThrow().getTableConfigId();
    published.clear();

    ApplyResult result =
        transfer.apply(
            set(
                List.of(
                    table("s_1", "t_1", column("c_1", 0), column("c_3", 0)), table("s_1", "t_new")),
                List.of(
                    new Translation("k_keep", "ja", "a"), new Translation("k_new", "ja", "c"))));
    entityManager.flush();
    entityManager.clear();

    // 自然キーが一致するテーブルは、内部IDを維持する。ファイルにないものは、削除される。
    assertThat(
            tableRepository
                .findBySchemaNameAndTableName("s_1", "t_1")
                .orElseThrow()
                .getTableConfigId())
        .isEqualTo(keptTableId);
    assertThat(tableRepository.findBySchemaNameAndTableName("s_1", "t_gone")).isEmpty();
    assertThat(tableRepository.findBySchemaNameAndTableName("s_1", "t_new")).isPresent();
    assertThat(columnRepository.findByTableConfigId(keptTableId))
        .extracting(ColumnConfig::getColumnName)
        .containsExactlyInAnyOrder("c_1", "c_3");
    assertThat(translationRepository.findAll())
        .extracting(TranslationEntry::getI18nKey)
        .containsExactlyInAnyOrder("k_keep", "k_new");
    // 件数: 追加=t_new・c_3、更新=なし(値が同じ項目は数えない)、削除=t_gone・t_goneのc_1・t_1のc_2。
    assertThat(result.sections().get(ImportSections.SCHEMA)).isEqualTo(new SectionCounts(2, 0, 3));
    assertThat(result.sections().get(ImportSections.TRANSLATIONS))
        .isEqualTo(new SectionCounts(1, 0, 1));
  }

  @Test
  void unchangedItemsAreNotCountedAsUpdatesAndChangedOnesAre() {
    ConfigNaturalKeySet original =
        set(
            List.of(table("s_1", "t_1", column("c_1", 1))),
            List.of(new Translation("k", "ja", "a")));
    transfer.apply(original);
    entityManager.flush();
    entityManager.clear();

    ApplyResult same = transfer.apply(original);
    assertThat(same.sections().get(ImportSections.SCHEMA)).isEqualTo(SectionCounts.ZERO);
    assertThat(same.sections().get(ImportSections.TRANSLATIONS)).isEqualTo(SectionCounts.ZERO);

    ConfigNaturalKeySet changed =
        set(
            List.of(
                new Table(
                    "s_1",
                    "t_1",
                    5,
                    null,
                    List.of(
                        new Column(
                            "c_1",
                            2,
                            "fmt",
                            "textarea",
                            Map.of("required", true),
                            "hidden",
                            List.of(),
                            null)))),
            List.of(new Translation("k", "ja", "b")));
    ApplyResult changedResult = transfer.apply(changed);
    entityManager.flush();
    entityManager.clear();

    assertThat(changedResult.sections().get(ImportSections.SCHEMA))
        .isEqualTo(new SectionCounts(0, 2, 0));
    assertThat(changedResult.sections().get(ImportSections.TRANSLATIONS))
        .isEqualTo(new SectionCounts(0, 1, 0));
    ColumnConfig stored = columnRepository.findAll().get(0);
    assertThat(stored.getEditorType()).isEqualTo(EditorType.TEXTAREA);
    assertThat(stored.getVisibility()).isEqualTo(Visibility.HIDDEN);
    assertThat(stored.getFormat()).isEqualTo("fmt");
    assertThat(stored.getValidationRule().rules()).containsEntry("required", true);
  }

  @Test
  void overwritesAnExistingColumnConfigWhichTheOldImportCouldNot() {
    // 既知の課題(config-engineのレビュー指摘R-02): 既存のColumnConfigを上書きする経路が成立しなかった。
    TableConfig existingTable = tableRepository.save(new TableConfig("s_1", "t_1"));
    columnRepository.save(
        new ColumnConfig(existingTable.getTableConfigId(), "c_1", EditorType.TEXT));
    entityManager.flush();
    entityManager.clear();

    transfer.apply(
        set(
            List.of(
                table(
                    "s_1",
                    "t_1",
                    new Column("c_1", 4, null, "integer", Map.of(), "visible", List.of(), null))),
            List.of()));
    entityManager.flush();
    entityManager.clear();

    List<ColumnConfig> columns =
        columnRepository.findByTableConfigId(existingTable.getTableConfigId());
    assertThat(columns).hasSize(1);
    assertThat(columns.get(0).getEditorType()).isEqualTo(EditorType.INTEGER);
    assertThat(columns.get(0).getDisplayOrder()).isEqualTo(4);
  }

  @Test
  void isPrimaryKeyIsKeptForExistingColumnsAndFalseForNewOnes() {
    TableConfig existingTable = tableRepository.save(new TableConfig("s_1", "t_1"));
    columnRepository.save(
        new ColumnConfig(existingTable.getTableConfigId(), "c_pk", EditorType.TEXT, true));
    entityManager.flush();
    entityManager.clear();

    transfer.apply(
        set(List.of(table("s_1", "t_1", column("c_pk", 9), column("c_new", 1))), List.of()));
    entityManager.flush();
    entityManager.clear();

    Map<String, Boolean> primaryKeys = new HashMap<>();
    columnRepository.findAll().forEach(c -> primaryKeys.put(c.getColumnName(), c.isPrimaryKey()));
    assertThat(primaryKeys).containsEntry("c_pk", true).containsEntry("c_new", false);
  }

  @Test
  void aSameKeyDeletedAndAddedAcrossApplysDoesNotViolateTheUniqueConstraint() {
    TableConfig existingTable = tableRepository.save(new TableConfig("s_1", "t_1"));
    columnRepository.save(
        new ColumnConfig(existingTable.getTableConfigId(), "c_1", EditorType.TEXT));
    entityManager.flush();
    entityManager.clear();

    transfer.apply(set(List.of(), List.of()));
    entityManager.flush();
    transfer.apply(set(List.of(table("s_1", "t_1", column("c_1", 0))), List.of()));
    entityManager.flush();

    assertThat(tableRepository.count()).isEqualTo(1);
    assertThat(columnRepository.count()).isEqualTo(1);
  }

  @Test
  void postCommitInvalidatesTheCacheAndPublishesOneEventPerChangedEntity() {
    ApplyResult result =
        transfer.apply(
            set(
                List.of(table("s_1", "t_1", column("c_1", 0))),
                List.of(new Translation("k", "ja", "a"))));
    long generationBefore = cache.generation();

    // 反映そのものは、キャッシュに触れず、イベントも発行しない(確定後の動作として、返すだけ)。
    assertThat(published).isEmpty();
    assertThat(cache.isStale()).isFalse();

    result.postCommit().invalidateCaches().run();
    result.postCommit().publishEvents().run();

    assertThat(cache.isStale()).isTrue();
    assertThat(cache.generation()).isGreaterThan(generationBefore);
    // テーブル1件+カラム1件+翻訳1件。
    assertThat(published).hasSize(3).allMatch(e -> e instanceof ConfigChangedEvent);
    assertThat(published).map(e -> ((ConfigChangedEvent) e).beforeValue()).containsOnlyNulls();
  }

  @Test
  void deletedAndUpdatedEntitiesCarryBeforeSnapshots() {
    transfer.apply(set(List.of(table("s_1", "t_1", column("c_1", 0))), List.of()));
    entityManager.flush();
    entityManager.clear();
    published.clear();

    ApplyResult result =
        transfer.apply(set(List.of(new Table("s_1", "t_1", 7, null, List.of())), List.of()));
    result.postCommit().publishEvents().run();

    // テーブルの更新(displayOrder 0 → 7)とカラムの削除。
    assertThat(published).hasSize(2);
    ConfigChangedEvent deleted =
        published.stream()
            .map(e -> (ConfigChangedEvent) e)
            .filter(e -> e.afterValue() == null)
            .findFirst()
            .orElseThrow();
    assertThat(deleted.targetType()).isEqualTo(ConfigChangedEvent.TARGET_TYPE_COLUMN_CONFIG);
    assertThat(deleted.beforeValue()).isNotNull();
    assertThat(deleted.actor()).isEqualTo("system");
  }

  @Test
  void aFailureWhilePublishingEventsIsSwallowed() {
    ConfigTransfer failing =
        new ConfigTransfer(
            tableRepository,
            columnRepository,
            translationRepository,
            new ConfigValidator(Validation.buildDefaultValidatorFactory().getValidator()),
            cache,
            event -> {
              throw new IllegalStateException("listener down");
            },
            TransactionOperations.withoutTransaction());
    failing.setEntityManager(entityManager);
    ApplyResult result =
        failing.apply(set(List.of(table("s_1", "t_1", column("c_1", 0))), List.of()));

    result.postCommit().publishEvents().run(); // 例外が、伝わらない。
  }

  @Test
  void anEmptySetDeletesEverything() {
    transfer.apply(
        set(
            List.of(table("s_1", "t_1", column("c_1", 0))),
            List.of(new Translation("k", "ja", "a"))));
    entityManager.flush();

    ApplyResult result = transfer.apply(set(List.of(), List.of()));
    entityManager.flush();

    assertThat(tableRepository.count()).isZero();
    assertThat(columnRepository.count()).isZero();
    assertThat(translationRepository.count()).isZero();
    assertThat(result.sections().get(ImportSections.SCHEMA)).isEqualTo(new SectionCounts(0, 0, 2));
  }

  @Test
  void choiceOptionsAndFkReferencesAreStoredAndComparedByValue() {
    Column withChoices =
        new Column(
            "c_1", 0, null, "select", Map.of(), "visible", List.of(new Choice("v1", "k1")), null);
    ConfigNaturalKeySet set = set(List.of(table("s_1", "t_1", withChoices)), List.of());
    transfer.apply(set);
    entityManager.flush();
    entityManager.clear();

    ApplyResult again = transfer.apply(set);

    assertThat(again.sections().get(ImportSections.SCHEMA)).isEqualTo(SectionCounts.ZERO);
    assertThat(columnRepository.findAll().get(0).getChoiceOptions())
        .containsExactly(new ChoiceOption("v1", "k1"));
  }
}
