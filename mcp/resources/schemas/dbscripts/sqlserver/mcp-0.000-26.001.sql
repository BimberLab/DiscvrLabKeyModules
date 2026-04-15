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
GO

CREATE TABLE mcp.chats (
    rowId       BIGINT IDENTITY(1,1) NOT NULL,
    remoteId    NVARCHAR(64)         NOT NULL,
    userId      USERID               NOT NULL,
    title       NVARCHAR(255)        NULL,
    status      NVARCHAR(20)         NOT NULL CONSTRAINT DF_mcp_chats_status DEFAULT 'regular',
    created     DATETIME             NOT NULL,
    modified    DATETIME             NOT NULL,
    CONSTRAINT PK_mcp_chats PRIMARY KEY (rowId),
    CONSTRAINT UQ_mcp_chats_remoteId UNIQUE (remoteId)
);
GO

CREATE INDEX IX_mcp_chats_user ON mcp.chats (userId, status, modified);
GO

CREATE TABLE mcp.chatMessages (
    rowId       BIGINT IDENTITY(1,1) NOT NULL,
    chatId      BIGINT               NOT NULL,
    messageId   NVARCHAR(64)         NOT NULL,
    parentId    NVARCHAR(64)         NULL,
    sequence    INT                  NOT NULL,
    format      NVARCHAR(32)         NOT NULL,
    content     NVARCHAR(MAX)        NOT NULL,
    created     DATETIME             NOT NULL,
    CONSTRAINT PK_mcp_chatMessages PRIMARY KEY (rowId),
    CONSTRAINT FK_mcp_chatMessages_chatId FOREIGN KEY (chatId) REFERENCES mcp.chats(rowId),
    CONSTRAINT UQ_mcp_chatMessages UNIQUE (chatId, messageId)
);
GO

CREATE INDEX IX_mcp_chatMessages_chat ON mcp.chatMessages (chatId, sequence);
GO
