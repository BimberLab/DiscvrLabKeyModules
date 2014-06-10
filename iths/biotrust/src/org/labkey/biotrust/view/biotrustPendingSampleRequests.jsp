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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>


<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    String renderId = "rc-pending-samplerequests-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<div id=<%=q(renderId)%>></div>
<script type="text/javascript">
    var qwp = new LABKEY.QueryWebPart({
    	renderTo: <%=q(renderId)%>,
        frame: 'none',
    	schemaName: 'biotrust',
    	queryName: 'PendingSampleRequestOverview',
        aggregates: [{column: 'NumSampleRequests', type: LABKEY.AggregateTypes.SUM, label: 'Total'}],
        containerFilter: 'CurrentAndSubfolders',
    	buttonBarPosition: 'none',
        sort: 'Category,Status'
    });
</script>