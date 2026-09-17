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

package com.mastersmith.menu.web;

import com.mastersmith.menu.dto.MenuItemInput;
import com.mastersmith.menu.dto.MenuItemView;
import com.mastersmith.menu.dto.MenuResponse;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.exception.MenuItemConflictException;
import com.mastersmith.menu.exception.MenuItemForbiddenException;
import com.mastersmith.menu.exception.MenuItemNotFoundException;
import com.mastersmith.menu.exception.MenuItemValidationException;
import com.mastersmith.menu.exception.MenuUnauthorizedException;
import com.mastersmith.menu.service.MenuItemCommandService;
import com.mastersmith.menu.service.MenuQueryService;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.schema.security.ActiveRoleResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * C3(menu-navigation REST API、FR7)を実装する。{@code GET /api/menu}(W1、BR6.1〜BR6.7・BR6.9)と、Contract
 * Design追補の{@code POST/PUT/DELETE /api/menu-items}(W2〜W4、BR6.8)を1つのコントローラにまとめる
 * (code-generation-plan.md Step 7、両者は同一リソース(MenuItem)を扱う)。
 *
 * <p>本コントローラは自前の権限判定ロジックを持たず、呼び出しごとに{@link ActiveRoleResolver}でactiveRoleIdを解決し、{@link
 * PermissionEngineApi#canAccessScreen}へ委譲する(project.md Mandated)。
 *
 * <p><b>401/403の切り分け(code-generation-plan.md「前提事項2」)</b>: C3契約は{@code GET /api/menu}に401のみを宣言し
 * (403は宣言されていない。BR6.3の権限フィルタは「除外」であり「エラー」ではないため)、{@code
 * /api/menu-items}には401と403の両方を宣言している。activeRoleIdを解決できない場合は{@code GET
 * /api/menu}・{@code /api/menu-items}のいずれも401とし、{@code /api/menu-items}のみ、activeRoleIdは解決できたが{@code
 * canAccessScreen}が{@code false}を返す場合を403とする。
 */
@RestController
public class MenuController {

  private static final Logger LOG = LoggerFactory.getLogger(MenuController.class);

  /** BR6.8: 業務メニューCRUD APIの認可に用いる予約screenKey(schema-introspectorと共有)。 */
  private static final String MENU_ITEMS_SCREEN_KEY = "config-import-export";

  private static final String GENERIC_UNAUTHORIZED_DETAIL = "認証情報を確認できませんでした。";
  private static final String GENERIC_FORBIDDEN_DETAIL = "この操作を実行する権限がありません。";

  private final MenuQueryService menuQueryService;
  private final MenuItemCommandService menuItemCommandService;
  private final PermissionEngineApi permissionEngineApi;
  private final ActiveRoleResolver activeRoleResolver;
  private final Timer getMenuDurationTimer;
  private final Counter getMenuErrorCounter;
  private final Timer menuItemsCrudDurationTimer;
  private final Counter menuItemsCrudErrorCounter;

  public MenuController(
      MenuQueryService menuQueryService,
      MenuItemCommandService menuItemCommandService,
      PermissionEngineApi permissionEngineApi,
      ActiveRoleResolver activeRoleResolver,
      MeterRegistry meterRegistry) {
    this.menuQueryService = menuQueryService;
    this.menuItemCommandService = menuItemCommandService;
    this.permissionEngineApi = permissionEngineApi;
    this.activeRoleResolver = activeRoleResolver;
    this.getMenuDurationTimer =
        Timer.builder("menu_navigation.get_menu.duration")
            .description("GET /api/menu request latency")
            .register(meterRegistry);
    this.getMenuErrorCounter =
        Counter.builder("menu_navigation.get_menu.error_count")
            .description("Number of failed GET /api/menu requests (401, cumulative)")
            .register(meterRegistry);
    this.menuItemsCrudDurationTimer =
        Timer.builder("menu_navigation.menu_items_crud.duration")
            .description("POST/PUT/DELETE /api/menu-items request latency")
            .register(meterRegistry);
    this.menuItemsCrudErrorCounter =
        Counter.builder("menu_navigation.menu_items_crud.error_count")
            .description("Number of failed /api/menu-items requests (400/401/403/404/409, cumulative)")
            .register(meterRegistry);
  }

  @GetMapping("/api/menu")
  public ResponseEntity<MenuResponse> getMenu(HttpServletRequest httpRequest) {
    Timer.Sample sample = Timer.start();
    try {
      String activeRoleId = activeRoleResolver.resolveActiveRoleId(httpRequest);
      if (activeRoleId == null) {
        // GET /api/menuの高頻度パスのため個別INFOログは出力しない(observability-design.md)。
        throw new MenuUnauthorizedException("Unable to resolve activeRoleId for GET /api/menu");
      }
      return ResponseEntity.ok(menuQueryService.getMenu(activeRoleId));
    } catch (RuntimeException e) {
      getMenuErrorCounter.increment();
      throw e;
    } finally {
      sample.stop(getMenuDurationTimer);
    }
  }

  @PostMapping("/api/menu-items")
  public ResponseEntity<MenuItemView> create(
      @Valid @RequestBody MenuItemInput input, HttpServletRequest httpRequest) {
    Timer.Sample sample = Timer.start();
    try {
      String activeRoleId = authorizeMenuItemsRequest(httpRequest);
      MenuItem created = menuItemCommandService.create(input);
      LOG.info(
          "MenuItem created: activeRoleId={}, menuItemId={}", activeRoleId, created.getMenuItemId());
      return ResponseEntity.status(HttpStatus.CREATED).body(toView(created));
    } catch (RuntimeException e) {
      menuItemsCrudErrorCounter.increment();
      throw e;
    } finally {
      sample.stop(menuItemsCrudDurationTimer);
    }
  }

  @PutMapping("/api/menu-items/{menuItemId}")
  public ResponseEntity<MenuItemView> update(
      @PathVariable String menuItemId,
      @Valid @RequestBody MenuItemInput input,
      HttpServletRequest httpRequest) {
    Timer.Sample sample = Timer.start();
    try {
      String activeRoleId = authorizeMenuItemsRequest(httpRequest);
      MenuItem updated = menuItemCommandService.update(menuItemId, input);
      LOG.info("MenuItem updated: activeRoleId={}, menuItemId={}", activeRoleId, menuItemId);
      return ResponseEntity.ok(toView(updated));
    } catch (RuntimeException e) {
      menuItemsCrudErrorCounter.increment();
      throw e;
    } finally {
      sample.stop(menuItemsCrudDurationTimer);
    }
  }

  @DeleteMapping("/api/menu-items/{menuItemId}")
  public ResponseEntity<Void> delete(
      @PathVariable String menuItemId, HttpServletRequest httpRequest) {
    Timer.Sample sample = Timer.start();
    try {
      String activeRoleId = authorizeMenuItemsRequest(httpRequest);
      menuItemCommandService.delete(menuItemId);
      LOG.info("MenuItem deleted: activeRoleId={}, menuItemId={}", activeRoleId, menuItemId);
      return ResponseEntity.noContent().build();
    } catch (RuntimeException e) {
      menuItemsCrudErrorCounter.increment();
      throw e;
    } finally {
      sample.stop(menuItemsCrudDurationTimer);
    }
  }

  /** {@code /api/menu-items}共通の認可判定(401/403、BR6.8)。 */
  private String authorizeMenuItemsRequest(HttpServletRequest httpRequest) {
    String activeRoleId = activeRoleResolver.resolveActiveRoleId(httpRequest);
    if (activeRoleId == null) {
      throw new MenuUnauthorizedException("Unable to resolve activeRoleId for /api/menu-items");
    }
    if (!permissionEngineApi.canAccessScreen(activeRoleId, MENU_ITEMS_SCREEN_KEY)) {
      LOG.warn("MenuItem access denied: activeRoleId={}", activeRoleId);
      throw new MenuItemForbiddenException("MenuItem access denied for activeRoleId=" + activeRoleId);
    }
    return activeRoleId;
  }

  /** C3契約の{@code MenuItem}スキーマは作成・更新結果に{@code order}を含めず、childrenも空配列とする。 */
  private static MenuItemView toView(MenuItem entity) {
    return new MenuItemView(
        entity.getMenuItemId(), entity.getLabel(), entity.getTargetTableConfigId(), List.of());
  }

  @ExceptionHandler(MenuUnauthorizedException.class)
  public ResponseEntity<ProblemDetail> handleUnauthorized(MenuUnauthorizedException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, GENERIC_UNAUTHORIZED_DETAIL);
    problem.setTitle("Unauthorized");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }

  @ExceptionHandler(MenuItemForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(MenuItemForbiddenException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, GENERIC_FORBIDDEN_DETAIL);
    problem.setTitle("MenuItem access forbidden");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(MenuItemValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidationFailure(MenuItemValidationException e) {
    LOG.error("MenuItem validation failed: {}", e.getMessage());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setTitle("Invalid menu item");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(MenuItemNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(MenuItemNotFoundException e) {
    LOG.error("MenuItem not found: {}", e.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    problem.setTitle("MenuItem not found");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }

  @ExceptionHandler(MenuItemConflictException.class)
  public ResponseEntity<ProblemDetail> handleConflict(MenuItemConflictException e) {
    LOG.error("MenuItem delete conflict: {}", e.getMessage());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "子項目を持つメニュー項目は削除できません。先に子項目を削除または付け替えてください。");
    problem.setTitle("Conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }
}
