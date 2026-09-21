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

package com.mastersmith.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.mastersmith.common.security.Operator;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.dto.UserPreferenceDto;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link UserPreferenceService}のテスト(W6、rules.md BR4.8):
 * GETの既定値(作成しない)・PUTの作成と更新・他人の設定を操作できないこと(対象userIdは操作者由来のみ)・
 * 許容値外の422・未認証の401。activeRoleIdとcanAccessScreenには依存しない。
 */
@DataJpaTest
class UserPreferenceServiceTest {

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TestEntityManager entityManager;

  private final PermissionEngineApi permissionEngineApi = mock(PermissionEngineApi.class);
  private UserPreferenceService service;

  @BeforeEach
  void setUp() {
    service =
        new UserPreferenceService(
            preferenceRepository,
            userRepository,
            transactionManager,
            new UserAuthorizer(permissionEngineApi));
  }

  private User user() {
    return userRepository.saveAndFlush(UserTestFactory.activeUser());
  }

  @Test
  void getReturnsDefaultsWithoutCreatingARecordWhenNoneExists() {
    User user = user();
    long before = preferenceRepository.count();

    UserPreferenceDto dto = service.get(new Operator(user.getUserId(), "session-1", null));

    assertThat(dto).isEqualTo(new UserPreferenceDto("light", "medium", "ja"));
    assertThat(preferenceRepository.count()).isEqualTo(before);
  }

  @Test
  void putCreatesThenUpdatesTheOperatorsOwnPreference() {
    User user = user();
    Operator operator = new Operator(user.getUserId(), "session-1", null);

    UserPreferenceDto created =
        service.update(operator, new UserPreferenceDto("dark", "large", "en"));
    UserPreferenceDto updated =
        service.update(operator, new UserPreferenceDto("light", "small", "ja"));

    assertThat(created).isEqualTo(new UserPreferenceDto("dark", "large", "en"));
    assertThat(updated).isEqualTo(new UserPreferenceDto("light", "small", "ja"));
    entityManager.clear();
    assertThat(service.get(operator)).isEqualTo(new UserPreferenceDto("light", "small", "ja"));
    assertThat(preferenceRepository.findAll())
        .filteredOn(p -> p.getUserId().equals(user.getUserId()))
        .hasSize(1);
  }

  @Test
  void anOperatorCanOnlyTouchTheirOwnPreferenceNeverAnotherUsers() {
    User alice = user();
    User bob = user();
    service.update(
        new Operator(bob.getUserId(), "session-1", null),
        new UserPreferenceDto("dark", "large", "en"));

    service.update(
        new Operator(alice.getUserId(), "session-1", "any-role"),
        new UserPreferenceDto("light", "small", "ja"));

    entityManager.clear();
    assertThat(service.get(new Operator(bob.getUserId(), "session-1", null)))
        .isEqualTo(new UserPreferenceDto("dark", "large", "en"));
    assertThat(service.get(new Operator(alice.getUserId(), "session-1", null)))
        .isEqualTo(new UserPreferenceDto("light", "small", "ja"));
  }

  @Test
  void noPermissionCheckIsMadeAndNoActiveRoleIsNeeded() {
    User user = user();

    service.get(new Operator(user.getUserId(), "session-1", null));
    service.update(
        new Operator(user.getUserId(), "session-1", null),
        new UserPreferenceDto("dark", "large", "en"));

    org.mockito.Mockito.verifyNoInteractions(permissionEngineApi);
  }

  static Stream<Arguments> unauthenticated() {
    return Stream.of(Arguments.of("operatorがnull(操作者を解決できない)", null));
  }

  @ParameterizedTest(name = "{0}は401")
  @MethodSource("unauthenticated")
  void unresolvedOperatorsAreRejectedWith401(String label, Operator operator) {
    assertThatThrownBy(() -> service.get(operator)).isInstanceOf(OperatorUnresolvedException.class);
    assertThatThrownBy(() -> service.update(operator, new UserPreferenceDto("dark", "large", "en")))
        .isInstanceOf(OperatorUnresolvedException.class);
  }

  @Test
  void anOperatorWhoseUserDoesNotExistCannotWriteAPreference() {
    assertThatThrownBy(
            () ->
                service.update(
                    new Operator("ghost-user", "session-1", null),
                    new UserPreferenceDto("dark", "large", "en")))
        .isInstanceOf(OperatorUnresolvedException.class);

    assertThat(preferenceRepository.findById("ghost-user")).isEmpty();
  }

  static Stream<Arguments> invalidPreferences() {
    return Stream.of(
        Arguments.of(
            new UserPreferenceDto("blue", "large", "en"), List.of("user.validation.theme.invalid")),
        Arguments.of(
            new UserPreferenceDto("dark", "huge", "en"),
            List.of("user.validation.fontSize.invalid")),
        Arguments.of(
            new UserPreferenceDto("dark", "large", "fr"),
            List.of("user.validation.locale.invalid")),
        Arguments.of(
            new UserPreferenceDto(null, null, null),
            List.of(
                "user.validation.theme.required",
                "user.validation.fontSize.required",
                "user.validation.locale.required")),
        Arguments.of(
            new UserPreferenceDto("DARK", "large", "en"),
            List.of("user.validation.theme.invalid")));
  }

  @ParameterizedTest
  @MethodSource("invalidPreferences")
  void valuesOutsideTheAllowedSetAreRejectedWith422AndNothingIsSaved(
      UserPreferenceDto dto, List<String> expectedKeys) {
    User user = user();
    long before = preferenceRepository.count();

    assertThatThrownBy(() -> service.update(new Operator(user.getUserId(), "session-1", null), dto))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e ->
                assertThat(e.getErrors())
                    .extracting(UserFieldError::message)
                    .containsExactlyElementsOf(expectedKeys));

    assertThat(preferenceRepository.count()).isEqualTo(before);
  }
}
