package org.labkey.mcp;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.mcp.AbstractAgentAction;
import org.labkey.api.mcp.McpService;
import org.labkey.api.mcp.PromptForm;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class McpController extends SpringActionController
{
    public static final String NAME = "mcp";

    private static final ActionResolver _resolver = new DefaultActionResolver(McpController.class);

    public McpController()
    {
        setActionResolver(_resolver);
    }

    @RequiresLogin
    public static class ChatAgentAction extends AbstractAgentAction<PromptForm>
    {
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
    }

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
