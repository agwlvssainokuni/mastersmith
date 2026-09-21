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

package com.mastersmith.dataio.service;

import com.mastersmith.config.entity.TableConfig;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 業務データ用RDBMSへ発行するSQLの識別子(スキーマ名・テーブル名・列名)を、接続先のJDBCドライバが報告する引用符 ({@link
 * java.sql.DatabaseMetaData#getIdentifierQuoteString()})で囲むための補助(レビュー指摘R-06(4)対応)。
 *
 * <p>PostgreSQL/H2は{@code "}、MySQL/MariaDBは<code>`</code>をドライバが返すため、方言ごとの分岐を持たずに 予約語({@code
 * order}・{@code group}等)の識別子を扱える。config-engineが保持する名称は、スキーマ探索時に
 * RDBMSのメタデータから取得した実名そのものであるため、引用符で囲んでも大文字小文字は実名と一致する。
 * 引用符自体を含む名称は、引用符を二重化してエスケープする(設定値経由のSQL断片混入への多層防御)。
 */
final class SqlIdentifiers {

  /** ドライバが引用符を報告しない・報告できない場合の既定(標準SQLの二重引用符)。 */
  private static final String DEFAULT_QUOTE = "\"";

  private SqlIdentifiers() {}

  /** 接続先が識別子の引用符として用いる文字列を取得する。 */
  static String quoteStringOf(Connection connection) throws SQLException {
    String quote = connection.getMetaData().getIdentifierQuoteString();
    if (quote == null) {
      return DEFAULT_QUOTE;
    }
    // 引用符機構をサポートしないドライバは、単一の空白文字を返す(JDBC仕様)。
    return quote.isBlank() ? "" : quote;
  }

  static String quote(String quote, String identifier) {
    if (quote.isEmpty()) {
      return identifier;
    }
    return quote + identifier.replace(quote, quote + quote) + quote;
  }

  /** {@code schema.table}形式の完全修飾テーブル名(スキーマ名が空の場合はテーブル名のみ)。 */
  static String qualifiedTableName(String quote, TableConfig tableConfig) {
    String table = quote(quote, tableConfig.getTableName());
    String schema = tableConfig.getSchemaName();
    if (schema == null || schema.isBlank()) {
      return table;
    }
    return quote(quote, schema) + "." + table;
  }
}
