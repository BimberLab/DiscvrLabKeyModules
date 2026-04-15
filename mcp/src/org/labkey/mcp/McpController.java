package org.labkey.mcp;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.mcp.AbstractAgentAction;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.mcp.PromptForm;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class McpController extends SpringActionController
{
    public static final String NAME = "mcp";

    private static final ActionResolver _resolver = new DefaultActionResolver(McpController.class);

    public McpController()
    {
        setActionResolver(_resolver);
    }

    // =========================================================================
    // Chat agent — now accepts history for multi-turn memory
    // =========================================================================

    @RequiresLogin
    public static class ChatAgentAction extends AbstractAgentAction<ChatAgentForm>
    {
        public ChatAgentAction()
        {
            // BaseViewAction's no-arg constructor reflects over execute() methods to determine
            // the command class. Because AbstractAgentAction declares execute(PromptForm,…) —
            // not execute(F,…) — reflection resolves the type to PromptForm even though F=ChatAgentForm.
            // Override that here so Spring binds the JSON to ChatAgentForm instead of PromptForm.
            setCommandClass(ChatAgentForm.class);
        }

        @Override
        protected String getAgentName()
        {
            return "mcp.chat";
        }

        @Override
        protected String getServicePrompt()
        {
            return """
                You are a helpful assistant integrated with LabKey Server. You have access to tools that can \
                query database schemas, list tables and columns, search the site, and navigate containers/folders. \
                Always use the available tools to answer questions about the data in this server. \
                When a user asks about data, first check which container they want to work in using setContainer, \
                then use the query tools to explore schemas and tables. \
                Be concise and helpful in your responses.""";
        }

        @Override
        public Object execute(PromptForm baseForm, BindException errors) throws Exception
        {
            ChatAgentForm form = (ChatAgentForm) baseForm;

            try (var mcpPush = McpContext.withContext(getViewContext()))
            {
                String prompt = form.getPrompt();

                String escapeResponse = handleEscape(prompt);
                if (null != escapeResponse)
                {
                    return new JSONObject(Map.of(
                            "contentType", "text/plain",
                            "response", escapeResponse,
                            "success", Boolean.TRUE));
                }

                ChatClient chatSession = getChat(true);
                if (null == chatSession)
                    return new JSONObject(Map.of(
                            "contentType", "text/plain",
                            "response", "Service is not ready yet",
                            "success", Boolean.FALSE));

                // Build prior-message history for multi-turn memory
                List<Message> history = new ArrayList<>();
                if (form.getHistory() != null)
                {
                    for (ChatAgentForm.HistoryEntry entry : form.getHistory())
                    {
                        if ("user".equals(entry.role()) && StringUtils.isNotBlank(entry.text()))
                            history.add(new UserMessage(entry.text()));
                        else if ("assistant".equals(entry.role()) && StringUtils.isNotBlank(entry.text()))
                            history.add(new AssistantMessage(entry.text()));
                    }
                }

                McpServiceImpl impl = (McpServiceImpl) McpService.get();
                McpService.MessageResponse response = history.isEmpty()
                        ? impl.sendMessage(chatSession, prompt)
                        : impl.sendMessage(chatSession, history, prompt);

                var ret = new JSONObject(Map.of("success", Boolean.TRUE));
                if (!HtmlString.isBlank(response.html()))
                {
                    ret.put("contentType", "text/html");
                    ret.put("response", response.html());
                }
                else if (StringUtils.isNotBlank(response.text()))
                {
                    ret.put("contentType", response.contentType());
                    ret.put("response", response.text());
                }
                else
                {
                    ret.put("contentType", "text/plain");
                    ret.put("response", "I got nothing");
                }
                return ret;
            }
        }
    }

    public static class ChatAgentForm extends PromptForm
    {
        private List<HistoryEntry> _history;

        public List<HistoryEntry> getHistory() { return _history; }
        public void setHistory(List<HistoryEntry> history) { _history = history; }

        public record HistoryEntry(String role, String text) {}
    }

    // =========================================================================
    // Chat history API actions (all @RequiresLogin, global per-user scope)
    // =========================================================================

    @RequiresLogin
    public static class ListChatsAction extends ReadOnlyApiAction<ListChatsForm>
    {
        @Override
        public ApiResponse execute(ListChatsForm form, BindException errors)
        {
            User user = getUser();
            boolean includeArchived = form.isArchived();
            List<ChatHistoryManager.Chat> chats = ChatHistoryManager.get().listChats(user, includeArchived);

            JSONArray arr = new JSONArray();
            for (ChatHistoryManager.Chat c : chats)
            {
                JSONObject obj = new JSONObject();
                obj.put("remoteId", c.remoteId());
                obj.put("title", c.title() != null ? c.title() : JSONObject.NULL);
                obj.put("status", c.status());
                obj.put("modified", c.modified() != null ? c.modified().getTime() : JSONObject.NULL);
                arr.put(obj);
            }
            return new ApiSimpleResponse(Map.of("chats", arr));
        }
    }

    public static class ListChatsForm
    {
        private boolean _archived = false;
        public boolean isArchived() { return _archived; }
        public void setArchived(boolean archived) { _archived = archived; }
    }

    @RequiresLogin
    public static class CreateChatAction extends MutatingApiAction<CreateChatForm>
    {
        @Override
        public ApiResponse execute(CreateChatForm form, BindException errors)
        {
            ChatHistoryManager.Chat chat = ChatHistoryManager.get()
                    .createChat(getUser(), form.getRemoteId(), null);
            return new ApiSimpleResponse(Map.of(
                    "remoteId", chat.remoteId(),
                    "title", chat.title() != null ? chat.title() : ""));
        }
    }

    public static class CreateChatForm
    {
        private String _remoteId;
        public String getRemoteId() { return _remoteId; }
        public void setRemoteId(String remoteId) { _remoteId = remoteId; }
    }

    @RequiresLogin
    public static class GetMessagesAction extends ReadOnlyApiAction<GetMessagesForm>
    {
        @Override
        public ApiResponse execute(GetMessagesForm form, BindException errors)
        {
            User user = getUser();
            List<ChatHistoryManager.ChatMessage> msgs =
                    ChatHistoryManager.get().listMessages(user, form.getRemoteId());

            JSONArray arr = new JSONArray();
            for (ChatHistoryManager.ChatMessage m : msgs)
            {
                JSONObject obj = new JSONObject();
                obj.put("messageId", m.messageId());
                obj.put("parentId", m.parentId() != null ? m.parentId() : JSONObject.NULL);
                obj.put("format", m.format());
                obj.put("content", m.content());
                arr.put(obj);
            }
            return new ApiSimpleResponse(Map.of("messages", arr));
        }
    }

    public static class GetMessagesForm
    {
        private String _remoteId;
        public String getRemoteId() { return _remoteId; }
        public void setRemoteId(String remoteId) { _remoteId = remoteId; }
    }

    @RequiresLogin
    public static class AppendMessageAction extends MutatingApiAction<AppendMessageForm>
    {
        @Override
        public ApiResponse execute(AppendMessageForm form, BindException errors)
        {
            ChatHistoryManager.get().appendMessage(
                    getUser(),
                    form.getRemoteId(),
                    form.getMessageId(),
                    form.getParentId(),
                    form.getFormat(),
                    form.getContent());
            return new ApiSimpleResponse(Map.of("ok", true));
        }
    }

    public static class AppendMessageForm
    {
        private String _remoteId;
        private String _messageId;
        private String _parentId;
        private String _format;
        private String _content;

        public String getRemoteId() { return _remoteId; }
        public void setRemoteId(String v) { _remoteId = v; }
        public String getMessageId() { return _messageId; }
        public void setMessageId(String v) { _messageId = v; }
        public String getParentId() { return _parentId; }
        public void setParentId(String v) { _parentId = v; }
        public String getFormat() { return _format; }
        public void setFormat(String v) { _format = v; }
        public String getContent() { return _content; }
        public void setContent(String v) { _content = v; }
    }

    @RequiresLogin
    public static class RenameChatAction extends MutatingApiAction<RenameChatForm>
    {
        @Override
        public ApiResponse execute(RenameChatForm form, BindException errors)
        {
            ChatHistoryManager.get().renameChat(getUser(), form.getRemoteId(), form.getTitle());
            return new ApiSimpleResponse(Map.of("ok", true));
        }
    }

    public static class RenameChatForm
    {
        private String _remoteId;
        private String _title;
        public String getRemoteId() { return _remoteId; }
        public void setRemoteId(String v) { _remoteId = v; }
        public String getTitle() { return _title; }
        public void setTitle(String v) { _title = v; }
    }

    @RequiresLogin
    public static class SetArchivedAction extends MutatingApiAction<SetArchivedForm>
    {
        @Override
        public ApiResponse execute(SetArchivedForm form, BindException errors)
        {
            String status = form.isArchived() ? "archived" : "regular";
            ChatHistoryManager.get().setStatus(getUser(), form.getRemoteId(), status);
            return new ApiSimpleResponse(Map.of("ok", true));
        }
    }

    public static class SetArchivedForm
    {
        private String _remoteId;
        private boolean _archived;
        public String getRemoteId() { return _remoteId; }
        public void setRemoteId(String v) { _remoteId = v; }
        public boolean isArchived() { return _archived; }
        public void setArchived(boolean v) { _archived = v; }
    }

    @RequiresLogin
    public static class DeleteChatAction extends MutatingApiAction<DeleteChatForm>
    {
        @Override
        public ApiResponse execute(DeleteChatForm form, BindException errors)
        {
            ChatHistoryManager.get().deleteChat(getUser(), form.getRemoteId());
            return new ApiSimpleResponse(Map.of("ok", true));
        }
    }

    public static class DeleteChatForm
    {
        private String _remoteId;
        public String getRemoteId() { return _remoteId; }
        public void setRemoteId(String v) { _remoteId = v; }
    }

    /**
     * Generates a short title for a chat using the LLM, stores it, and returns it.
     * Accepts { remoteId, sample } where sample is the first user message text.
     */
    @RequiresLogin
    public static class GenerateTitleAction extends MutatingApiAction<GenerateTitleForm>
    {
        @Override
        public ApiResponse execute(GenerateTitleForm form, BindException errors) throws Exception
        {
            try (var mcpPush = McpContext.withContext(getViewContext()))
            {
                McpServiceImpl impl = (McpServiceImpl) McpService.get();
                ChatClient titleClient = impl.buildTitleClient();

                String title = titleClient.prompt()
                        .user(form.getSample())
                        .call()
                        .content();

                if (title != null)
                    title = title.trim().replaceAll("^[\"']+|[\"']+$", "");

                if (StringUtils.isBlank(title))
                    title = "New chat";

                ChatHistoryManager.get().renameChat(getUser(), form.getRemoteId(), title);
                return new ApiSimpleResponse(Map.of("title", title));
            }
        }
    }

    public static class GenerateTitleForm
    {
        private String _remoteId;
        private String _sample;
        public String getRemoteId() { return _remoteId; }
        public void setRemoteId(String v) { _remoteId = v; }
        public String getSample() { return _sample; }
        public void setSample(String v) { _sample = v; }
    }

    // =========================================================================
    // Views
    // =========================================================================

    @RequiresPermission(ReadPermission.class)
    public static class ChatViewAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            ModuleHtmlView view = ModuleHtmlView.get(ModuleLoader.getInstance().getModule(McpModule.class), "mcpChat");
            if (view == null)
            {
                return new HtmlView(HtmlString.of("MCP Chat frontend has not been built. Run 'npm run build' in the mcp module directory."));
            }
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("MCP Chat");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class SettingsAction extends FormViewAction<McpSettingsForm>
    {
        @Override
        public void validateCommand(McpSettingsForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(McpSettingsForm form, boolean reshow, BindException errors)
        {
            form.setApiKey(McpSettings.getApiKey() != null ? "********" : "");
            form.setModelName(McpSettings.getModelName());
            return new JspView<>("/org/labkey/mcp/settings.jsp", form, errors);
        }

        @Override
        public boolean handlePost(McpSettingsForm form, BindException errors)
        {
            String apiKey = form.getApiKey();
            // Don't overwrite with the masked value
            if ("********".equals(apiKey))
            {
                apiKey = McpSettings.getApiKey();
            }
            McpSettings.save(apiKey, form.getModelName());

            // Reset the cached ChatModel so it picks up new settings
            McpServiceImpl impl = (McpServiceImpl) McpService.get();
            impl.resetChatModel();

            return true;
        }

        @Override
        public URLHelper getSuccessURL(McpSettingsForm form)
        {
            return null; // Re-show the form
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("MCP Settings");
        }
    }

    public static class McpSettingsForm
    {
        private String _apiKey;
        private String _modelName;

        public String getApiKey()
        {
            return _apiKey;
        }

        public void setApiKey(String apiKey)
        {
            _apiKey = apiKey;
        }

        public String getModelName()
        {
            return _modelName;
        }

        public void setModelName(String modelName)
        {
            _modelName = modelName;
        }
    }
}
