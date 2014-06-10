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
package org.labkey.biotrust;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.survey.model.SurveyListener;
import org.labkey.api.survey.model.SurveyStatus;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.biotrust.audit.BioTrustAuditViewFactory;
import org.labkey.biotrust.email.BioTrustNotificationManager;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.RequestStatusTable;
import org.labkey.biotrust.query.study.StudyRegistrationDomain;
import org.labkey.biotrust.security.BudgetApproverRole;
import org.labkey.biotrust.security.EditRequestsPermission;
import org.labkey.biotrust.security.PrimaryStudyContactRole;
import org.labkey.biotrust.security.PrincipalInvestigatorRole;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 3/8/13
 */
public class BioTrustSurveyListener implements SurveyListener
{
    @Override
    public void surveyDeleted(Container c, User user, Survey survey) throws Exception
    {
        // clean up objects associated with the survey
        BioTrustSampleManager.get().surveyDeleted(c, user, survey);
        BioTrustManager.get().surveyDeleted(c, user, survey);
    }

    @Override
    public void surveyBeforeDelete(Container c, User user, Survey survey) throws Exception
    {
        // issue 180: don't allow PI to delete a registered study
        if (!c.hasPermission(user, EditRequestsPermission.class) && !"Pending".equals(survey.getStatus()))
            throw new UnauthorizedException("You do not have permissions to delete registered studies.");
    }

    @Override
    public void surveyCreated(Container c, User user, Survey survey, Map<String, Object> rowData) throws Exception
    {
        surveyUpdated(c, user, survey, Collections.EMPTY_MAP, rowData);
    }

    @Override
    public void surveyUpdated(Container c, User user, Survey survey, @Nullable Map<String, Object> oldRow, Map<String, Object> rowData) throws Exception
    {
        SurveyDesign design = SurveyService.get().getSurveyDesign(c, user, survey.getSurveyDesignId());

        if (design != null && rowData != null)
        {
            boolean isStudyRegistrationSurveyUpdate = design.getQueryName().equals(BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME);
            boolean isTissueRecordSurveyUpdate = design.getQueryName().equals(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

            // support survey submission of both study registrations and tissue records
            if (design.getSchemaName().equals(BioTrustQuerySchema.NAME) && (isStudyRegistrationSurveyUpdate || isTissueRecordSurveyUpdate))
            {
                String prevStatus = oldRow != null ? (String)oldRow.get("Status") : null;
                String newStatus = (String)rowData.get("Status");
                boolean statusChanged = !StringUtils.equals(prevStatus, newStatus);
                boolean isApprovalReviewStatus = BioTrustManager.get().isApprovalRequestStatus(newStatus);

                if (statusChanged)
                {
                    if (isStudyRegistrationSurveyUpdate && "Received".equals(newStatus))
                    {
                        BioTrustAuditViewFactory.addAuditEvent(c, user, survey.getRowId(), BioTrustAuditViewFactory.Actions.StudyRegistered,
                                newStatus, prevStatus, "The study : " + survey.getLabel() + ", was registered.");

                        BioTrustNotificationManager.get().sendStudyRegisteredEmail(c, user, survey);
                    }
                    else if (isTissueRecordSurveyUpdate)
                    {
                        if (SurveyStatus.Submitted.name().equals(newStatus))
                        {
                            BioTrustAuditViewFactory.addAuditEvent(c, user, 0, survey.getRowId(), BioTrustAuditViewFactory.Actions.SampleRequestSubmitted,
                                    newStatus, prevStatus, "The sample request : " + survey.getLabel() + ", was submitted.");

                            BioTrustNotificationManager.get().sendSurveySubmittedEmail(c, user, survey);
                        }
                        else if (isApprovalReviewStatus)
                        {
                            BioTrustAuditViewFactory.addAuditEvent(c, user, 0, survey.getRowId(), BioTrustAuditViewFactory.Actions.ApprovalReviewStatusChange,
                                    newStatus, prevStatus, "The sample request : " + survey.getLabel() + ", has been sent to " + newStatus + ".");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void surveyResponsesBeforeUpdate(Container c, User user, Survey survey) throws Exception
    {
        if (survey != null)
        {
            SurveyDesign surveyDesign = SurveyService.get().getSurveyDesign(c, user, survey.getSurveyDesignId());
            if (surveyDesign != null)
            {
                // don't allow inserts to updates to tissue records that are locked (i.e. submitted and status is Approved, Closed, etc.)
                if (BioTrustQuerySchema.NAME.equals(surveyDesign.getSchemaName()) && BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME.equals(surveyDesign.getQueryName())
                        && survey.getSubmitted() != null && RequestStatusTable.getLockedStates(c, user).indexOf(survey.getStatus()) > -1)
                {
                    throw new UnauthorizedException("Updates are not allowed for sample requests in a locked state.");
                }
            }
        }
    }

    @Override
    public void surveyResponsesUpdated(Container c, User user, Survey survey, Map<String, Object> rowData) throws Exception
    {
        SurveyDesign design = SurveyService.get().getSurveyDesign(c, user, survey.getSurveyDesignId());

        if (design != null && rowData != null)
        {
            // if the survey updated is the study registration, check for updates to contact information
            if (design.getSchemaName().equals(BioTrustQuerySchema.NAME) && design.getQueryName().equals(BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME))
            {
                Object piUserInSystem = rowData.get(StudyRegistrationDomain.PRINCIPAL_INVESTIGATOR_INSYSTEM_PROPERTY_NAME);
                Object piUserId = rowData.get(StudyRegistrationDomain.PRINCIPAL_INVESTIGATOR_PROPERTY_NAME);
                if (BooleanUtils.toBoolean(String.valueOf(piUserInSystem)) && piUserId != null)
                    updateContactRole(c, NumberUtils.toInt(String.valueOf(piUserId), -1), RoleManager.getRole(PrincipalInvestigatorRole.class));

                Object primaryUserInSystem = rowData.get(StudyRegistrationDomain.PRIMARY_STUDY_CONTACT_INSYSTEM_PROPERTY_NAME);
                Object primaryUserId = rowData.get(StudyRegistrationDomain.PRIMARY_STUDY_CONTACT_PROPERTY_NAME);
                if (BooleanUtils.toBoolean(String.valueOf(primaryUserInSystem)) && primaryUserId != null)
                    updateContactRole(c, NumberUtils.toInt(String.valueOf(primaryUserId), -1), RoleManager.getRole(PrimaryStudyContactRole.class));

                Object budgetUserInSystem = rowData.get(StudyRegistrationDomain.BUDGET_CONTACT_INSYSTEM_PROPERTY_NAME);
                Object budgetUserId = rowData.get(StudyRegistrationDomain.BUDGET_CONTACT_PROPERTY_NAME);
                if (BooleanUtils.toBoolean(String.valueOf(budgetUserInSystem)) && budgetUserId != null)
                    updateContactRole(c, NumberUtils.toInt(String.valueOf(budgetUserId), -1), RoleManager.getRole(BudgetApproverRole.class));
            }
        }
    }

    @Override
    public List<String> getSurveyLockedStates()
    {
        return Collections.emptyList();
    }

    private void updateContactRole(Container c, int userId, Role role)
    {
        if (userId != -1)
        {
            User user = UserManager.getUser(userId);
            if (user != null)
                BioTrustContactsManager.get().addRoleAssignment(c, user, role);
        }
    }
}
