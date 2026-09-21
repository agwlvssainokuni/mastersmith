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

package com.mastersmith.permission.rbacio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.dto.RbacImportSet.Auxiliary;
import com.mastersmith.permission.dto.RbacImportSet.Primary;
import com.mastersmith.permission.dto.RbacImportSet.Role;
import com.mastersmith.permission.dto.RbacImportSet.Scope;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 権限昇格の判定(BR9.11)・主権限が0件の拒否(BR9.12)の、権限マトリクスのテスト(表形式。team.md Q6の追加の合格条件、Testing Contractの例外:
 * <b>実装に先立って</b>ケースを洗い出した、 ATDD寄りのテスト。code-generation-plan.md Step 4)。
 *
 * <p>ケースの軸: 操作者のロールの実効権限(FULL・READ・NONE・指定なし。スコープの階層継承(COLUMN→TABLE→SCHEMA)と、下位での上書き・拒否を含む) ×
 * 取り込むエントリの権限 × スコープ(SCHEMA・TABLE・COLUMN) × 補助権限(作成・削除。TABLE→SCHEMAの継承、独立した解決) × ブートストラップ状態(あり・なし)
 * × 取り込みで新規に作るテーブル・カラム(仮の識別。新規は、上位(スキーマ)の設定に、フォールバックする)。
 *
 * <p>期待値は、(1)人間が、権限の規則(rules.md
 * BR3.4・BR3.5・BR3.6・BR3.8・BR3.13)から、1件ずつ決めた「ゴールデン表」と、(2)実装とは独立に、階層の探索を書いた「オラクル」による全組み合わせの、2つで与える。
 */
class RbacImportValidationMatrixTest {

  private static final String ACTOR = "actor-role";
  private static final String SCHEMA = "schema_0001";
  private static final String TABLE_ID = "table-id-0001";
  private static final String COLUMN_ID = "column-id-0001";

  private static final Scope SCHEMA_SCOPE = new Scope(ScopeType.SCHEMA, SCHEMA, null, null);
  private static final Scope TABLE_EXISTING = new Scope(ScopeType.TABLE, SCHEMA, TABLE_ID, null);
  private static final Scope TABLE_NEW = new Scope(ScopeType.TABLE, SCHEMA, null, null);
  private static final Scope COLUMN_EXISTING =
      new Scope(ScopeType.COLUMN, SCHEMA, TABLE_ID, COLUMN_ID);
  private static final Scope COLUMN_NEW_IN_EXISTING_TABLE =
      new Scope(ScopeType.COLUMN, SCHEMA, TABLE_ID, null);
  private static final Scope COLUMN_IN_NEW_TABLE = new Scope(ScopeType.COLUMN, SCHEMA, null, null);

  // ---- 部品 ----

  /** 操作者の、主権限・補助権限の割当(スコープの位置と、その値)。 */
  private record ActorGrants(
      String label, List<PrimaryPermission> primary, List<AuxiliaryPermission> auxiliary) {
    @Override
    public String toString() {
      return label;
    }
  }

  private static PrimaryPermission primary(ScopeType type, String ref, PermissionLevel level) {
    return new PrimaryPermission(ACTOR, type, ref, level);
  }

  private static AuxiliaryPermission auxiliary(
      ScopeType type, String ref, Boolean create, Boolean delete) {
    return new AuxiliaryPermission(ACTOR, type, ref, create, delete);
  }

  private static ActorGrants primaryGrants(String label, PrimaryPermission... rows) {
    return new ActorGrants(label, List.of(rows), List.of());
  }

  private static ActorGrants auxGrants(String label, AuxiliaryPermission... rows) {
    return new ActorGrants(label, List.of(), List.of(rows));
  }

  private static RbacImportValidator validator(ActorGrants grants) {
    PrimaryPermissionRepository primaryRepository = mock(PrimaryPermissionRepository.class);
    AuxiliaryPermissionRepository auxiliaryRepository = mock(AuxiliaryPermissionRepository.class);
    when(primaryRepository.findByRoleId(ACTOR)).thenReturn(grants.primary());
    when(auxiliaryRepository.findByRoleId(ACTOR)).thenReturn(grants.auxiliary());
    return new RbacImportValidator(primaryRepository, auxiliaryRepository);
  }

  private static RbacImportSet primarySet(Scope scope, PermissionLevel level) {
    return new RbacImportSet(
        List.of(new Role("target-role")),
        List.of(),
        List.of(new Primary("target-role", scope, level)),
        List.of());
  }

  private static RbacImportSet auxiliarySet(Scope scope, Boolean create, Boolean delete) {
    // 主権限は、昇格にならない(NONE)1件を、必ず持たせる(主権限が0件の拒否(BR9.12)と、切り離す)。
    return new RbacImportSet(
        List.of(new Role("target-role")),
        List.of(),
        List.of(new Primary("target-role", SCHEMA_SCOPE, PermissionLevel.NONE)),
        List.of(new Auxiliary("target-role", scope, create, delete)));
  }

  private static List<String> escalationFields(List<ImportValidationError> errors) {
    return errors.stream()
        .filter(e -> ImportMessageKeys.RBAC_ESCALATION.equals(e.message()))
        .map(ImportValidationError::field)
        .toList();
  }

  // ---- オラクル(実装とは独立に、階層の探索を、素直に書いたもの) ----

  /** スコープの位置から、上位へ向かう探索の列(COLUMN → TABLE → SCHEMA)。未確定(仮の識別)の階層は、飛ばす。 */
  private static List<String[]> chain(Scope scope) {
    List<String[]> chain = new ArrayList<>();
    if (scope.scopeType() == ScopeType.COLUMN && scope.columnConfigId() != null) {
      chain.add(new String[] {"COLUMN", scope.columnConfigId()});
    }
    if (scope.scopeType() != ScopeType.SCHEMA && scope.tableConfigId() != null) {
      chain.add(new String[] {"TABLE", scope.tableConfigId()});
    }
    chain.add(new String[] {"SCHEMA", scope.schemaName()});
    return chain;
  }

  private static PermissionLevel oracleLevel(ActorGrants grants, Scope scope) {
    for (String[] position : chain(scope)) {
      for (PrimaryPermission row : grants.primary()) {
        if (row.getScopeType().name().equals(position[0])
            && row.getScopeRef().equals(position[1])) {
          return row.getLevel();
        }
      }
    }
    return PermissionLevel.NONE;
  }

  /** 補助権限の、1つの項目の、実効値(TABLE → SCHEMAの順に、最初にnullでない値。COLUMNは対象外。なければfalse)。 */
  private static boolean oracleAuxiliary(ActorGrants grants, Scope scope, boolean create) {
    for (String[] position : chain(scope)) {
      if (position[0].equals("COLUMN")) {
        continue;
      }
      for (AuxiliaryPermission row : grants.auxiliary()) {
        if (row.getScopeType().name().equals(position[0])
            && row.getScopeRef().equals(position[1])) {
          Boolean value = create ? row.getCreateAllowed() : row.getDeleteAllowed();
          if (value != null) {
            return value;
          }
        }
      }
    }
    return false;
  }

  // ---- ケースの表 ----

  private static List<ActorGrants> primaryGrantPatterns() {
    return List.of(
        primaryGrants("G0:指定なし"),
        primaryGrants("G1:SCHEMA=READ", primary(ScopeType.SCHEMA, SCHEMA, PermissionLevel.READ)),
        primaryGrants("G2:SCHEMA=FULL", primary(ScopeType.SCHEMA, SCHEMA, PermissionLevel.FULL)),
        primaryGrants("G3:TABLE=FULL", primary(ScopeType.TABLE, TABLE_ID, PermissionLevel.FULL)),
        primaryGrants("G4:TABLE=READ", primary(ScopeType.TABLE, TABLE_ID, PermissionLevel.READ)),
        primaryGrants(
            "G5:SCHEMA=FULL,TABLE=NONE(下位の拒否)",
            primary(ScopeType.SCHEMA, SCHEMA, PermissionLevel.FULL),
            primary(ScopeType.TABLE, TABLE_ID, PermissionLevel.NONE)),
        primaryGrants(
            "G6:SCHEMA=READ,TABLE=FULL,COLUMN=NONE",
            primary(ScopeType.SCHEMA, SCHEMA, PermissionLevel.READ),
            primary(ScopeType.TABLE, TABLE_ID, PermissionLevel.FULL),
            primary(ScopeType.COLUMN, COLUMN_ID, PermissionLevel.NONE)),
        primaryGrants(
            "G7:TABLE=READ,COLUMN=FULL(下位の引き上げ)",
            primary(ScopeType.TABLE, TABLE_ID, PermissionLevel.READ),
            primary(ScopeType.COLUMN, COLUMN_ID, PermissionLevel.FULL)),
        primaryGrants(
            "G8:SCHEMA=FULL,COLUMN=READ",
            primary(ScopeType.SCHEMA, SCHEMA, PermissionLevel.FULL),
            primary(ScopeType.COLUMN, COLUMN_ID, PermissionLevel.READ)));
  }

  private static List<Scope> allScopes() {
    return List.of(
        SCHEMA_SCOPE,
        TABLE_EXISTING,
        TABLE_NEW,
        COLUMN_EXISTING,
        COLUMN_NEW_IN_EXISTING_TABLE,
        COLUMN_IN_NEW_TABLE);
  }

  static Stream<Arguments> primaryCartesianCases() {
    List<Arguments> cases = new ArrayList<>();
    for (ActorGrants grants : primaryGrantPatterns()) {
      for (Scope scope : allScopes()) {
        for (PermissionLevel level : PermissionLevel.values()) {
          for (boolean bootstrap : new boolean[] {false, true}) {
            boolean expectEscalation =
                !bootstrap && level.rank() > oracleLevel(grants, scope).rank();
            cases.add(Arguments.of(grants, scope, level, bootstrap, expectEscalation));
          }
        }
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "[{index}] 操作者={0} 対象={1} 割当={2} 初期状態={3} → 昇格={4}")
  @MethodSource("primaryCartesianCases")
  void primaryPermissionEscalationFollowsTheScopeHierarchyForEveryCombination(
      ActorGrants grants,
      Scope scope,
      PermissionLevel level,
      boolean bootstrap,
      boolean expectEscalation) {
    List<ImportValidationError> errors =
        validator(grants).validate(primarySet(scope, level), ACTOR, bootstrap);

    assertThat(escalationFields(errors))
        .isEqualTo(expectEscalation ? List.of("primaryPermissions[0]") : List.of());
  }

  /** ゴールデン表: 権限の規則から、人間が、1件ずつ決めた期待値。 */
  static Stream<Arguments> primaryGoldenCases() {
    Object[][] rows = {
      // {操作者の割当の番号, 対象, 割当のレベル, ブートストラップ, 昇格か}
      {0, SCHEMA_SCOPE, PermissionLevel.FULL, false, true},
      {0, SCHEMA_SCOPE, PermissionLevel.READ, false, true},
      {0, SCHEMA_SCOPE, PermissionLevel.NONE, false, false},
      // G1: SCHEMA=READ。下位のTABLEは、継承する。
      {1, TABLE_EXISTING, PermissionLevel.READ, false, false},
      {1, TABLE_EXISTING, PermissionLevel.FULL, false, true},
      {1, COLUMN_NEW_IN_EXISTING_TABLE, PermissionLevel.READ, false, false},
      {1, COLUMN_NEW_IN_EXISTING_TABLE, PermissionLevel.FULL, false, true},
      // G2: SCHEMA=FULL。新規のテーブル・カラムは、スキーマの設定にフォールバックする。
      {2, COLUMN_EXISTING, PermissionLevel.FULL, false, false},
      {2, TABLE_NEW, PermissionLevel.FULL, false, false},
      {2, COLUMN_IN_NEW_TABLE, PermissionLevel.FULL, false, false},
      // G3: TABLE=FULLだけ。SCHEMAの設定はなく、新規のテーブルには及ばない。
      {3, SCHEMA_SCOPE, PermissionLevel.READ, false, true},
      {3, COLUMN_EXISTING, PermissionLevel.FULL, false, false},
      {3, TABLE_NEW, PermissionLevel.READ, false, true},
      // G4: TABLE=READ。
      {4, TABLE_EXISTING, PermissionLevel.READ, false, false},
      {4, TABLE_EXISTING, PermissionLevel.FULL, false, true},
      // G5: SCHEMA=FULLだが、TABLE=NONE(下位の拒否)。既存のテーブルは拒否が優先、新規のテーブルはスキーマのFULL。
      {5, TABLE_EXISTING, PermissionLevel.READ, false, true},
      {5, COLUMN_EXISTING, PermissionLevel.READ, false, true},
      {5, TABLE_NEW, PermissionLevel.FULL, false, false},
      // G6: COLUMN=NONEが、TABLE=FULLに優先する。新しいカラムは、TABLEのFULL。
      {6, COLUMN_EXISTING, PermissionLevel.READ, false, true},
      {6, COLUMN_NEW_IN_EXISTING_TABLE, PermissionLevel.FULL, false, false},
      // G7: COLUMN=FULLが、TABLE=READを引き上げる。TABLE自体はREAD。
      {7, COLUMN_EXISTING, PermissionLevel.FULL, false, false},
      {7, TABLE_EXISTING, PermissionLevel.FULL, false, true},
      // G8: SCHEMA=FULL、COLUMN=READ。
      {8, COLUMN_EXISTING, PermissionLevel.FULL, false, true},
      {8, TABLE_EXISTING, PermissionLevel.FULL, false, false},
      // ブートストラップ状態: 昇格の判定を行わない(初回のRBAC投入を許す)。
      {0, SCHEMA_SCOPE, PermissionLevel.FULL, true, false},
      {0, COLUMN_IN_NEW_TABLE, PermissionLevel.FULL, true, false},
    };
    List<ActorGrants> patterns = primaryGrantPatterns();
    return Stream.of(rows)
        .map(row -> Arguments.of(patterns.get((Integer) row[0]), row[1], row[2], row[3], row[4]));
  }

  @ParameterizedTest(name = "[{index}] 操作者={0} 対象={1} 割当={2} 初期状態={3} → 昇格={4}")
  @MethodSource("primaryGoldenCases")
  void primaryPermissionEscalationMatchesTheHandWrittenExpectations(
      ActorGrants grants,
      Scope scope,
      PermissionLevel level,
      boolean bootstrap,
      boolean expectEscalation) {
    List<ImportValidationError> errors =
        validator(grants).validate(primarySet(scope, level), ACTOR, bootstrap);

    assertThat(escalationFields(errors))
        .isEqualTo(expectEscalation ? List.of("primaryPermissions[0]") : List.of());
  }

  private static List<ActorGrants> auxiliaryGrantPatterns() {
    return List.of(
        auxGrants("A0:指定なし"),
        auxGrants("A1:SCHEMA(作成=可)", auxiliary(ScopeType.SCHEMA, SCHEMA, true, null)),
        auxGrants("A2:SCHEMA(作成=可,削除=可)", auxiliary(ScopeType.SCHEMA, SCHEMA, true, true)),
        auxGrants(
            "A3:SCHEMA(作成=可),TABLE(作成=不可)",
            auxiliary(ScopeType.SCHEMA, SCHEMA, true, null),
            auxiliary(ScopeType.TABLE, TABLE_ID, false, null)),
        auxGrants(
            "A4:SCHEMA(作成=可),TABLE(作成=指定なし,削除=可)",
            auxiliary(ScopeType.SCHEMA, SCHEMA, true, null),
            auxiliary(ScopeType.TABLE, TABLE_ID, null, true)));
  }

  static Stream<Arguments> auxiliaryCartesianCases() {
    List<Scope> scopes = List.of(SCHEMA_SCOPE, TABLE_EXISTING, TABLE_NEW);
    Boolean[] values = {true, false, null};
    List<Arguments> cases = new ArrayList<>();
    for (ActorGrants grants : auxiliaryGrantPatterns()) {
      for (Scope scope : scopes) {
        for (Boolean create : values) {
          for (Boolean delete : values) {
            for (boolean bootstrap : new boolean[] {false, true}) {
              List<String> expected = new ArrayList<>();
              if (!bootstrap) {
                if (Boolean.TRUE.equals(create) && !oracleAuxiliary(grants, scope, true)) {
                  expected.add("auxiliaryPermissions[0].createAllowed");
                }
                if (Boolean.TRUE.equals(delete) && !oracleAuxiliary(grants, scope, false)) {
                  expected.add("auxiliaryPermissions[0].deleteAllowed");
                }
              }
              cases.add(Arguments.of(grants, scope, create, delete, bootstrap, expected));
            }
          }
        }
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "[{index}] 操作者={0} 対象={1} 作成={2} 削除={3} 初期状態={4} → 昇格={5}")
  @MethodSource("auxiliaryCartesianCases")
  void auxiliaryPermissionEscalationResolvesCreateAndDeleteIndependently(
      ActorGrants grants,
      Scope scope,
      Boolean create,
      Boolean delete,
      boolean bootstrap,
      List<String> expectedFields) {
    List<ImportValidationError> errors =
        validator(grants).validate(auxiliarySet(scope, create, delete), ACTOR, bootstrap);

    assertThat(escalationFields(errors)).isEqualTo(expectedFields);
  }

  /** ゴールデン表(補助権限): 作成・削除は、それぞれ独立に、TABLE → SCHEMAの順に継承する。 */
  static Stream<Arguments> auxiliaryGoldenCases() {
    Object[][] rows = {
      // {操作者の割当の番号, 対象, 作成, 削除, 期待する昇格の項目}
      {0, SCHEMA_SCOPE, true, null, List.of("auxiliaryPermissions[0].createAllowed")},
      {0, SCHEMA_SCOPE, false, false, List.of()},
      {0, SCHEMA_SCOPE, null, null, List.of()},
      {1, TABLE_EXISTING, true, null, List.of()},
      {1, TABLE_EXISTING, true, true, List.of("auxiliaryPermissions[0].deleteAllowed")},
      {2, TABLE_NEW, true, true, List.of()},
      // A3: TABLEで作成=不可が、SCHEMAの作成=可に優先する。新規のテーブルは、SCHEMAの作成=可。
      {3, TABLE_EXISTING, true, null, List.of("auxiliaryPermissions[0].createAllowed")},
      {3, TABLE_NEW, true, null, List.of()},
      // A4: TABLEの作成=指定なしは、SCHEMAの作成=可を継承。削除は、TABLEで可。
      {4, TABLE_EXISTING, true, true, List.of()},
      {4, SCHEMA_SCOPE, true, true, List.of("auxiliaryPermissions[0].deleteAllowed")},
    };
    List<ActorGrants> patterns = auxiliaryGrantPatterns();
    return Stream.of(rows)
        .map(row -> Arguments.of(patterns.get((Integer) row[0]), row[1], row[2], row[3], row[4]));
  }

  @ParameterizedTest(name = "[{index}] 操作者={0} 対象={1} 作成={2} 削除={3} → {4}")
  @MethodSource("auxiliaryGoldenCases")
  void auxiliaryPermissionEscalationMatchesTheHandWrittenExpectations(
      ActorGrants grants, Scope scope, Boolean create, Boolean delete, List<String> expected) {
    List<ImportValidationError> errors =
        validator(grants).validate(auxiliarySet(scope, create, delete), ACTOR, false);

    assertThat(escalationFields(errors)).isEqualTo(expected);
  }

  // ---- 主権限が0件の拒否(BR9.12) ----

  @Test
  void emptyPrimaryPermissionsAreRejectedEvenInTheBootstrapState() {
    RbacImportSet set = new RbacImportSet(List.of(new Role("r")), List.of(), List.of(), List.of());

    for (boolean bootstrap : new boolean[] {false, true}) {
      List<ImportValidationError> errors =
          validator(primaryGrants("G0:指定なし")).validate(set, ACTOR, bootstrap);

      assertThat(errors)
          .extracting(ImportValidationError::message, ImportValidationError::field)
          .contains(
              org.assertj.core.groups.Tuple.tuple(
                  ImportMessageKeys.RBAC_EMPTY, "primaryPermissions"));
    }
  }

  @Test
  void aSingleNonEmptyPrimaryPermissionIsEnoughToAvoidTheEmptyRejection() {
    List<ImportValidationError> errors =
        validator(primaryGrants("G0:指定なし"))
            .validate(primarySet(SCHEMA_SCOPE, PermissionLevel.NONE), ACTOR, false);

    assertThat(errors).isEmpty();
  }

  // ---- 全件を集める・fail closed ----

  @Test
  void collectsEveryEscalatingEntryInsteadOfStoppingAtTheFirst() {
    RbacImportSet set =
        new RbacImportSet(
            List.of(new Role("r")),
            List.of(),
            List.of(
                new Primary("r", SCHEMA_SCOPE, PermissionLevel.FULL),
                new Primary("r", TABLE_EXISTING, PermissionLevel.NONE),
                new Primary("r", TABLE_NEW, PermissionLevel.READ)),
            List.of(new Auxiliary("r", SCHEMA_SCOPE, true, true)));

    List<ImportValidationError> errors =
        validator(primaryGrants("G0:指定なし")).validate(set, ACTOR, false);

    assertThat(escalationFields(errors))
        .containsExactly(
            "primaryPermissions[0]",
            "primaryPermissions[2]",
            "auxiliaryPermissions[0].createAllowed",
            "auxiliaryPermissions[0].deleteAllowed");
  }

  @Test
  void anActorWithoutAnActiveRoleFailsClosedForEveryGrantAboveNone() {
    PrimaryPermissionRepository primaryRepository = mock(PrimaryPermissionRepository.class);
    AuxiliaryPermissionRepository auxiliaryRepository = mock(AuxiliaryPermissionRepository.class);
    RbacImportValidator validator = new RbacImportValidator(primaryRepository, auxiliaryRepository);
    RbacImportSet set =
        new RbacImportSet(
            List.of(new Role("r")),
            List.of(),
            List.of(
                new Primary("r", SCHEMA_SCOPE, PermissionLevel.READ),
                new Primary("r", TABLE_EXISTING, PermissionLevel.NONE)),
            List.of());

    for (String actor : new String[] {null, "", "  "}) {
      assertThat(escalationFields(validator.validate(set, actor, false)))
          .containsExactly("primaryPermissions[0]");
    }
    verify(primaryRepository, never()).findByRoleId(org.mockito.ArgumentMatchers.any());
  }
}
