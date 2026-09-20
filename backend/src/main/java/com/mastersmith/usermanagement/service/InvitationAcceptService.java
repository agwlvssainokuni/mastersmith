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

import com.mastersmith.usermanagement.dto.AcceptInvitationRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.entity.FontSize;
import com.mastersmith.usermanagement.entity.Theme;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserPreference;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserChangedEventPublisher;
import com.mastersmith.usermanagement.event.UserSnapshot;
import com.mastersmith.usermanagement.exception.InvitationTokenNotFoundException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.observation.UserObservations;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.PasswordHasher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 招待受諾・初回パスワード設定(W2、rules.md BR4.2・BR4.3・BR4.9)。認証不要のAPIで、招待トークンの保持が根拠となる(NFR2.4)。
 *
 * <p>順序(reliability-design.md NFR4.1):
 * ①トークンでUserを検索(トランザクションの外の軽い読み取り。見つからない・招待中でない場合は、ハッシュを計算せずに404) → ②入力の検証(422) →
 * ③ハッシュ計算(許可の中、トランザクションの外) → ④{@link TransactionTemplate}で、status=invitedを条件とした条件付き更新
 * (0件なら404)とUserPreferenceの作成 → ⑤コミット後にUserChangedEvent(ACTIVATED)を発行。未知のトークンで、ハッシュ計算の資源を消費させない。
 * 未知・使用済み・取消済みのトークンは区別せず、同一の404にする。
 */
@Service
public class InvitationAcceptService {

  private record Activation(UserChangedEvent event, UserResponse response) {}

  private final UserRepository userRepository;
  private final UserPreferenceRepository preferenceRepository;
  private final TransactionTemplate transaction;
  private final PasswordHasher passwordHasher;
  private final UserChangedEventPublisher eventPublisher;
  private final Counter notFoundCounter;

  /** 観測(スパン)。既定は何もしない。アプリケーションでは、{@link UserObservations}のBeanが注入される(NFR5.3)。 */
  private UserObservations observations = UserObservations.NOOP;

  @Autowired
  public void setObservations(UserObservations observations) {
    this.observations = observations;
  }

  public InvitationAcceptService(
      UserRepository userRepository,
      UserPreferenceRepository preferenceRepository,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      PasswordHasher passwordHasher,
      UserChangedEventPublisher eventPublisher,
      MeterRegistry meterRegistry) {
    this.userRepository = userRepository;
    this.preferenceRepository = preferenceRepository;
    this.transaction = new TransactionTemplate(transactionManager);
    this.passwordHasher = passwordHasher;
    this.eventPublisher = eventPublisher;
    this.notFoundCounter =
        Counter.builder("user.invitation.accept.not_found")
            .description("招待受諾で404を返した回数(BR4.2)")
            .register(meterRegistry);
  }

  /**
   * 招待を受諾する。
   *
   * @throws InvitationTokenNotFoundException 未知・使用済み・取消済みのトークン(区別しない)
   * @throws UserValidationException パスワード・氏名・表示設定の不備(フィールド単位。値は含めない)
   * @throws com.mastersmith.usermanagement.HashCapacityExceededException ハッシュ計算の許可を待機の上限内に取れなかった場合
   */
  public UserResponse accept(String token, AcceptInvitationRequest request) {
    return observations.observe("user.api.accept", () -> doAccept(token, request));
  }

  private UserResponse doAccept(String token, AcceptInvitationRequest request) {
    User invited = findInvitedUser(token);
    UserSnapshot before = UserSnapshot.from(invited);

    List<UserFieldError> errors = new ArrayList<>();
    UserInputValidator.validatePassword(request.password(), errors);
    String name = UserInputValidator.normalizeName(request.name(), errors);
    Theme theme = UserInputValidator.parseTheme(request.theme(), false, errors);
    FontSize fontSize = UserInputValidator.parseFontSize(request.fontSize(), false, errors);
    UiLocale locale = UserInputValidator.parseLocale(request.locale(), false, errors);
    UserValidationException.throwIfAny(errors);

    // ハッシュ計算は、許可の中で、トランザクションの外側で行う(資源の取得順序の不変条件)。
    String passwordHash = passwordHasher.hash(request.password());

    Activation activation =
        transaction.execute(
            status ->
                activate(
                    token,
                    invited.getUserId(),
                    passwordHash,
                    name,
                    before,
                    theme,
                    fontSize,
                    locale));
    eventPublisher.publish(activation.event());
    return activation.response();
  }

  private User findInvitedUser(String token) {
    Optional<User> found =
        token == null || token.isBlank()
            ? Optional.empty()
            : userRepository.findByInvitationToken(token);
    if (found.isEmpty() || found.get().getStatus() != UserStatus.INVITED) {
      throw notFound();
    }
    return found.get();
  }

  private Activation activate(
      String token,
      String userId,
      String passwordHash,
      String name,
      UserSnapshot before,
      Theme theme,
      FontSize fontSize,
      UiLocale locale) {
    // 並行受諾では、更新件数が1件になるのは一方だけで、他方は0件となり404になる(ロールバックされる)。
    if (userRepository.activateInvitation(token, passwordHash, name) == 0) {
      throw notFound();
    }
    preferenceRepository.save(new UserPreference(userId, theme, fontSize, locale));
    User activated = userRepository.findById(userId).orElseThrow();
    UserChangedEvent event =
        UserChangedEvent.of(
            UserChangeOperation.ACTIVATED,
            userId,
            before,
            UserSnapshot.from(activated),
            // 受諾したUser自身のuserId(BR4.9)。
            userId);
    return new Activation(event, UserResponse.from(activated));
  }

  private InvitationTokenNotFoundException notFound() {
    notFoundCounter.increment();
    return new InvitationTokenNotFoundException();
  }
}
