package org.labkey.mcp;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Model;
import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.labkey.api.data.Container;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class McpServiceImpl implements McpService
{
    private static final Logger LOG = LogHelper.getLogger(McpServiceImpl.class, "MCP Service Implementation");

    private final List<ToolCallback> _tools = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, McpImpl> _toolImplMap = new ConcurrentHashMap<>();
    private final List<McpServerFeatures.SyncResourceSpecification> _resources = Collections.synchronizedList(new ArrayList<>());
    private final List<McpServerFeatures.SyncPromptSpecification> _prompts = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ChatClient> _chatSessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> _resourceMetrics = new ConcurrentHashMap<>();
    private final Map<String, Container> _sessionContainers = new ConcurrentHashMap<>();

    private volatile ChatModel _chatModel;

    @Override
    public boolean isReady()
    {
        return McpSettings.isConfigured();
    }

    @Override
    public void registerTools(@NotNull List<ToolCallback> tools, McpImpl mcp)
    {
        for (ToolCallback tool : tools)
        {
            String name = tool.getToolDefinition().name();
            if (_toolImplMap.containsKey(name))
            {
                LOG.error("Duplicate MCP tool name: " + name + ". Skipping registration from " + mcp.getClass().getName());
                continue;
            }
            _tools.add(tool);
            _toolImplMap.put(name, mcp);
            LOG.info("Registered MCP tool: " + name);
        }
    }

    @Override
    public void registerPrompts(@NotNull List<McpServerFeatures.SyncPromptSpecification> prompts)
    {
        _prompts.addAll(prompts);
    }

    @Override
    public void registerResources(@NotNull List<McpServerFeatures.SyncResourceSpecification> resources)
    {
        _resources.addAll(resources);
    }

    @Override
    public ToolCallback @NonNull [] getToolCallbacks()
    {
        return _tools.toArray(new ToolCallback[0]);
    }

    public List<McpServerFeatures.SyncResourceSpecification> getResources()
    {
        return List.copyOf(_resources);
    }

    public List<McpServerFeatures.SyncPromptSpecification> getPrompts()
    {
        return List.copyOf(_prompts);
    }

    public Map<String, McpImpl> getToolImplMap()
    {
        return Collections.unmodifiableMap(_toolImplMap);
    }

    @Override
    public ChatClient getChat(HttpSession session, String agentName, Supplier<String> systemPromptSupplier, boolean createIfNotExists)
    {
        String key = session.getId() + ":" + agentName;

        ChatClient existing = _chatSessions.get(key);
        if (existing != null)
            return existing;

        if (!createIfNotExists)
            return null;

        ChatModel chatModel = getChatModel();
        if (chatModel == null)
        {
            LOG.warn("Cannot create ChatClient: no LLM API key configured");
            return null;
        }

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(systemPromptSupplier.get())
                .defaultToolCallbacks(getToolCallbacks())
                .build();

        _chatSessions.put(key, chatClient);
        return chatClient;
    }

    @Override
    public void close(HttpSession session, ChatClient chat)
    {
        _chatSessions.entrySet().removeIf(entry ->
                entry.getKey().startsWith(session.getId() + ":") && entry.getValue() == chat);
    }

    @Override
    public void saveSessionContainer(ToolContext context, Container container)
    {
        HttpSession session = (HttpSession) context.getContext().get("session");
        if (session != null)
        {
            _sessionContainers.put(session.getId(), container);
        }
    }

    @Override
    public void incrementResourceRequestCount(String resource)
    {
        _resourceMetrics.computeIfAbsent(resource, k -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public MessageResponse sendMessage(ChatClient chat, String message)
    {
        try
        {
            // Build the tool context from the ThreadLocal McpContext so tools get User and Container
            Map<String, Object> toolContext = new HashMap<>();
            try
            {
                McpContext ctx = McpContext.get();
                toolContext.put("user", ctx.getUser());
                if (ctx.getContainer() != null)
                    toolContext.put("container", ctx.getContainer());
            }
            catch (IllegalStateException e)
            {
                // No McpContext on ThreadLocal — tools that require container/user will fail gracefully
                LOG.debug("No McpContext available for tool context");
            }

            String response = chat.prompt()
                    .user(message)
                    .toolContext(toolContext)
                    .call()
                    .content();

            if (response != null && !response.isBlank())
            {
                return new MessageResponse("text/plain", response, null);
            }
            return new MessageResponse("text/plain", "No response generated.", null);
        }
        catch (Exception e)
        {
            LOG.error("Error sending message to LLM", e);
            return new MessageResponse("text/plain", "Error: " + e.getMessage(), null);
        }
    }

    /**
     * Sends a message with full conversation history so the LLM has prior context.
     * history should be the messages preceding the new prompt (user+assistant turns).
     */
    public MessageResponse sendMessage(ChatClient chat, List<Message> history, String newMessage)
    {
        try
        {
            Map<String, Object> toolContext = new HashMap<>();
            try
            {
                McpContext ctx = McpContext.get();
                toolContext.put("user", ctx.getUser());
                if (ctx.getContainer() != null)
                    toolContext.put("container", ctx.getContainer());
            }
            catch (IllegalStateException e)
            {
                LOG.debug("No McpContext available for tool context");
            }

            String response = chat.prompt()
                    .messages(history)
                    .user(newMessage)
                    .toolContext(toolContext)
                    .call()
                    .content();

            if (response != null && !response.isBlank())
                return new MessageResponse("text/plain", response, null);

            return new MessageResponse("text/plain", "No response generated.", null);
        }
        catch (Exception e)
        {
            LOG.error("Error sending message to LLM", e);
            return new MessageResponse("text/plain", "Error: " + e.getMessage(), null);
        }
    }

    @Override
    public VectorStore getVectorStore()
    {
        // Vector store requires an EmbeddingModel, which is a separate concern.
        // Return null for now; can be implemented when RAG features are needed.
        return null;
    }

    private ChatModel getChatModel()
    {
        if (_chatModel == null)
        {
            synchronized (this)
            {
                if (_chatModel == null && McpSettings.isConfigured())
                {
                    _chatModel = createChatModel();
                }
            }
        }
        return _chatModel;
    }

    /**
     * Builds a minimal ChatClient for one-shot title generation — no tools, no system prompt.
     */
    ChatClient buildTitleClient()
    {
        ChatModel chatModel = getChatModel();
        if (chatModel == null)
            throw new IllegalStateException("No LLM API key configured");

        return ChatClient.builder(chatModel)
                .defaultSystem("Generate a 2-5 word title for this conversation based on the user's first message. " +
                        "Respond with only the title — no quotes, no punctuation at the end.")
                .build();
    }

    /// Resets the cached ChatModel. Call when settings change.
    void resetChatModel()
    {
        synchronized (this)
        {
            _chatModel = null;
            _chatSessions.clear();
        }
    }

    private ChatModel createChatModel()
    {
        String apiKey = McpSettings.getApiKey();
        String model = McpSettings.getModelName();

        LOG.info("Creating AnthropicChatModel with model: " + model);

        var anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        return AnthropicChatModel.builder()
                .anthropicClient(anthropicClient)
                .options(AnthropicChatOptions.builder()
                        .model(Model.of(model))
                        .build())
                .build();
    }
}
