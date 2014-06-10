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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
      resources.add(ClientDependency.fromFilePath("biotrust/NWBioTrust.css"));
      return resources;
  }
%>
<%
    ActionURL baseTabURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
    ActionURL studyRegistrationsTabURL = baseTabURL.clone().addParameter("pageId", "nwbt.STUDY_REGISTRATIONS");
    ActionURL sampleRequestsTabURL = baseTabURL.clone().addParameter("pageId", "nwbt.SAMPLE_REQUESTS");

    String studyRegId = "studyreg-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String sampleRequestId = "samplerequest-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<table>
    <tr>
        <td valign="top"><div id=<%=q(studyRegId)%>><span style="font-style: italic;">Loading...</span></div></td>
        <td valign="top"><div id=<%=q(sampleRequestId)%>></div></td>
    </tr>
</table>

<script type="text/javascript">

    Ext4.onReady(function(){
        LABKEY.Query.executeSql({
            schemaName: "biotrust",
            sql: "SELECT " +
                "COUNT(RowId) AS Studies, " +
                "SUM(NumPending) AS PendingRequests, " +
                "SUM(NumSubmitted) AS SubmittedRequests " +
                "FROM StudyDashboardData",
            success: function(data){
                if (data.rows.length == 1)
                {
                    Ext4.get(<%=q(studyRegId)%>).update("");
                    Ext4.create('Ext.panel.Panel', {
                        renderTo: <%=q(studyRegId)%>,
                        frame: true,
                        width : 375,
                        style : 'margin: 7px;',
                        bodyStyle : 'padding: 3px;',
                        html : "<div class='request-title'>My Studies</div>"
                                + "<div class='request-description'>" + getDescriptionPrefix(data.rows[0]['Studies'] || 0, "study")
                                + " in your My Studies Tab.</div>"
                                + "<a href=<%=q(studyRegistrationsTabURL.getLocalURIString())%> class='request-title'>Click Here</a>"
                    });

                    var sampleRequestCount = data.rows[0]['PendingRequests'] + data.rows[0]['SubmittedRequests']
                    Ext4.get(<%=q(sampleRequestId)%>).update("");
                    Ext4.create('Ext.panel.Panel', {
                        renderTo: <%=q(sampleRequestId)%>,
                        frame: true,
                        width : 375,
                        style : 'margin: 7px;',
                        bodyStyle : 'padding: 3px;',
                        html : "<div class='request-title'>My Sample Requests</div>"
                            + "<div class='request-description'>" + getDescriptionPrefix(sampleRequestCount || 0, "sample request")
                            + " in your My Sample Requests Tab.</div>"
                            + "<a href=<%=q(sampleRequestsTabURL.getLocalURIString())%> class='request-title'>Click Here</a>"
                    });
                }
                else
                    onFailure();
            },
            failure: onFailure
        })
    });

    function getDescriptionPrefix(count, noun)
    {
        return count != 1 ? "There are " + count + " " + (noun=='study'?'studie':noun) + "s" : "There is " + count + " " + noun;
    }

    function onFailure()
    {
        Ext4.get(<%=q(studyRegId)%>).update("");
        Ext4.get(<%=q(sampleRequestId)%>).update("");
    }

</script>