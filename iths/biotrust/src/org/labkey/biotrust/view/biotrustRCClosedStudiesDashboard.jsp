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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.security.UpdateWorkflowPermission" %>
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
      return resources;
  }
%>
<%
    String closedRenderId = "closed-dashboard-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    boolean canUpdate = getContainer().hasPermission(getUser(), UpdateWorkflowPermission.class);
    boolean isAdmin = getContainer().hasPermission(getUser(), AdminPermission.class);
%>

<div class="dvc" id=<%=q(closedRenderId)%>></div>

<script type="text/javascript">

    Ext4.onReady(function(){

        // check to see if this user is in the RC Role
        this.isRcRole = false;
        LABKEY.Security.getUserPermissions({
            success: function(data){
                var isRcRole = data.container.roles.indexOf("org.labkey.biotrust.security.BioTrustRCRole") > -1;

                Ext4.create('LABKEY.ext4.biotrust.ClosedStudiesDashboardPanel', {
                    frame: true,
                    canUpdate   : <%=canUpdate%>,
                    isRcRole: isRcRole,
                    isAdmin     : <%=isAdmin%>,
                    collapseGroups: false,
                    renderTo : <%=q(closedRenderId)%>
                });

            },
            scope: this
        });

    });

</script>