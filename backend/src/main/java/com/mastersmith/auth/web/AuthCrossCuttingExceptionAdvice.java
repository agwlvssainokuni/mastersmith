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

package com.mastersmith.auth.web;

import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.exception.SessionNotFoundException;
import com.mastersmith.auth.security.AuthProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 他ユニットのコントローラの中で投げられる、authentication-serviceの例外を、401・503のProblemDetailsに変換する(security-design.md
 * NFR2.8)。
 *
 * <p>対象のコントローラは限定せず、対象の<b>例外の型</b>を、U5自身の3つ({@link SessionNotFoundException}・{@link
 * SessionExpiredException}・{@link
 * AuthStorageUnavailableException})に限定する。list-engine(U10)・record-edit-engine(U11)が、C14({@code
 * getActiveRoleId})の例外を握りつぶさず、そのまま伝播させると、ここで、401({@code auth.token.invalid}、{@code
 * WWW-Authenticate: Bearer})・503({@code
 * auth.service.unavailable})になる。U5自身の例外の型だけを扱うため、他ユニットの例外の型に依存せず、他ユニットの例外の変換とも衝突しない。
 *
 * <p>依存を持たない(スライスのテストを含め、どのコンテキストでも、追加の部品なしに読み込める)。{@code @Order}は、共通基盤の基底のハンドラより優先するよう、明記する。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthCrossCuttingExceptionAdvice {

  @ExceptionHandler({SessionNotFoundException.class, SessionExpiredException.class})
  public ResponseEntity<ProblemDetail> handleSessionInvalid(HttpServletRequest request) {
    return AuthApiExceptionAdvice.problem(AuthProblem.TOKEN_INVALID, request);
  }

  @ExceptionHandler(AuthStorageUnavailableException.class)
  public ResponseEntity<ProblemDetail> handleStorageUnavailable(HttpServletRequest request) {
    return AuthApiExceptionAdvice.problem(AuthProblem.SERVICE_UNAVAILABLE, request);
  }
}
