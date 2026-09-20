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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * ユーザー単位の表示設定(テーマ・フォントサイズ・表示言語、entities.md UserPreference。FR9.1・FR10.1)。
 *
 * <p>招待受諾時または初期管理者の作成時に作成する(BR4.3)。Userが無効化されても保持する。Userとは{@code userId}で対応する(1対0..1)。
 */
@Entity
@Table(name = "user_preference")
public class UserPreference {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false, length = 36)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "theme", nullable = false)
  private Theme theme;

  @Enumerated(EnumType.STRING)
  @Column(name = "font_size", nullable = false)
  private FontSize fontSize;

  @Enumerated(EnumType.STRING)
  @Column(name = "locale", nullable = false)
  private UiLocale locale;

  protected UserPreference() {
    // JPA用
  }

  public UserPreference(String userId, Theme theme, FontSize fontSize, UiLocale locale) {
    this.userId = userId;
    this.theme = theme;
    this.fontSize = fontSize;
    this.locale = locale;
  }

  /** 既定値(light/medium/ja、BR4.3)で作成する。 */
  public static UserPreference withDefaults(String userId) {
    return new UserPreference(userId, Theme.DEFAULT, FontSize.DEFAULT, UiLocale.DEFAULT);
  }

  public String getUserId() {
    return userId;
  }

  public Theme getTheme() {
    return theme;
  }

  public FontSize getFontSize() {
    return fontSize;
  }

  public UiLocale getLocale() {
    return locale;
  }

  public void update(Theme theme, FontSize fontSize, UiLocale locale) {
    this.theme = theme;
    this.fontSize = fontSize;
    this.locale = locale;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserPreference other)) {
      return false;
    }
    return Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(userId);
  }

  @Override
  public String toString() {
    return "UserPreference{userId='%s', theme=%s, fontSize=%s, locale=%s}"
        .formatted(userId, theme, fontSize, locale);
  }
}
