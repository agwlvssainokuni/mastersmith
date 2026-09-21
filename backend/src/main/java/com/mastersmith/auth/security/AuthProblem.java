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

package com.mastersmith.auth.security;

import org.springframework.http.HttpStatus;

/**
 * 認証のエラー応答の種類(security-design.md NFR2.8)。ステータス・{@code title}・{@code
 * detail}(状況ごとの固定の文言。原因の詳細・入力値を含めない)・ {@code
 * code}(安定したi18nキー。frontend-ui(U12)が、キーから、表示する文言を選ぶ、NFR7.1)・{@code WWW-Authenticate:
 * Bearer}を付けるかを、1か所に持つ。 フィルタでの応答({@link ProblemDetailsWriter})と、コントローラでの応答({@code
 * AuthApiExceptionAdvice}・{@code AuthCrossCuttingExceptionAdvice})が、同じ内容を返す。
 */
public enum AuthProblem {
  /** ログイン失敗(全原因)。原因を区別しない(BR5.2)。 */
  LOGIN_FAILED(
      HttpStatus.UNAUTHORIZED,
      "Unauthorized",
      "ログインできませんでした。メールアドレスとパスワードを確認してください。",
      "auth.login.failed",
      false),
  /** 認証フィルタの失敗(全原因)・他ユニットの処理の中でのC14の例外。 */
  TOKEN_INVALID(
      HttpStatus.UNAUTHORIZED, "Unauthorized", "認証情報を確認できませんでした。", "auth.token.invalid", true),
  /** リフレッシュの失敗(全原因)。猶予内の再送・盗用の疑いを区別しない(BR5.6)。 */
  REFRESH_REJECTED(
      HttpStatus.UNAUTHORIZED, "Unauthorized", "セッションを更新できませんでした。", "auth.refresh.rejected", false),
  /** 保持していないロールの選択(BR5.9)。 */
  ROLE_NOT_HELD(HttpStatus.FORBIDDEN, "Forbidden", "選択できないロールです。", "auth.role.not-held", false),
  /** 認証の要否の規則による拒否(deny by default)。 */
  FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden", "この操作を実行する権限がありません。", "auth.forbidden", false),
  /** ハッシュ計算の待機超過(BR5.15)と、内部設定DBの障害(NFR4.2)。原因を区別しない。 */
  SERVICE_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Service Unavailable",
      "ただいま処理できません。しばらくしてから、もう一度お試しください。",
      "auth.service.unavailable",
      false),
  /** リクエストボディが上限(64KiB)を超えた(NFR2.10)。 */
  REQUEST_TOO_LARGE(
      HttpStatus.CONTENT_TOO_LARGE,
      "Payload Too Large",
      "リクエストボディが大きすぎます(上限64KiB)。",
      "auth.request.too-large",
      false),
  /** JSONの形式が不正。 */
  REQUEST_MALFORMED(
      HttpStatus.BAD_REQUEST, "Bad Request", "リクエストボディを読み取れません。", "auth.request.malformed", false),
  /** 分類できない例外(詳細なし)。 */
  INTERNAL_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Internal Server Error",
      "処理中にエラーが発生しました。",
      "auth.internal-error",
      false);

  private final HttpStatus status;
  private final String title;
  private final String detail;
  private final String code;
  private final boolean bearerChallenge;

  AuthProblem(
      HttpStatus status, String title, String detail, String code, boolean bearerChallenge) {
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.code = code;
    this.bearerChallenge = bearerChallenge;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }

  /** i18nキー(ProblemDetailsの拡張メンバー{@code code})。 */
  public String code() {
    return code;
  }

  /** {@code WWW-Authenticate: Bearer}を付けるか(理由を示す{@code error}などは付けない)。 */
  public boolean bearerChallenge() {
    return bearerChallenge;
  }
}
