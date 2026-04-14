<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.mcp.McpController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    McpController.McpSettingsForm form = (McpController.McpSettingsForm) HttpView.currentModel();
%>

<p>Configure the LLM provider settings for the MCP service. These settings are required for
the chat interface and are stored encrypted.</p>

<labkey:errors/>
<labkey:form action="<%=new ActionURL(McpController.SettingsAction.class, getContainer())%>" method="POST">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label"><label for="apiKey">Anthropic API Key</label></td>
            <td><input type="password" id="apiKey" name="apiKey" size="60" value="<%=h(form.getApiKey())%>" autocomplete="off" /></td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="modelName">Model Name</label></td>
            <td><input type="text" id="modelName" name="modelName" size="40" value="<%=h(form.getModelName())%>" /></td>
        </tr>
        <tr>
            <td></td>
            <td><small>Default: claude-sonnet-4-20250514</small></td>
        </tr>
        <tr>
            <td></td>
            <td><labkey:button text="Save" /></td>
        </tr>
    </table>
</labkey:form>
