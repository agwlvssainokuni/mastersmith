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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.menu.dto.MenuItemView;
import com.mastersmith.menu.dto.MenuResponse;
import com.mastersmith.menu.entity.MenuItem;
import com.mastersmith.menu.exception.MenuItemConflictException;
import com.mastersmith.menu.exception.MenuItemNotFoundException;
import com.mastersmith.menu.exception.MenuItemValidationException;
import com.mastersmith.menu.service.MenuItemCommandService;
import com.mastersmith.menu.service.MenuQueryService;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.schema.security.ActiveRoleResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link MenuController}の単体テスト(C3: {@code GET /api/menu}の200/401、{@code
 * /api/menu-items}の201/200/204・400・401・403・404・409、code-generation-plan.md Step8)。{@link
 * MenuQueryService}・{@link MenuItemCommandService}・{@link PermissionEngineApi}・{@link
 * ActiveRoleResolver}をモックし、HTTP層(ステータスコード・ProblemDetail形式)の検証に専念する。
 *
 * <p>認可拒否(negative-authorization)専用テスト(team.md Q8-c)として{@link
 * #returns403WhenPermissionIsDeniedForMenuItemsCrud()}を含む。
 */
@WebMvcTest(MenuController.class)
class MenuControllerTest {

  private static final String MENU_ENDPOINT = "/api/menu";
  private static final String MENU_ITEMS_ENDPOINT = "/api/menu-items";
  private static final String ACTIVE_ROLE_ID = "role-1";
  private static final String VALID_INPUT_BODY =
      "{\"parentMenuItemId\":null,\"label\":\"商品マスタ\",\"order\":1,\"targetTableConfigId\":\"table-config-1\"}";
  private static final String BLANK_LABEL_INPUT_BODY =
      "{\"parentMenuItemId\":null,\"label\":\"\",\"order\":1,\"targetTableConfigId\":null}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MenuQueryService menuQueryService;
  @MockitoBean private MenuItemCommandService menuItemCommandService;
  @MockitoBean private PermissionEngineApi permissionEngineApi;
  @MockitoBean private ActiveRoleResolver activeRoleResolver;

  @TestConfiguration
  static class MeterRegistryTestConfig {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Test
  void returns200WithBusinessMenuAndAdminMenuWhenAuthenticated() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    MenuItemView businessItem = new MenuItemView("item-1", "商品マスタ", "table-config-1", List.of());
    MenuItemView adminItem = new MenuItemView("admin-user-management", "ユーザ管理", null, List.of());
    when(menuQueryService.getMenu(ACTIVE_ROLE_ID))
        .thenReturn(new MenuResponse(List.of(businessItem), List.of(adminItem)));

    mockMvc
        .perform(get(MENU_ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.businessMenu[0].menuItemId").value("item-1"))
        .andExpect(jsonPath("$.adminMenu[0].menuItemId").value("admin-user-management"));
  }

  @Test
  void returns401WhenActiveRoleIdCannotBeResolvedForGetMenu() throws Exception {
    // 前提事項2: GET /api/menuはactiveRoleId未解決時に401とし、403は用いない。
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(null);

    mockMvc.perform(get(MENU_ENDPOINT)).andExpect(status().isUnauthorized());

    verifyNoInteractions(menuQueryService);
  }

  @Test
  void returns201WhenMenuItemIsCreated() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(true);
    when(menuItemCommandService.create(any()))
        .thenReturn(new MenuItem("item-1", null, "商品マスタ", 1, "table-config-1"));

    mockMvc
        .perform(post(MENU_ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.menuItemId").value("item-1"));
  }

  @Test
  void returns401WhenActiveRoleIdCannotBeResolvedForMenuItemsCrud() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(null);

    mockMvc
        .perform(post(MENU_ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT_BODY))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(menuItemCommandService);
    verifyNoInteractions(permissionEngineApi);
  }

  @Test
  void returns403WhenPermissionIsDeniedForMenuItemsCrud() throws Exception {
    // 認可拒否(negative-authorization)専用テスト(team.md Q8-c)。
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(false);

    mockMvc
        .perform(post(MENU_ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT_BODY))
        .andExpect(status().isForbidden());

    verifyNoInteractions(menuItemCommandService);
  }

  @Test
  void returns4xxWhenLabelIsBlank() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(true);

    mockMvc
        .perform(
            post(MENU_ITEMS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BLANK_LABEL_INPUT_BODY))
        .andExpect(status().is4xxClientError());

    verifyNoInteractions(menuItemCommandService);
  }

  @Test
  void returns400WhenTargetTableConfigIdDoesNotExist() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(true);
    when(menuItemCommandService.create(any()))
        .thenThrow(new MenuItemValidationException("targetTableConfigId does not exist"));

    mockMvc
        .perform(post(MENU_ITEMS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void returns200WhenMenuItemIsUpdated() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(true);
    when(menuItemCommandService.update(eq("item-1"), any()))
        .thenReturn(new MenuItem("item-1", null, "商品マスタ", 1, "table-config-1"));

    mockMvc
        .perform(
            put(MENU_ITEMS_ENDPOINT + "/item-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_INPUT_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.menuItemId").value("item-1"));
  }

  @Test
  void returns404WhenUpdatingAMenuItemThatDoesNotExist() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(true);
    when(menuItemCommandService.update(eq("does-not-exist"), any()))
        .thenThrow(new MenuItemNotFoundException("not found"));

    mockMvc
        .perform(
            put(MENU_ITEMS_ENDPOINT + "/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_INPUT_BODY))
        .andExpect(status().isNotFound());
  }

  @Test
  void returns204WhenMenuItemIsDeleted() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(true);

    mockMvc.perform(delete(MENU_ITEMS_ENDPOINT + "/item-1")).andExpect(status().isNoContent());
  }

  @Test
  void returns409WhenDeletingAMenuItemThatHasChildren() throws Exception {
    when(activeRoleResolver.resolveActiveRoleId(any())).thenReturn(ACTIVE_ROLE_ID);
    when(permissionEngineApi.canAccessScreen(ACTIVE_ROLE_ID, "config-import-export")).thenReturn(true);
    doThrow(new MenuItemConflictException("has children")).when(menuItemCommandService).delete("item-1");

    mockMvc.perform(delete(MENU_ITEMS_ENDPOINT + "/item-1")).andExpect(status().isConflict());
  }
}
