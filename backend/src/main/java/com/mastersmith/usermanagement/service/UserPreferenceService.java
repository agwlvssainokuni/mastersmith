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

import com.mastersmith.usermanagement.dto.UserPreferenceDto;
import com.mastersmith.usermanagement.entity.FontSize;
import com.mastersmith.usermanagement.entity.Theme;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.entity.UserPreference;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.observation.UserObservations;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.Operator;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ユーザー単位の表示設定の取得・更新(W6、rules.md BR4.8。FR9.1・FR10.1)。認証済み(操作者のuserIdを解決できる)であれば、誰でも自分自身の設定を
 * 操作できる(canAccessScreenとactiveRoleIdは不要)。対象のuserIdは、常に操作者のuserIdで、リクエストからは受け取らない(他人の設定は操作できない)。
 */
@Service
public class UserPreferenceService {

  private final UserPreferenceRepository preferenceRepository;
  private final UserRepository userRepository;
  private final TransactionTemplate transaction;
  private final UserAuthorizer authorizer;

  /** 観測(スパン)。既定は何もしない。アプリケーションでは、{@link UserObservations}のBeanが注入される(NFR5.3)。 */
  private UserObservations observations = UserObservations.NOOP;

  @Autowired
  public void setObservations(UserObservations observations) {
    this.observations = observations;
  }

  public UserPreferenceService(
      UserPreferenceRepository preferenceRepository,
      UserRepository userRepository,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      UserAuthorizer authorizer) {
    this.preferenceRepository = preferenceRepository;
    this.userRepository = userRepository;
    this.transaction = new TransactionTemplate(transactionManager);
    this.authorizer = authorizer;
  }

  /** 自分自身の設定を返す。存在しなければ、既定値(light/medium/ja)を返す(作成はしない)。 */
  public UserPreferenceDto get(Operator operator) {
    return observations.observe("user.preferences.get", () -> doGet(operator));
  }

  private UserPreferenceDto doGet(Operator operator) {
    String userId = authorizer.requireOperatorUserId(operator);
    return preferenceRepository
        .findById(userId)
        .map(UserPreferenceDto::from)
        .orElseGet(UserPreferenceDto::defaults);
  }

  /**
   * 自分自身の設定を更新する(存在しなければ作成する)。テーマ・フォントサイズ・表示言語は、すべて必須で、許容値内であること。
   *
   * @throws UserValidationException 許容値外・未指定(フィールド単位)
   * @throws OperatorUnresolvedException 操作者のuserIdに対応するUserが存在しない場合(401)
   */
  public UserPreferenceDto update(Operator operator, UserPreferenceDto request) {
    return observations.observe("user.preferences.update", () -> doUpdate(operator, request));
  }

  private UserPreferenceDto doUpdate(Operator operator, UserPreferenceDto request) {
    String userId = authorizer.requireOperatorUserId(operator);
    List<UserFieldError> errors = new ArrayList<>();
    Theme theme = UserInputValidator.parseTheme(request.theme(), true, errors);
    FontSize fontSize = UserInputValidator.parseFontSize(request.fontSize(), true, errors);
    UiLocale locale = UserInputValidator.parseLocale(request.locale(), true, errors);
    UserValidationException.throwIfAny(errors);

    UserPreference saved =
        transaction.execute(
            status -> {
              if (!userRepository.existsById(userId)) {
                throw new OperatorUnresolvedException();
              }
              UserPreference preference =
                  preferenceRepository
                      .findById(userId)
                      .orElseGet(() -> UserPreference.withDefaults(userId));
              preference.update(theme, fontSize, locale);
              return preferenceRepository.saveAndFlush(preference);
            });
    return UserPreferenceDto.from(saved);
  }
}
