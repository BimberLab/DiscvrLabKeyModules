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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.security.EditRequestsPermission" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromPath("Ext4ClientApi"));
      resources.add(ClientDependency.fromPath("dataview/DataViewsPanel.css"));
      resources.add(ClientDependency.fromPath("biotrust/NWBioTrust.css"));
      resources.add(ClientDependency.fromPath("biotrust/DashboardPanel.js"));
      resources.add(ClientDependency.fromPath("biotrust/DocumentSetPanel.js"));
      resources.add(ClientDependency.fromPath("biotrust/CreateNewRequestPanel.js"));
      return resources;
  }
%>
<%
    Container c = getContainer();
    User user = getUser();

    String createRegRenderId = "create-registration-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String registrationRenderId = "registration-dashboard-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id=<%=q(createRegRenderId)%>></div>
<br/>
<div class="dvc" id=<%=q(registrationRenderId)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        <% if (c.hasPermission(user, UpdatePermission.class))
           {
        %>
            Ext4.create('Ext.Button', {
                renderTo: <%=q(createRegRenderId)%>,
                text: 'Create New Study Registration',
                href: LABKEY.ActionURL.buildURL("biotrust", "updateStudyRegistration"),
                hrefTarget: '_self',
                height: 30
            });
        <% } %>

        Ext4.create('LABKEY.ext4.biotrust.RequestorStudyRegDashboardPanel', {
            frame: true,
            renderTo : <%=q(registrationRenderId)%>,
            isRcRole : <%=c.hasPermission(user, EditRequestsPermission.class)%>
        });

    });

</script>