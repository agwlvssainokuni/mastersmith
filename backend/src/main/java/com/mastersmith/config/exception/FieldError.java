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

package com.mastersmith.config.exception;

/**
 * フィールド単位の設定バリデーションエラー(rules.md BR1.11)。
 *
 * <p>開発者向けのスタックトレースや内部実装詳細(SQL文等)は含まず、エラーの生じた フィールド名とルール種別のみを保持する。呼び出し元(config-import-export、C7契約)は
 * この情報をRFC 9457のerrors配列(field, message)へマッピングする。
 *
 * @param field エラーの生じたフィールド(例: "schemaName", "columnConfigId:columnName")
 * @param ruleType 違反したルール種別(例: "required", "choiceOrFkExclusive", "unsupportedRdbmsType")
 */
public record FieldError(String field, String ruleType) {}
