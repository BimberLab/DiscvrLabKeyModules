package org.labkey.mcp;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;

public class McpSchema
{
    private static final McpSchema _instance = new McpSchema();

    public static final String NAME = "mcp";
    public static final String TABLE_CHATS = "chats";
    public static final String TABLE_CHAT_MESSAGES = "chatMessages";

    public static McpSchema getInstance()
    {
        return _instance;
    }

    private McpSchema()
    {
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public TableInfo getTableInfoChats()
    {
        return getSchema().getTable(TABLE_CHATS);
    }

    public TableInfo getTableInfoChatMessages()
    {
        return getSchema().getTable(TABLE_CHAT_MESSAGES);
    }
}
