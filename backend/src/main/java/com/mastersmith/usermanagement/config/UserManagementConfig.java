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

package com.mastersmith.usermanagement.config;

import com.mastersmith.usermanagement.mail.InvitationMailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * user-managementの設定プロパティ({@code mastersmith.users.*}・{@code
 * mastersmith.users.initial-admin.*}・{@code mastersmith.mail.*})の有効化。
 */
@Configuration
@EnableConfigurationProperties({
  UserManagementProperties.class,
  InvitationMailProperties.class,
  InitialAdminProperties.class
})
public class UserManagementConfig {}
