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
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.security.roles.Role" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.BioTrustContactsManager" %>
<%@ page import="org.labkey.biotrust.security.CreateContactsPermission" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
      resources.add(ClientDependency.fromFilePath("biotrust/NWBioTrust.css"));
      resources.add(ClientDependency.fromFilePath("biotrust/ContactsPanel.js"));
      return resources;
  }
%>
<%
    Container c = getContainer();

    ActionURL contactsTabURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c);
    contactsTabURL.addParameter("pageId", "nwbt.CONTACTS");

    String renderId = "biotrust-managecontacts-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    boolean admin = c.hasPermission(getUser(), UpdatePermission.class);
    boolean createContact = c.hasPermission(getUser(), CreateContactsPermission.class);

    ObjectMapper jsonMapper = new ObjectMapper();
    List<Map<String, String>> btRoles = new ArrayList<>();

    for (Role role : BioTrustContactsManager.get().getBioTrustRoles(false))
    {
        Map<String, String> roleInfo = new HashMap<>();

        roleInfo.put("name", role.getName());
        roleInfo.put("uniqueName", role.getUniqueName());

        btRoles.add(roleInfo);
    }
%>

<script type="text/javascript">

    Ext4.onReady(function(){

        Ext4.create('LABKEY.ext4.biotrust.ContactsPanel', {
            renderTo    : <%=q(renderId)%>,
            returnUrl   : <%=q(getActionURL().getLocalURIString())%>,
            frame       : true,
            admin       : <%=admin%>,
            userId      : <%=getUser().getUserId()%>,
            roles       : <%=text(jsonMapper.writeValueAsString(btRoles))%>,
            createContact : <%=createContact%>
        });
    });

</script>

<div class="dvc" id=<%=q(renderId)%>></div>
