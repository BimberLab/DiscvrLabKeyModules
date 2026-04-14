package org.labkey.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.labkey.api.mcp.McpService;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.mcp.McpToolUtils;

import java.io.IOException;
import java.util.List;

/**
 * Servlet wrapper that delegates to {@link HttpServletStreamableServerTransportProvider}
 * for MCP Streamable HTTP transport. Adds feature-flag gating on top.
 *
 * Registered at /_mcp/* via {@link McpModule#registerServlets}.
 */
public class McpTransportServlet extends HttpServlet
{
    private static final Logger LOG = LogHelper.getLogger(McpTransportServlet.class, "MCP Transport Servlet");

    private HttpServletStreamableServerTransportProvider _transportProvider;
    private McpSyncServer _mcpServer;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        // Lazily initialized on first request
    }

    private synchronized void ensureInitialized()
    {
        if (_mcpServer != null)
            return;

        McpServiceImpl service = (McpServiceImpl) McpService.get();

        _transportProvider = new HttpServletStreamableServerTransportProvider.Builder()
                .mcpEndpoint("/mcp")
                .build();

        // Initialize the transport provider as a servlet with our config
        try
        {
            _transportProvider.init(getServletConfig());
        }
        catch (ServletException e)
        {
            LOG.error("Failed to initialize MCP transport provider", e);
            return;
        }

        // Convert Spring AI ToolCallbacks to MCP tool specifications
        List<McpServerFeatures.SyncToolSpecification> toolSpecs =
                McpToolUtils.toSyncToolSpecification(List.of(service.getToolCallbacks()));

        var builder = McpServer.sync(_transportProvider)
                .serverInfo("LabKey MCP Server", "1.0.0")
                .instructions("LabKey Server MCP endpoint. Use setContainer to set the working folder before calling other tools.");

        if (!toolSpecs.isEmpty())
            builder.tools(toolSpecs);

        List<McpServerFeatures.SyncResourceSpecification> resources = service.getResources();
        if (!resources.isEmpty())
            builder.resources(resources);

        List<McpServerFeatures.SyncPromptSpecification> prompts = service.getPrompts();
        if (!prompts.isEmpty())
            builder.prompts(prompts);

        _mcpServer = builder.build();

        LOG.info("MCP Streamable HTTP server initialized with " + toolSpecs.size() + " tools, "
                + resources.size() + " resources, " + prompts.size() + " prompts");
    }

    private boolean checkEnabled(HttpServletResponse response) throws IOException
    {
        if (!OptionalFeatureService.get().isFeatureEnabled(McpService.ENABLE_MCP_SERVER_FLAG))
        {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "MCP server is not enabled. Enable the '" + McpService.ENABLE_MCP_SERVER_FLAG + "' feature flag.");
            return false;
        }
        return true;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        if (!checkEnabled(response))
            return;
        ensureInitialized();
        if (_transportProvider == null)
        {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "MCP server failed to initialize.");
            return;
        }
        // Delegate to the transport provider servlet which handles GET/POST/DELETE
        _transportProvider.service(request, response);
    }

    @Override
    public void destroy()
    {
        if (_mcpServer != null)
        {
            try
            {
                _mcpServer.close();
            }
            catch (Exception e)
            {
                LOG.error("Error closing MCP server", e);
            }
        }
        super.destroy();
    }
}
