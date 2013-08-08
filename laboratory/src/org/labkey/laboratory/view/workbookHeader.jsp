<%
    /*
    * Copyright (c) 2010-2012 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.laboratory.query.WorkbookModel" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("laboratory.context"));
        resources.add(ClientDependency.fromFilePath("laboratory/panel/WorkbookHeaderPanel.js"));
        resources.add(ClientDependency.fromFilePath("editInPlaceElement.css"));
        resources.add(ClientDependency.fromFilePath("editInPlaceElement.js"));
        return resources;
    }
%>
<%
    JspView me = (JspView) HttpView.currentView();
    WorkbookModel model = (WorkbookModel)me.getModelBean();
    Integer workbookId = model.getWorkbookId();
    String wpId = "wp_" + me.getWebPartRowId();
%>

<style type="text/css">
    .wb-name
    {
        color: #999999;
    }
</style>

<div id=<%=q(wpId)%>></div>

<script type="text/javascript">
    Ext4.onReady(function(){
        var webpartId = <%=q(wpId)%>;
        var workbookId = <%=h(workbookId)%>;
        var title = <%=q(getViewContext().getContainer().getTitle())%> || '';

        Ext4.create('Laboratory.panel.WebpartHeaderPanel', {
            description: <%=q(getViewContext().getContainer().getDescription())%>,
            materials: <%=q(model.getMaterials())%>,
            methods: <%=q(model.getMethods())%>,
            results: <%=q(model.getResults())%>,
            tags: <%=text(model.getTags() == null || model.getTags().length == 0 ? "null" : "['" + text(StringUtils.join(Arrays.asList(model.getTags()), "','")) + "']")%>
        }).render(webpartId);

        //set page title
        var titleId = Ext4.id();
        LABKEY.NavTrail.setTrail("<span class='wb-name'>" + workbookId + ":&nbsp;</span><span class='labkey-edit-in-place' id='" + titleId + "'>" + title + "</span>", undefined, title);

        new LABKEY.ext.EditInPlaceElement({
            applyTo: titleId,
            updateConfig: {
                url: LABKEY.ActionURL.buildURL("core", "updateTitle"),
                jsonDataPropName: 'title'
            },
            listeners: {
                beforecomplete: function(newText){
                    return (newText.length > 0);
                }
            }
        });
    });
</script>
