/*
 * Copyright (c) 2026 LabKey Corporation
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

CREATE SCHEMA mcp;

CREATE TABLE mcp.chats (
    rowId       BIGSERIAL    NOT NULL,
    remoteId    VARCHAR(64)  NOT NULL,
    userId      USERID       NOT NULL,
    title       VARCHAR(255),
    status      VARCHAR(20)  NOT NULL DEFAULT 'regular',
    created     TIMESTAMP    NOT NULL,
    modified    TIMESTAMP    NOT NULL,
    CONSTRAINT PK_mcp_chats PRIMARY KEY (rowId),
    CONSTRAINT UQ_mcp_chats_remoteId UNIQUE (remoteId)
);

CREATE INDEX IX_mcp_chats_user ON mcp.chats (userId, status, modified);

CREATE TABLE mcp.chatMessages (
    rowId       BIGSERIAL    NOT NULL,
    chatId      BIGINT       NOT NULL,
    messageId   VARCHAR(64)  NOT NULL,
    parentId    VARCHAR(64),
    sequence    INT          NOT NULL,
    format      VARCHAR(32)  NOT NULL,
    content     TEXT         NOT NULL,
    created     TIMESTAMP    NOT NULL,
    CONSTRAINT PK_mcp_chatMessages PRIMARY KEY (rowId),
    CONSTRAINT FK_mcp_chatMessages_chatId FOREIGN KEY (chatId) REFERENCES mcp.chats(rowId),
    CONSTRAINT UQ_mcp_chatMessages UNIQUE (chatId, messageId)
);

CREATE INDEX IX_mcp_chatMessages_chat ON mcp.chatMessages (chatId, sequence);
