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

package com.mastersmith.usermanagement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/** {@link UserPreference}のJPAマッピングとマイグレーション(V4)の整合の確認。 */
@DataJpaTest
class UserPreferenceJpaTest {

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void persistsAndReloadsAllValues() {
    User user = userRepository.saveAndFlush(UserTestFactory.activeUser());
    repository.saveAndFlush(
        new UserPreference(user.getUserId(), Theme.DARK, FontSize.LARGE, UiLocale.EN));
    entityManager.clear();

    UserPreference found = repository.findById(user.getUserId()).orElseThrow();
    assertThat(found.getTheme()).isEqualTo(Theme.DARK);
    assertThat(found.getFontSize()).isEqualTo(FontSize.LARGE);
    assertThat(found.getLocale()).isEqualTo(UiLocale.EN);
  }

  @Test
  void withDefaultsUsesLightMediumJa() {
    UserPreference defaults = UserPreference.withDefaults("user-1");

    assertThat(defaults.getTheme()).isEqualTo(Theme.LIGHT);
    assertThat(defaults.getFontSize()).isEqualTo(FontSize.MEDIUM);
    assertThat(defaults.getLocale()).isEqualTo(UiLocale.JA);
  }

  @Test
  void updatesValues() {
    User user = userRepository.saveAndFlush(UserTestFactory.activeUser());
    UserPreference preference =
        repository.saveAndFlush(UserPreference.withDefaults(user.getUserId()));

    preference.update(Theme.DARK, FontSize.SMALL, UiLocale.EN);
    repository.saveAndFlush(preference);
    entityManager.clear();

    UserPreference found = repository.findById(user.getUserId()).orElseThrow();
    assertThat(found.getTheme()).isEqualTo(Theme.DARK);
    assertThat(found.getFontSize()).isEqualTo(FontSize.SMALL);
    assertThat(found.getLocale()).isEqualTo(UiLocale.EN);
  }

  @Test
  void rejectsAPreferenceForAnUnknownUser() {
    // userIdはUserと対応する(FK制約)。
    assertThatThrownBy(
            () -> repository.saveAndFlush(UserPreference.withDefaults("no-such-user-id")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void enumValuesRoundTripThroughTheApiRepresentation() {
    assertThat(Theme.fromValue("dark")).contains(Theme.DARK);
    assertThat(FontSize.fromValue("large")).contains(FontSize.LARGE);
    assertThat(UiLocale.fromValue("en")).contains(UiLocale.EN);
    assertThat(UserStatus.fromValue("invited")).contains(UserStatus.INVITED);
    // 許容値外・大文字小文字違いは受け付けない。
    assertThat(Theme.fromValue("DARK")).isEmpty();
    assertThat(FontSize.fromValue("huge")).isEmpty();
    assertThat(UiLocale.fromValue("fr")).isEmpty();
    assertThat(UserStatus.fromValue(null)).isEmpty();
  }
}
