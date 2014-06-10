<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
      resources.add(ClientDependency.fromFilePath("dataview/DataViewsPanel.css"));
      resources.add(ClientDependency.fromFilePath("biotrust/NWBioTrust.css"));
      resources.add(ClientDependency.fromFilePath("biotrust/DashboardPanel.js"));
      resources.add(ClientDependency.fromFilePath("biotrust/DocumentSetPanel.js"));
      resources.add(ClientDependency.fromFilePath("biotrust/CreateNewRequestPanel.js"));
      return resources;
  }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    String createRequestRenderId = "create-request-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String requestsRenderId = "requests-dashboard-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id=<%=q(createRequestRenderId)%>></div>
<br/>
<div class="dvc" id=<%=q(requestsRenderId)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        // add the dashboard create new request form panel
        <% if (getContainer().hasPermission(getUser(), UpdatePermission.class))
           {
        %>
            Ext4.create('Ext.Button', {
                renderTo: <%=q(createRequestRenderId)%>,
                text: 'Request Sample Collection',
                href: LABKEY.ActionURL.buildURL("biotrust", "updateSampleRequest"),
                hrefTarget: '_self',
                height: 30
            });
        <% } %>

        Ext4.create('LABKEY.ext4.biotrust.RequestorSRDashboardPanel', {
            frame: true,
            gridEmptyText: 'No sample requests to show',
            renderTo : <%=q(requestsRenderId)%>
        });

    });

</script>