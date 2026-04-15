package org.labkey.mcp;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-access layer for per-user chat history stored in the mcp schema.
 * All reads and writes are scoped to the given User so authorization is
 * enforced here rather than in individual controller actions.
 */
public class ChatHistoryManager
{
    private static final ChatHistoryManager _instance = new ChatHistoryManager();

    private ChatHistoryManager() {}

    public static ChatHistoryManager get()
    {
        return _instance;
    }

    // -------------------------------------------------------------------------
    // Records
    // -------------------------------------------------------------------------

    public record Chat(long rowId, String remoteId, int userId, String title,
                       String status, Date created, Date modified) {}

    public record ChatMessage(long rowId, long chatId, String messageId, String parentId,
                              int sequence, String format, String content, Date created) {}

    // -------------------------------------------------------------------------
    // Chat operations
    // -------------------------------------------------------------------------

    /**
     * Returns the chat with the given remoteId owned by user, or null if not found.
     */
    @Nullable
    public Chat getChat(User user, String remoteId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("remoteId"), remoteId)
                .addCondition(FieldKey.fromString("userId"), user.getUserId());
        return new TableSelector(McpSchema.getInstance().getTableInfoChats(), filter, null)
                .getObject(Chat.class);
    }

    /**
     * Creates a chat row. Idempotent: if a row already exists for this (userId, remoteId), returns it.
     */
    public Chat createChat(User user, String remoteId, @Nullable String title)
    {
        Chat existing = getChat(user, remoteId);
        if (existing != null)
            return existing;

        Date now = new Date();
        Map<String, Object> row = new HashMap<>();
        row.put("remoteId", remoteId);
        row.put("userId", user.getUserId());
        row.put("title", title);
        row.put("status", "regular");
        row.put("created", now);
        row.put("modified", now);

        Map<String, Object> inserted = Table.insert(user, McpSchema.getInstance().getTableInfoChats(), row);
        return getChat(user, remoteId);
    }

    /**
     * Returns all chats owned by user, ordered by modified DESC.
     * If includeArchived is false, only 'regular' chats are returned.
     */
    public List<Chat> listChats(User user, boolean includeArchived)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("userId"), user.getUserId());
        if (!includeArchived)
            filter.addCondition(FieldKey.fromString("status"), "regular");

        Sort sort = new Sort("-modified");
        return new TableSelector(McpSchema.getInstance().getTableInfoChats(), filter, sort)
                .getArrayList(Chat.class);
    }

    /**
     * Updates the title of a chat owned by user.
     */
    public void renameChat(User user, String remoteId, String newTitle)
    {
        Chat chat = requireChat(user, remoteId);
        Map<String, Object> row = new HashMap<>();
        row.put("rowId", chat.rowId());
        row.put("title", newTitle);
        row.put("modified", new Date());
        Table.update(user, McpSchema.getInstance().getTableInfoChats(), row,
                chat.rowId());
    }

    /**
     * Sets the status ('regular' or 'archived') of a chat owned by user.
     */
    public void setStatus(User user, String remoteId, String status)
    {
        Chat chat = requireChat(user, remoteId);
        Map<String, Object> row = new HashMap<>();
        row.put("rowId", chat.rowId());
        row.put("status", status);
        row.put("modified", new Date());
        Table.update(user, McpSchema.getInstance().getTableInfoChats(), row,
                chat.rowId());
    }

    /**
     * Deletes a chat and all its messages.
     */
    public void deleteChat(User user, String remoteId)
    {
        Chat chat = requireChat(user, remoteId);

        // Delete messages first (no FK cascade on LabKey Table.delete)
        SimpleFilter msgFilter = new SimpleFilter(FieldKey.fromString("chatId"), chat.rowId());
        Table.delete(McpSchema.getInstance().getTableInfoChatMessages(), msgFilter);

        // Delete chat row
        SimpleFilter chatFilter = new SimpleFilter(FieldKey.fromString("rowId"), chat.rowId());
        Table.delete(McpSchema.getInstance().getTableInfoChats(), chatFilter);
    }

    // -------------------------------------------------------------------------
    // Message operations
    // -------------------------------------------------------------------------

    /**
     * Appends a message to the chat. Idempotent on (chatId, messageId).
     * Bumps chat modified timestamp.
     */
    public ChatMessage appendMessage(User user, String remoteId, String messageId,
                                     @Nullable String parentId, String format, String content)
    {
        Chat chat = createChat(user, remoteId, null);

        // Idempotency: if this messageId already exists, return it
        SimpleFilter existingFilter = new SimpleFilter(FieldKey.fromString("chatId"), chat.rowId())
                .addCondition(FieldKey.fromString("messageId"), messageId);
        ChatMessage existing = new TableSelector(McpSchema.getInstance().getTableInfoChatMessages(),
                existingFilter, null).getObject(ChatMessage.class);
        if (existing != null)
            return existing;

        // Compute next sequence number
        int nextSeq = (int) new TableSelector(McpSchema.getInstance().getTableInfoChatMessages(),
                new SimpleFilter(FieldKey.fromString("chatId"), chat.rowId()), null).getRowCount() + 1;

        Date now = new Date();
        Map<String, Object> row = new HashMap<>();
        row.put("chatId", chat.rowId());
        row.put("messageId", messageId);
        row.put("parentId", parentId);
        row.put("sequence", nextSeq);
        row.put("format", format);
        row.put("content", content);
        row.put("created", now);

        Table.insert(user, McpSchema.getInstance().getTableInfoChatMessages(), row);

        // Bump chat modified
        Map<String, Object> chatUpdate = new HashMap<>();
        chatUpdate.put("rowId", chat.rowId());
        chatUpdate.put("modified", now);
        Table.update(user, McpSchema.getInstance().getTableInfoChats(), chatUpdate, chat.rowId());

        return new TableSelector(McpSchema.getInstance().getTableInfoChatMessages(),
                existingFilter, null).getObject(ChatMessage.class);
    }

    /**
     * Returns all messages for a chat owned by user, ordered by sequence ASC.
     */
    public List<ChatMessage> listMessages(User user, String remoteId)
    {
        Chat chat = requireChat(user, remoteId);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("chatId"), chat.rowId());
        Sort sort = new Sort("sequence");
        return new TableSelector(McpSchema.getInstance().getTableInfoChatMessages(), filter, sort)
                .getArrayList(ChatMessage.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Chat requireChat(User user, String remoteId)
    {
        Chat chat = getChat(user, remoteId);
        if (chat == null)
            throw new IllegalArgumentException("Chat not found: " + remoteId);
        return chat;
    }
}
