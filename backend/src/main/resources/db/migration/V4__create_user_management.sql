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

-- user-management(U4): User・UserPreference(entities.md)の永続化先。
--
-- users:
--   - email(正規化後・小文字)と invitation_token に一意制約を設ける。invitation_tokenは
--     status=invitedの間のみ非nullで、nullは一意制約の対象外(複数行がnullを持てる)。
--   - 一意制約のインデックスが、emailによる検索・email昇順の一覧(NFR3.1)と、招待トークンによる
--     検索(NFR2.4)を支える。
--   - password_hashは招待中(invited)はnull(Argon2id、NFR2.2)。
--   - status・列挙型の列は、他ユニットのマイグレーション(V1)と同じくH2のENUM型で表す
--     (Hibernateの@Enumerated(STRING)の既定のマッピングに一致させ、ddl-auto: validateを通すため)。
--
-- user_role: Userの直接付与のroleId(要素コレクション)。role_idはpermission-engineのRoleへの
-- 「不透明な文字列参照」のため、物理FK制約は設けない(実在検証はアプリケーション層、BR4.5)。
-- user_idはこのテーブル群の内側の参照のため、FK制約を設ける。
--
-- user_preference: ユーザー単位の表示設定。招待受諾時または初期管理者の作成時に作成する(BR4.3)。
CREATE TABLE users (
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    status ENUM('ACTIVE', 'DISABLED', 'INVITED') NOT NULL,
    invitation_token VARCHAR(36),
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_invitation_token UNIQUE (invitation_token)
);

CREATE TABLE user_role (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE user_preference (
    user_id VARCHAR(36) NOT NULL,
    theme ENUM('DARK', 'LIGHT') NOT NULL,
    font_size ENUM('LARGE', 'MEDIUM', 'SMALL') NOT NULL,
    locale ENUM('EN', 'JA') NOT NULL,
    CONSTRAINT pk_user_preference PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
