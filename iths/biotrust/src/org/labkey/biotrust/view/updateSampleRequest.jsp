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
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.survey.SurveyService" %>
<%@ page import="org.labkey.api.survey.model.SurveyDesign" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.BioTrustController" %>
<%@ page import="org.labkey.biotrust.BioTrustSampleManager" %>
<%@ page import="org.labkey.biotrust.model.SampleRequest" %>
<%@ page import="org.labkey.biotrust.security.EditRequestsPermission" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4ClientApi"));
        resources.add(ClientDependency.fromPath("/survey/Survey.css"));
        resources.add(ClientDependency.fromPath("/survey/BaseSurveyPanel.js"));
        resources.add(ClientDependency.fromPath("/survey/UsersCombo.js"));
        resources.add(ClientDependency.fromPath("/biotrust/SampleRequestWizard.js"));
        resources.add(ClientDependency.fromPath("/biotrust/AssociatedStudyQuestion.js"));
        resources.add(ClientDependency.fromPath("/biotrust/ContactCombo.js"));
        resources.add(ClientDependency.fromPath("/biotrust/ContactsPanel.js"));
        resources.add(ClientDependency.fromPath("/biotrust/DocumentUploadQuestion.js"));
        resources.add(ClientDependency.fromPath("/biotrust/TissueRecordPanel.js"));
        return resources;
    }
%>
<%
    JspView<BioTrustController.RequestForm> me = (JspView<BioTrustController.RequestForm>) HttpView.currentView();
    BioTrustController.RequestForm bean = me.getModelBean();
    Container c = getContainer();
    User user = getUser();

    Integer rowId = 0;
    Integer studyId = 0;
    if (bean != null)
    {
        rowId = bean.getRowId();
        studyId = bean.getStudyId();
    }

    boolean submitted = false;
    boolean locked = false;
    if (rowId > 0)
    {
        SampleRequest sampleRequest = BioTrustSampleManager.get().getSampleRequest(c, user, rowId);
        submitted = BioTrustSampleManager.get().isSampleRequestSubmitted(c, user, sampleRequest);
        locked = BioTrustSampleManager.get().isSampleRequestLocked(c,  user, sampleRequest);
    }

    // we allow editing for 1) non-submitted requests 2) submitted (but not complete) requests if the user has EditRequestsPermission
    // CBR issue 191: we also want to allow editing of special properties for locked requests (i.e. protocol docs, sample pickup info, etc.)
    boolean canEditRequests = c.hasPermission(user, AdminPermission.class) || c.hasPermission(user, EditRequestsPermission.class);
    boolean canEditSubmitted = !locked && canEditRequests;
    boolean canEdit = (!submitted && c.hasPermission(user, UpdatePermission.class)) || canEditSubmitted;

    StringBuilder alwaysEditablePropertyNames = new StringBuilder();
    String sep = "";
    for (String name : BioTrustSampleManager.get().getAlwaysEditablePropertyNames())
    {
        sep = ",";
        alwaysEditablePropertyNames.append("'").append(name.toLowerCase()).append("'").append(sep);
    }

    // for the NWBT setup, we expect the survey designs to exist at the project level
    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c.getParent());
    filter.addCondition(FieldKey.fromParts("Label"), "SampleRequest");
    SurveyDesign[] designs = SurveyService.get().getSurveyDesigns(filter);

    filter = new SimpleFilter(FieldKey.fromParts("Container"), c.getParent());
    filter.addCondition(FieldKey.fromParts("Label"), "StudyRegistration");
    SurveyDesign[] studyDesigns = SurveyService.get().getSurveyDesigns(filter);

    String formRenderId = "request-form-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<%
    if (getErrors("form").hasErrors())
    {
        %><%=formatMissedErrors("form")%><%
    }
    else
    {
%>
<div id=<%=q(formRenderId)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        var panel = Ext4.create('LABKEY.ext4.biotrust.SampleRequestWizard', {
            cls             : 'lk-survey-panel themed-panel',
            rowId           : <%=rowId%>,
            isSubmitted     : <%=submitted%>,
            canEdit         : <%=canEdit%>,
            canEditSubmitted : <%=canEditSubmitted%>,
            canEditRequests : <%=canEditRequests%>,
            alwaysEditablePropertyNames : [<%=alwaysEditablePropertyNames%>],
            renderTo        : <%=q(formRenderId)%>,
            studyId         : <%=studyId%>,
            surveyDesignId  : <%=designs.length > 0 ? designs[0].getRowId() : 0 %>,
            studySurveyDesignId : <%=studyDesigns.length > 0 ? studyDesigns[0].getRowId() : 0 %>,
            autosaveInterval: 60000
        });

    });

</script>
<%
    }
%>