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

package com.mastersmith.usermanagement;

import java.util.Optional;

/**
 * user-management(U4)がauthentication-service(U5)へ提供する内部Javaインタフェース契約(inception/contract-design/contract-summary.md
 * C11とその追補)。C5(frontend-ui向けREST)とは別の境界。
 *
 * <p>呼び出し元(U5)は、各メソッドを、<b>トランザクションの外で</b>呼ぶこと(ハッシュ計算の許可を保持している間はDB接続を取らない、という資源の取得順序の 不変条件を保つため)。
 *
 * <p><b>{@code revokeRefreshTokensOnDisable}は、契約から削除した</b>(authentication-service(U5)の機能設計
 * 追補4番。引き込み型のため不要)。FR2.3の 「以後の再認証はできない」は、{@link
 * #isDisabled(String)}が常に最新のstatusを返すことと、authentication-serviceが、ログイン・リフレッシュで
 * 無効化されたユーザーを拒否することで担保する。{@link #findByUserId(String)}・{@link #dummyVerify(String)}は、U5の追補として加えた。
 */
public interface UserAccountLookupApi {

  /**
   * 正規化後(trim・小文字)のemailでUserを検索する。{@link UserAccount#roleIds()}は、直接付与分とGroup経由分の和集合、{@link
   * UserAccount#passwordHash()}はnull。
   */
  Optional<UserAccount> findByEmail(String email);

  /**
   * パスワードを検証する。status=activeで{@code
   * passwordHash}が非nullの場合のみ検証し、それ以外(invited・disabled・不存在)、および129文字以上の
   * パスワードは、ハッシュ計算をせずfalse。平文をログ・エラーメッセージに出力しない。
   *
   * <p><b>副作用</b>: 検証に成功し、保存済みのハッシュが現在の設定より古い場合は、新しいパラメータのハッシュへ更新する(独立したトランザクション、
   * 失敗してもログインの成否には影響しない)。この更新は、UserChangedEventの対象外。
   *
   * @throws HashCapacityExceededException ハッシュ計算の許可を、待機の上限内に取れなかった場合(HTTPへの変換は呼び出し元)
   */
  boolean verifyPasswordHash(String userId, String rawPassword);

  /** 最新のstatusがdisabled、または不存在の場合にtrue(キャッシュしない、fail closed。rules.md BR4.13)。 */
  boolean isDisabled(String userId);

  /**
   * userIdでUserを検索する(C11への追補、authentication-service(U5)の機能設計 追補4番)。アクセストークンが{@code
   * sub}(userId)しか運ばないため、 リフレッシュ・ロール選択で、最新の選択可能なロールを得るために用いる。{@link
   * UserAccount#roleIds()}は、直接付与分とGroup経由分の和集合、{@link
   * UserAccount#passwordHash()}はnull。statusは問わない(不存在の場合だけ空)。
   */
  Optional<UserAccount> findByUserId(String userId);

  /**
   * ユーザーを指定しないダミーの検証(C11への追補、BR5.2・BR5.15)。実際の検証({@link #verifyPasswordHash})と同じコストのハッシュ計算を、同じ
   * ハッシュ計算の同時実行数の上限を共有して行い、結果は返さない。129文字以上のパスワードは、ハッシュ計算をしない({@link #verifyPasswordHash}と同じ
   * 扱い)。呼び出し元(authentication-service)が、実際の検証を行わない場合(未登録・招待中・無効化済み・ロック中)に、応答時間と503の有無を、実際の
   * 検証と揃えるために用いる。平文をログ・エラーメッセージに出力しない。
   *
   * @throws HashCapacityExceededException ハッシュ計算の許可を、待機の上限内に取れなかった場合(HTTPへの変換は呼び出し元)
   */
  void dummyVerify(String rawPassword);
}
