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
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.survey.SurveyService" %>
<%@ page import="org.labkey.api.survey.model.Survey" %>
<%@ page import="org.labkey.api.survey.model.SurveyDesign" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.biotrust.BioTrustController" %>
<%@ page import="org.labkey.biotrust.query.BioTrustQuerySchema" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
        resources.add(ClientDependency.fromFilePath("/survey/Survey.css"));
        resources.add(ClientDependency.fromFilePath("/survey/BaseSurveyPanel.js"));
        resources.add(ClientDependency.fromFilePath("/survey/UsersCombo.js"));
        resources.add(ClientDependency.fromFilePath("/biotrust/StudyRegistrationWizard.js"));
        resources.add(ClientDependency.fromFilePath("/biotrust/ContactCombo.js"));
        resources.add(ClientDependency.fromFilePath("/biotrust/ContactsPanel.js"));
        resources.add(ClientDependency.fromFilePath("/biotrust/DocumentUploadQuestion.js"));
        return resources;
    }
%>
<%
    JspView<BioTrustController.RequestForm> me = (JspView<BioTrustController.RequestForm>) HttpView.currentView();
    BioTrustController.RequestForm bean = me.getModelBean();
    Container c = getContainer();
    User user = getUser();

    Integer rowId = 0;
    if (bean != null)
    {
        rowId = bean.getRowId();
    }

    Integer surveyRowId = 0;
    boolean isRegistered = false;
    if (rowId > 0)
    {
        Survey studyRegistration = SurveyService.get().getSurvey(c, user, BioTrustQuerySchema.NAME, BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME, rowId.toString());
        surveyRowId = studyRegistration.getRowId();
        isRegistered = !("Pending".equals(studyRegistration.getStatus()));
    }

    boolean canEdit = c.hasPermission(user, UpdatePermission.class);

    // for the NWBT setup, we expect the survey designs to exist at the project level
    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c.getParent());
    filter.addCondition(FieldKey.fromParts("Label"), "StudyRegistration");
    SurveyDesign[] studyDesigns = SurveyService.get().getSurveyDesigns(filter);

    String formRenderId = "registration-form-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
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

        var panel = Ext4.create('LABKEY.ext4.biotrust.StudyRegistrationWizard', {
            cls             : 'lk-survey-panel themed-panel',
            rowId           : <%=rowId%>,
            surveyRowId     : <%=surveyRowId%>,
            canEdit         : <%=canEdit%>,
            renderTo        : <%=q(formRenderId)%>,
            surveyDesignId  : <%=studyDesigns.length > 0 ? studyDesigns[0].getRowId() : 0 %>,
            isRegistered    : <%=isRegistered%>,
            autosaveInterval: 60000
        });

    });

</script>
<%
    }
%>