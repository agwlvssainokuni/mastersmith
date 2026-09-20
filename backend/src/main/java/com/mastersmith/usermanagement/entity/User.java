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

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * 招待・登録・更新・無効化の対象となるユーザーアカウント(entities.md User)。
 *
 * <p>{@code passwordHash}は招待中(invited)はnullで、招待受諾時または初期管理者の作成時に設定される(Argon2id)。{@code
 * invitationToken}はstatus=invitedの間のみ非nullとする(受諾・取消でnull化する)。この2つは認証情報であり、応答型 ({@code
 * UserResponse})・イベントのスナップショット({@code UserSnapshot})には含めない(NFR2.2)ため、{@link #toString()}にも出力しない。
 *
 * <p>{@code roleIds}は直接付与されたロールIDで、permission-engineのRoleへの「不透明な文字列参照」である(物理FK制約は設けず、実在検証は
 * アプリケーション層、BR4.5)。 Group経由の間接付与分は保持しない。
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false, length = 36)
  private String userId;

  @Column(name = "name", nullable = false)
  private String name;

  /** trim・小文字へ正規化した値(BR4.15)。一意。 */
  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private UserStatus status;

  @Column(name = "invitation_token", length = 36)
  private String invitationToken;

  // FetchMode.SELECT: ロール付きの行ロック読み取り(SELECT ... FOR UPDATE)で結合(JOIN)を使わず、
  // ロール一覧は別のSELECTで読む(FOR UPDATEを単一テーブルの行に限るため)。
  @ElementCollection(fetch = FetchType.EAGER)
  @Fetch(FetchMode.SELECT)
  @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_id", nullable = false)
  private Set<String> roleIds = new LinkedHashSet<>();

  protected User() {
    // JPA用
  }

  private User(
      String userId,
      String name,
      String email,
      String passwordHash,
      UserStatus status,
      String invitationToken,
      Collection<String> roleIds) {
    this.userId = userId;
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.status = status;
    this.invitationToken = invitationToken;
    this.roleIds = new LinkedHashSet<>(roleIds);
  }

  /** 招待(W1)で作成するUser。status=invited、招待トークンを持ち、passwordHashは未設定。 */
  public static User invited(
      String name, String email, Collection<String> roleIds, String invitationToken) {
    return new User(
        UUID.randomUUID().toString(),
        name,
        email,
        null,
        UserStatus.INVITED,
        Objects.requireNonNull(invitationToken),
        roleIds);
  }

  /** 初期管理者の自動作成(W5)で作成するUser。status=active、招待トークンなし。 */
  public static User activeAdmin(
      String name, String email, String passwordHash, Collection<String> roleIds) {
    return new User(
        UUID.randomUUID().toString(),
        name,
        email,
        Objects.requireNonNull(passwordHash),
        UserStatus.ACTIVE,
        null,
        roleIds);
  }

  public String getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public String getInvitationToken() {
    return invitationToken;
  }

  /** 直接付与されたロールIDを、ソート済みの不変リストで返す(スナップショット・比較の再現性のため)。 */
  public List<String> getRoleIds() {
    List<String> sorted = new ArrayList<>(roleIds);
    sorted.sort(String::compareTo);
    return List.copyOf(sorted);
  }

  /** 直接付与されたロールIDを、指定の集合で置き換える。 */
  public void replaceRoleIds(Collection<String> newRoleIds) {
    roleIds.clear();
    roleIds.addAll(newRoleIds);
  }

  /** 無効化(W4、BR4.6)・招待の取消(BR4.11): status=disabledへ遷移させ、招待トークンをnullにする。 */
  public void disable() {
    this.status = UserStatus.DISABLED;
    this.invitationToken = null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof User other)) {
      return false;
    }
    return Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(userId);
  }

  /** 機微値(passwordHash・invitationToken)・個人情報(name・email)を含めない(NFR2.2・NFR2.6)。 */
  @Override
  public String toString() {
    return "User{userId='%s', status=%s}".formatted(userId, status);
  }
}
