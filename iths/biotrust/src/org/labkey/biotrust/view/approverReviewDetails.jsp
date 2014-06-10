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
      resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
      resources.add(ClientDependency.fromFilePath("dataview/DataViewsPanel.css"));
      resources.add(ClientDependency.fromFilePath("biotrust/NWBioTrust.css"));
      resources.add(ClientDependency.fromFilePath("biotrust/DashboardPanel.js"));
      resources.add(ClientDependency.fromFilePath("biotrust/DocumentSetPanel.js"));
      return resources;
  }
%>
<%
    JspView<BioTrustController.RequestForm> me = (JspView) HttpView.currentView();
    BioTrustController.RequestForm bean = me.getModelBean();

    String reviewRenderId = "review-details-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id=<%=q(reviewRenderId)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        // check to see if this user is in the FacultyRole so we know if we should give them the UI for adding a review response
        LABKEY.Security.getUserPermissions({
            success: function(data){
                var isFacultyRole = data.container.roles.indexOf("org.labkey.biotrust.security.FacultyRole") > -1;
                var isRcRole = data.container.roles.indexOf("org.labkey.biotrust.security.BioTrustRCRole") > -1;

                LABKEY.Query.selectRows({
                    schemaName: 'biotrust',
                    queryName: 'StudySampleRequests',
                    filterArray: [LABKEY.Filter.create('RowId', <%=bean.getRowId()%>)],
                    containerFilter: 'CurrentAndSubfolders',
                    success: function(data) {
                        if (data.rows.length == 1)
                        {
                            var row = data.rows[0];

                            Ext4.create('LABKEY.ext4.biotrust.ApproverReviewPanel', {
                                renderTo : <%=q(reviewRenderId)%>,
                                status : row["Status"],
                                isApproval : row["IsApproval"],
                                submitted: row["Submitted"],
                                recordId: row["RecordId"],
                                tissueId: row["RowId"],
                                surveyId: row["SurveyRowId"],
                                sampleId: row["SampleId"],
                                studyId: row["StudyId"],
                                studySurveyDesignId: row["StudySurveyDesignId"],
                                requestType: row["RequestTypeDisplay"] || row["RequestType"],
                                containerId: row["Container"],
                                reviewerSelected : row["UserReviewResponseExpected"],
                                isFacultyRole: isFacultyRole,
                                isRcRole: isRcRole
                            });
                        }
                    }
                });

            }
        });

    });

</script>