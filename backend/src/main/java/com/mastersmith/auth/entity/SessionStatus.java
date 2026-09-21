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

package com.mastersmith.auth.entity;

/**
 * Sessionの状態(entities.md)。{@code ACTIVE}は有効、{@code REVOKED}は失効済み(ログアウト・無効になったトークンの再使用・ユーザーの無効化の検知)。
 * 失効したSessionは、再び{@code ACTIVE}に戻らない。リフレッシュの有効期限の経過は、状態を変えず、時刻の比較で判定する。
 */
public enum SessionStatus {
  ACTIVE,
  REVOKED
}
