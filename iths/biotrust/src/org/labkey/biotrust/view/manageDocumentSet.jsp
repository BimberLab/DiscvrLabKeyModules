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
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.BioTrustController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("Ext4ClientAPI"));
      resources.add(ClientDependency.fromFilePath("biotrust/DocumentSetPanel.js"));
      return resources;
  }
%>
<%
    JspView<BioTrustController.RequestForm> me = (JspView) HttpView.currentView();
    BioTrustController.RequestForm bean = me.getModelBean();

    String returnURL = bean.getSrcURL() != null ? bean.getSrcURL().toString() : null;
    String renderId = "document-set-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id=<%=q(renderId)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        var panel = Ext4.create('LABKEY.ext4.biotrust.DocumentSetPanel', {
            displayMode: false,
            requestRowId: <%=bean.getRowId()%>,
            requestContainerPath: <%=q(getContainer().getPath())%>,
            renderTo : <%=q(renderId)%>,
            returnURL : <%=q(returnURL)%>
        });

    });

</script>