-- Copyright 2026 agwlvssainokuni
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- authentication-service(U5): Session・AccountLoginState(entities.md)の永続化先。
-- 列・制約・インデックスは、nfr-design/scalability-design.md NFR3.4のとおり。
--
-- auth_session:
--   - session_idは、SecureRandomの128ビットをBase64URL(パディングなし)にした22文字。アクセストークンのsidに入る。
--   - refresh_token_hash・previous_refresh_token_hashは、リフレッシュトークン(256ビット)のSHA-256を
--     Base64URL(パディングなし)にした43文字。平文は保持しない。どちらも一意(NULLは複数行を許す)。
--   - refresh_expires_atのインデックスは、期限切れのSessionの定期削除(NFR4.5)の検索のため。
--   - user_idのインデックスは設けない(本ユニットに、user_idで検索する処理がないため)。
--   - user_id・active_role_idは、U4のUser・U3のRoleへの「不透明な文字列参照」のため、物理FK制約は設けない。
--   - status・列挙型の列は、他ユニットのマイグレーションと同じくH2のENUM型で表す(Hibernateの
--     @Enumerated(STRING)の既定のマッピングに一致させ、ddl-auto: validateを通すため)。
--   - 日時は、UTCのミリ秒精度(TIMESTAMP(3) WITH TIME ZONE)。
--
-- account_login_state:
--   - user_idごとに1行(activeなユーザーへの最初のログイン試行の予約で作る)。
--   - generationは、失敗回数のリセットのたびに1増える世代番号(補償の更新の条件、BR5.3)。
CREATE TABLE auth_session (
    session_id VARCHAR(22) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    active_role_id VARCHAR(255),
    refresh_token_hash VARCHAR(43) NOT NULL,
    previous_refresh_token_hash VARCHAR(43),
    issued_at TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    last_refreshed_at TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    refresh_expires_at TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    status ENUM('ACTIVE', 'REVOKED') NOT NULL,
    CONSTRAINT pk_auth_session PRIMARY KEY (session_id),
    CONSTRAINT uk_auth_session_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT uk_auth_session_previous_refresh_token_hash UNIQUE (previous_refresh_token_hash)
);

CREATE INDEX idx_auth_session_refresh_expires_at ON auth_session (refresh_expires_at);

CREATE TABLE account_login_state (
    user_id VARCHAR(255) NOT NULL,
    consecutive_failures INT DEFAULT 0 NOT NULL,
    locked_until TIMESTAMP(3) WITH TIME ZONE,
    generation BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_account_login_state PRIMARY KEY (user_id)
);
