package org.labkey.mcp;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.mcp.McpService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.OptionalFeatureFlag;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.settings.OptionalFeatureService.FeatureType;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class McpModule extends DefaultModule
{
    public static final String NAME = "MCP";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return null;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController(McpController.NAME, McpController.class);

        // Install the real McpService implementation before any other module's doStartup() registers tools
        McpService.setInstance(new McpServiceImpl());
    }

    @Override
    public void registerServlets(ServletContext servletCtx)
    {
        ServletRegistration.Dynamic servlet = servletCtx.addServlet("mcp-transport", new McpTransportServlet());
        servlet.addMapping("/_mcp/*");
        servlet.setAsyncSupported(true);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        AdminConsole.addLink(
                AdminConsole.SettingsLinkType.Configuration,
                "MCP Settings",
                DetailsURL.fromString("mcp/settings.view", ContainerManager.getRoot()).getActionURL()
        );

        OptionalFeatureService.get().addFeatureFlag(new OptionalFeatureFlag(
                McpService.ENABLE_MCP_SERVER_FLAG,
                "Enable MCP Server",
                "Enables the Model Context Protocol (MCP) Streamable HTTP endpoint at /_mcp for external clients like Claude Desktop.",
                false,
                false,
                FeatureType.Experimental
        ));
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }
}
