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

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.dto.ColumnConfigSnapshot;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.ConfigNaturalKeySet;
import com.mastersmith.config.dto.TableConfigSnapshot;
import com.mastersmith.config.dto.TranslationSnapshot;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.entity.TranslationEntryId;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.event.ConfigChangeOperation;
import com.mastersmith.config.event.ConfigChangeSnapshots;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.config.exception.FieldError;
import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.model.FkReference;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import com.mastersmith.config.validation.ConfigValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * config-engineの、設定一式のエクスポート・取り込みの検証・取り込みの反映(C9の{@code getExportableConfigSet}・{@code
 * validateConfigSet}・{@code applyConfigSet}の実体。config-import-export functional-spec.md
 * 追補一覧2番、logical-components.md 追補1〜5)。
 *
 * <ul>
 *   <li><b>エクスポート</b>: キャッシュを介さず、内部設定DBから直接読み、他から変更できないスナップショットを返す(NFR4.4)。
 *   <li><b>検証</b>: 何も反映せず、config-engineの既存の規則(BR1.1〜BR1.4)を、全件を集める形で再利用し、誤りの一覧を返す(BR9.10)。
 *   <li><b>反映</b>(全置換、BR9.9): 自然キーで既存の項目と照合し、ファイルにない項目を削除(削除→追加・更新の順、段階ごとに{@code
 *       flush()})し、既存の項目は内部IDを維持して更新する(BR9.14)。 {@code
 *       isPrimaryKey}は、既存では現在の値を維持し、新規ではfalse(BR1.14)。値が変わらない項目は、「更新」に数えない。挿入は{@link
 *       EntityManager#persist}で行い(採番済みのIDを持つ新規エンティティを、 Spring Data JPAの{@code
 *       save}で保存すると、挿入の前に1件ずつSELECTが走り、バッチが効かない。{@code
 *       H2/HibernateBatchProbeTest}で確認)、JDBCのバッチにまとめる。
 * </ul>
 *
 * <p>反映は、キャッシュに触れない。確定後の動作({@link PostCommit})として、キャッシュの無効化と、変更されたエンティティごとの個別の{@link
 * ConfigChangedEvent}の発行(1つの独立したトランザクションの中で、 例外を握りつぶす)を返す。
 */
@Component
public class ConfigTransfer {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigTransfer.class);

  /** 翻訳の言語の許容値(MVPは日本語・英語。config-import-export entities.md TranslationItem.locale)。 */
  static final List<String> ALLOWED_LOCALES = List.of("ja", "en");

  private final TableConfigRepository tableConfigRepository;
  private final ColumnConfigRepository columnConfigRepository;
  private final TranslationEntryRepository translationEntryRepository;
  private final ConfigValidator configValidator;
  private final ConfigCache cache;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionOperations eventTransaction;

  @PersistenceContext private EntityManager entityManager;

  /** Springが用いるコンストラクター。 */
  @Autowired
  public ConfigTransfer(
      TableConfigRepository tableConfigRepository,
      ColumnConfigRepository columnConfigRepository,
      TranslationEntryRepository translationEntryRepository,
      ConfigValidator configValidator,
      ConfigCache cache,
      ApplicationEventPublisher eventPublisher,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
    this(
        tableConfigRepository,
        columnConfigRepository,
        translationEntryRepository,
        configValidator,
        cache,
        eventPublisher,
        requiresNew(transactionManager));
  }

  /** 確定後のイベントの発行のトランザクションを指定するコンストラクター(テスト用)。 */
  public ConfigTransfer(
      TableConfigRepository tableConfigRepository,
      ColumnConfigRepository columnConfigRepository,
      TranslationEntryRepository translationEntryRepository,
      ConfigValidator configValidator,
      ConfigCache cache,
      ApplicationEventPublisher eventPublisher,
      TransactionOperations eventTransaction) {
    this.tableConfigRepository = tableConfigRepository;
    this.columnConfigRepository = columnConfigRepository;
    this.translationEntryRepository = translationEntryRepository;
    this.configValidator = configValidator;
    this.cache = cache;
    this.eventPublisher = eventPublisher;
    this.eventTransaction = eventTransaction;
  }

  /** {@link EntityManager}を差し替える(単体テスト用)。 */
  public void setEntityManager(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  // ---- エクスポート ----

  /** 内部設定DBから、全設定を直接読み、他から変更できないスナップショットにして返す。 */
  public ConfigExportSet export() {
    return new ConfigExportSet(
        tableConfigRepository.findAll().stream().map(TableConfigSnapshot::of).toList(),
        columnConfigRepository.findAll().stream().map(ColumnConfigSnapshot::of).toList(),
        translationEntryRepository.findAll().stream().map(TranslationSnapshot::of).toList());
  }

  // ---- 検証 ----

  /** 何も反映せず、誤りの一覧を返す(全件を集める。誤りがなければ空)。 */
  public List<ImportValidationError> validate(ConfigNaturalKeySet set) {
    List<ImportValidationError> errors = new ArrayList<>();
    List<ConfigNaturalKeySet.Table> tables = set.tables();
    for (int i = 0; i < tables.size(); i++) {
      validateTable(tables.get(i), "tables[" + i + "]", errors);
    }
    List<ConfigNaturalKeySet.Translation> translations = set.translations();
    for (int i = 0; i < translations.size(); i++) {
      validateTranslation(translations.get(i), "translations[" + i + "]", errors);
    }
    return errors;
  }

  private void validateTable(
      ConfigNaturalKeySet.Table table, String path, List<ImportValidationError> errors) {
    if (table.displayOrder() == null) {
      errors.add(
          ImportValidationError.of(path + ".displayOrder", ImportMessageKeys.FIELD_REQUIRED));
    }
    TableConfig entity =
        new TableConfig(
            "validation-only",
            table.schemaName(),
            table.tableName(),
            table.displayOrder() == null ? 0 : table.displayOrder(),
            table.optimisticLockColumn());
    for (FieldError violation : configValidator.propertyErrors(entity)) {
      errors.add(toError(path + "." + violation.field(), violation.ruleType()));
    }
    List<ConfigNaturalKeySet.Column> columns = table.columns();
    for (int j = 0; j < columns.size(); j++) {
      validateColumn(columns.get(j), path + ".columns[" + j + "]", errors);
    }
  }

  private void validateColumn(
      ConfigNaturalKeySet.Column column, String path, List<ImportValidationError> errors) {
    if (column.displayOrder() == null) {
      errors.add(
          ImportValidationError.of(path + ".displayOrder", ImportMessageKeys.FIELD_REQUIRED));
    }
    EditorType editorType = parseEditorType(column.editorType());
    boolean editorTypeReported = false;
    if (column.editorType() != null && !column.editorType().isBlank() && editorType == null) {
      errors.add(
          ImportValidationError.of(
              path + ".editorType",
              ImportMessageKeys.FIELD_VALUE_INVALID,
              Map.of("allowed", allowedEditorTypes())));
      editorTypeReported = true;
    }
    Visibility visibility = parseVisibility(column.visibility());
    if (column.visibility() == null || column.visibility().isBlank()) {
      errors.add(ImportValidationError.of(path + ".visibility", ImportMessageKeys.FIELD_REQUIRED));
    } else if (visibility == null) {
      errors.add(
          ImportValidationError.of(
              path + ".visibility",
              ImportMessageKeys.FIELD_VALUE_INVALID,
              Map.of("allowed", allowedVisibilities())));
    }
    ColumnConfig entity = toEntity("validation-only", column, editorType, visibility);
    for (FieldError violation : configValidator.propertyErrors(entity)) {
      // 許容されない値は、すでに報告した(editorTypeがnullになった理由の重複を避ける)。
      if (editorTypeReported && "editorType".equals(violation.field())) {
        continue;
      }
      // visibilityは、上で、必須・許容値を報告した(列挙が解決できないときのnullによる重複を避ける)。
      if ("visibility".equals(violation.field())) {
        continue;
      }
      String propertyPath = violation.field();
      errors.add(
          toError(propertyPath.isEmpty() ? path : path + "." + propertyPath, violation.ruleType()));
    }
  }

  private static void validateTranslation(
      ConfigNaturalKeySet.Translation translation,
      String path,
      List<ImportValidationError> errors) {
    if (translation.i18nKey() == null || translation.i18nKey().isBlank()) {
      errors.add(ImportValidationError.of(path + ".i18nKey", ImportMessageKeys.FIELD_REQUIRED));
    }
    if (translation.locale() == null || translation.locale().isBlank()) {
      errors.add(ImportValidationError.of(path + ".locale", ImportMessageKeys.FIELD_REQUIRED));
    } else if (!ALLOWED_LOCALES.contains(translation.locale())) {
      errors.add(
          ImportValidationError.of(
              path + ".locale",
              ImportMessageKeys.FIELD_VALUE_INVALID,
              Map.of("allowed", ALLOWED_LOCALES)));
    }
    if (translation.text() == null || translation.text().isBlank()) {
      errors.add(ImportValidationError.of(path + ".text", ImportMessageKeys.FIELD_REQUIRED));
    }
  }

  private static ImportValidationError toError(String field, String ruleType) {
    return switch (ruleType) {
      case "required" -> ImportValidationError.of(field, ImportMessageKeys.FIELD_REQUIRED);
      case "choiceOrFkExclusive" ->
          ImportValidationError.of(field, ImportMessageKeys.COLUMN_CHOICE_OR_FK_EXCLUSIVE);
      default -> ImportValidationError.of(field, ImportMessageKeys.FIELD_VALUE_INVALID);
    };
  }

  private static List<String> allowedEditorTypes() {
    return Arrays.stream(EditorType.values()).map(EditorType::toWireValue).toList();
  }

  private static List<String> allowedVisibilities() {
    return Arrays.stream(Visibility.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList();
  }

  private static EditorType parseEditorType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return EditorType.fromWireValue(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Visibility parseVisibility(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Visibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** 入力の1カラムから、検証用・追加用のColumnConfigを組み立てる(内部IDは、コンストラクターで採番される)。 */
  private static ColumnConfig toEntity(
      String tableConfigId,
      ConfigNaturalKeySet.Column column,
      EditorType editorType,
      Visibility visibility) {
    ColumnConfig entity = new ColumnConfig(tableConfigId, column.columnName(), editorType, false);
    entity.setDisplayOrder(column.displayOrder() == null ? 0 : column.displayOrder());
    entity.setFormat(column.format());
    entity.setVisibility(visibility);
    entity.setValidationRule(new ValidationRule(column.validationRule()));
    entity.setChoiceOptions(toChoiceOptions(column.choiceOptions()));
    entity.setFkReference(toFkReference(column.fkReference()));
    return entity;
  }

  private static List<ChoiceOption> toChoiceOptions(List<ConfigNaturalKeySet.Choice> choices) {
    return choices.stream().map(c -> new ChoiceOption(c.value(), c.i18nKey())).toList();
  }

  private static FkReference toFkReference(ConfigNaturalKeySet.Fk fk) {
    if (fk == null) {
      return null;
    }
    return new FkReference(
        fk.referencedSchemaName(),
        fk.referencedTableName(),
        fk.referencedValueColumnName(),
        fk.referencedLabelColumnName());
  }

  // ---- 反映 ----

  private record TableKey(String schemaName, String tableName) {}

  /** 反映の件数の、可変の集計。 */
  private static final class Counter {
    int added;
    int updated;
    int deleted;

    SectionCounts toCounts() {
      return new SectionCounts(added, updated, deleted);
    }
  }

  /**
   * 呼び出し元のトランザクションの中で、全置換で反映する(伝播は、呼び出し元の{@code ConfigModelStore}の{@code
   * MANDATORY})。自身ではコミットせず、キャッシュに触れない。
   */
  public ApplyResult apply(ConfigNaturalKeySet set) {
    Objects.requireNonNull(entityManager, "entityManager");
    List<TableConfig> existingTables = tableConfigRepository.findAll();
    List<ColumnConfig> existingColumns = columnConfigRepository.findAll();
    List<TranslationEntry> existingTranslations = translationEntryRepository.findAll();

    Map<TableKey, TableConfig> tableByKey = new HashMap<>();
    Map<String, TableConfig> tableById = new HashMap<>();
    for (TableConfig table : existingTables) {
      tableByKey.put(new TableKey(table.getSchemaName(), table.getTableName()), table);
      tableById.put(table.getTableConfigId(), table);
    }
    Map<String, Map<String, ColumnConfig>> columnsByTableId = new HashMap<>();
    for (ColumnConfig column : existingColumns) {
      columnsByTableId
          .computeIfAbsent(column.getTableConfigId(), k -> new HashMap<>())
          .put(column.getColumnName(), column);
    }
    Map<TranslationEntryId, TranslationEntry> translationById = new HashMap<>();
    for (TranslationEntry translation : existingTranslations) {
      translationById.put(translation.getId(), translation);
    }

    // 入力側の自然キーの集合。
    Map<TableKey, Set<String>> desiredColumns = new HashMap<>();
    for (ConfigNaturalKeySet.Table table : set.tables()) {
      Set<String> names = new HashSet<>();
      table.columns().forEach(c -> names.add(c.columnName()));
      desiredColumns.put(new TableKey(table.schemaName(), table.tableName()), names);
    }
    Set<TranslationEntryId> desiredTranslations = new HashSet<>();
    set.translations()
        .forEach(t -> desiredTranslations.add(new TranslationEntryId(t.i18nKey(), t.locale())));

    List<ConfigChangedEvent> events = new ArrayList<>();
    Counter schema = new Counter();
    Counter translations = new Counter();

    // (1) 削除: カラム → テーブル → 翻訳(ファイルにないもの)。段階の終わりに、flushする。
    for (ColumnConfig column : existingColumns) {
      TableConfig table = tableById.get(column.getTableConfigId());
      Set<String> names =
          table == null
              ? null
              : desiredColumns.get(new TableKey(table.getSchemaName(), table.getTableName()));
      if (names == null || !names.contains(column.getColumnName())) {
        events.add(
            event(
                ConfigChangedEvent.TARGET_TYPE_COLUMN_CONFIG,
                column.getColumnConfigId(),
                ConfigChangeSnapshots.of(column),
                null));
        entityManager.remove(column);
        schema.deleted++;
      }
    }
    for (TableConfig table : existingTables) {
      if (!desiredColumns.containsKey(new TableKey(table.getSchemaName(), table.getTableName()))) {
        events.add(
            event(
                ConfigChangedEvent.TARGET_TYPE_TABLE_CONFIG,
                table.getTableConfigId(),
                ConfigChangeSnapshots.of(table),
                null));
        entityManager.remove(table);
        schema.deleted++;
      }
    }
    for (TranslationEntry translation : existingTranslations) {
      if (!desiredTranslations.contains(translation.getId())) {
        events.add(
            event(
                ConfigChangedEvent.TARGET_TYPE_TRANSLATION_ENTRY,
                translationEventId(translation.getI18nKey(), translation.getLocale()),
                ConfigChangeSnapshots.of(translation),
                null));
        entityManager.remove(translation);
        translations.deleted++;
      }
    }
    entityManager.flush();

    // (2) 追加・更新: テーブル・カラム → 翻訳。
    for (ConfigNaturalKeySet.Table input : set.tables()) {
      TableConfig table =
          upsertTable(
              tableByKey.get(new TableKey(input.schemaName(), input.tableName())),
              input,
              schema,
              events);
      Map<String, ColumnConfig> existingOfTable =
          columnsByTableId.getOrDefault(table.getTableConfigId(), Map.of());
      for (ConfigNaturalKeySet.Column column : input.columns()) {
        upsertColumn(table, existingOfTable.get(column.columnName()), column, schema, events);
      }
    }
    for (ConfigNaturalKeySet.Translation input : set.translations()) {
      upsertTranslation(
          translationById.get(new TranslationEntryId(input.i18nKey(), input.locale())),
          input,
          translations,
          events);
    }
    entityManager.flush();

    Map<String, SectionCounts> sections = new LinkedHashMap<>();
    sections.put(ImportSections.SCHEMA, schema.toCounts());
    sections.put(ImportSections.TRANSLATIONS, translations.toCounts());
    return new ApplyResult(sections, new PostCommit(cache::invalidate, publisher(events)));
  }

  private TableConfig upsertTable(
      TableConfig existing,
      ConfigNaturalKeySet.Table input,
      Counter counter,
      List<ConfigChangedEvent> events) {
    int order = input.displayOrder();
    if (existing == null) {
      TableConfig created = new TableConfig(input.schemaName(), input.tableName());
      created.setDisplayOrder(order);
      created.setOptimisticLockColumn(input.optimisticLockColumn());
      entityManager.persist(created);
      counter.added++;
      events.add(
          event(
              ConfigChangedEvent.TARGET_TYPE_TABLE_CONFIG,
              created.getTableConfigId(),
              null,
              ConfigChangeSnapshots.of(created)));
      return created;
    }
    if (existing.getDisplayOrder() != order
        || !Objects.equals(existing.getOptimisticLockColumn(), input.optimisticLockColumn())) {
      Map<String, Object> before = ConfigChangeSnapshots.of(existing);
      existing.setDisplayOrder(order);
      existing.setOptimisticLockColumn(input.optimisticLockColumn());
      counter.updated++;
      events.add(
          event(
              ConfigChangedEvent.TARGET_TYPE_TABLE_CONFIG,
              existing.getTableConfigId(),
              before,
              ConfigChangeSnapshots.of(existing)));
    }
    return existing;
  }

  private void upsertColumn(
      TableConfig table,
      ColumnConfig existing,
      ConfigNaturalKeySet.Column input,
      Counter counter,
      List<ConfigChangedEvent> events) {
    EditorType editorType = parseEditorType(input.editorType());
    Visibility visibility = parseVisibility(input.visibility());
    if (existing == null) {
      ColumnConfig created = toEntity(table.getTableConfigId(), input, editorType, visibility);
      entityManager.persist(created);
      counter.added++;
      events.add(
          event(
              ConfigChangedEvent.TARGET_TYPE_COLUMN_CONFIG,
              created.getColumnConfigId(),
              null,
              ConfigChangeSnapshots.of(created)));
      return;
    }
    ColumnConfig desired = toEntity(table.getTableConfigId(), input, editorType, visibility);
    if (sameColumnSettings(existing, desired)) {
      return;
    }
    Map<String, Object> before = ConfigChangeSnapshots.of(existing);
    // isPrimaryKeyは、既存のカラムでは現在の値を維持する(BR1.14)。ここでは、設定できる項目だけを更新する。
    existing.setDisplayOrder(desired.getDisplayOrder());
    existing.setFormat(desired.getFormat());
    existing.setEditorType(desired.getEditorType());
    existing.setValidationRule(desired.getValidationRule());
    existing.setVisibility(desired.getVisibility());
    existing.setChoiceOptions(desired.getChoiceOptions());
    existing.setFkReference(desired.getFkReference());
    counter.updated++;
    events.add(
        event(
            ConfigChangedEvent.TARGET_TYPE_COLUMN_CONFIG,
            existing.getColumnConfigId(),
            before,
            ConfigChangeSnapshots.of(existing)));
  }

  /** 値が変わらないカラムは、「更新」に数えない(BR9.9)。nullと空の集合は、同じとみなす。 */
  private static boolean sameColumnSettings(ColumnConfig existing, ColumnConfig desired) {
    return existing.getDisplayOrder() == desired.getDisplayOrder()
        && Objects.equals(existing.getFormat(), desired.getFormat())
        && existing.getEditorType() == desired.getEditorType()
        && existing.getVisibility() == desired.getVisibility()
        && Objects.equals(
            emptyIfNull(existing.getValidationRule()), emptyIfNull(desired.getValidationRule()))
        && Objects.equals(
            emptyIfNull(existing.getChoiceOptions()), emptyIfNull(desired.getChoiceOptions()))
        && Objects.equals(existing.getFkReference(), desired.getFkReference());
  }

  private static ValidationRule emptyIfNull(ValidationRule rule) {
    return rule == null ? ValidationRule.empty() : rule;
  }

  private static List<ChoiceOption> emptyIfNull(List<ChoiceOption> options) {
    return options == null ? List.of() : options;
  }

  private void upsertTranslation(
      TranslationEntry existing,
      ConfigNaturalKeySet.Translation input,
      Counter counter,
      List<ConfigChangedEvent> events) {
    String eventId = translationEventId(input.i18nKey(), input.locale());
    if (existing == null) {
      TranslationEntry created =
          new TranslationEntry(input.i18nKey(), input.locale(), input.text());
      entityManager.persist(created);
      counter.added++;
      events.add(
          event(
              ConfigChangedEvent.TARGET_TYPE_TRANSLATION_ENTRY,
              eventId,
              null,
              ConfigChangeSnapshots.of(created)));
    } else if (!Objects.equals(existing.getText(), input.text())) {
      Map<String, Object> before = ConfigChangeSnapshots.of(existing);
      existing.setText(input.text());
      counter.updated++;
      events.add(
          event(
              ConfigChangedEvent.TARGET_TYPE_TRANSLATION_ENTRY,
              eventId,
              before,
              ConfigChangeSnapshots.of(existing)));
    }
  }

  private static String translationEventId(String i18nKey, String locale) {
    return "%s:%s".formatted(i18nKey, locale);
  }

  private static ConfigChangedEvent event(
      String targetType, String targetId, Object before, Object after) {
    return ConfigChangedEvent.of(
        ConfigChangeOperation.CONFIG_SET_IMPORTED, targetType, targetId, before, after, "system");
  }

  /** 個別の変更イベントを、独立した1つのトランザクションの中で発行する(受信側の書き込みを、確実に確定させるため)。例外は握りつぶし、ERRORのログだけに残す。 */
  private Runnable publisher(List<ConfigChangedEvent> events) {
    List<ConfigChangedEvent> pending = List.copyOf(events);
    return () -> {
      if (pending.isEmpty()) {
        return;
      }
      try {
        eventTransaction.executeWithoutResult(
            status -> pending.forEach(eventPublisher::publishEvent));
      } catch (RuntimeException e) {
        LOG.error(
            "event=config.import.event-publish-failed unit=config-engine cause={}",
            e.getClass().getName());
      }
    };
  }
}
