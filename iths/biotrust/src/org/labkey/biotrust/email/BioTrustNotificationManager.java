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
package org.labkey.biotrust.email;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.biotrust.BioTrustContactsManager;
import org.labkey.biotrust.BioTrustController;
import org.labkey.biotrust.BioTrustModule;
import org.labkey.biotrust.BioTrustSampleManager;
import org.labkey.biotrust.audit.BioTrustAuditViewFactory;
import org.labkey.biotrust.model.TissueRecord;
import org.labkey.biotrust.security.BioTrustRCRole;
import org.labkey.biotrust.security.PrincipalInvestigatorRole;

import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 3/11/13
 */
public class BioTrustNotificationManager
{
    private static final Logger _log = Logger.getLogger(BioTrustNotificationManager.class);
    private static final BioTrustNotificationManager _instance = new BioTrustNotificationManager();
    public static final String NWBT_EMAIL_NOTIFICATIONS = "nwbt_notifications";

    public enum NotificationType {

        studyRegistered,
        sampleRequestSubmitted,
        approverReviewRequested,
        approverReviewSubmitted,
        investigatorFolderCreated
    }

    private BioTrustNotificationManager()
    {
    }

    public static BioTrustNotificationManager get()
    {
        return _instance;
    }

    public void sendStudyRegisteredEmail(Container c, User user, Survey survey)
    {
        if (isNotificationEnabled(c, NotificationType.studyRegistered))
        {
            StudyRegisteredEmailTemplate template = EmailTemplateService.get().getEmailTemplate(StudyRegisteredEmailTemplate.class);

            ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c.getProject(), "nwbt.OPEN_STUDIES_AND_REQUESTS");

            template.setSubmittedBy(user);
            template.setSampleRequestLink(url);

            sendRCNotificationEmail(c, user, survey, true, template);
        }
    }

    public void sendSurveySubmittedEmail(Container c, User user, Survey survey)
    {
        if (isNotificationEnabled(c, NotificationType.sampleRequestSubmitted))
        {
            SampleRequestSubmittedEmailTemplate template = EmailTemplateService.get().getEmailTemplate(SampleRequestSubmittedEmailTemplate.class);

            ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c.getProject(), "nwbt.OPEN_STUDIES_AND_REQUESTS");

            template.setSubmittedBy(user);
            template.setSampleRequestLink(url);

            sendRCNotificationEmail(c, user, survey, true, template);
        }
    }

    /**
     * Sends the notification back to the RC, whenever a sample request reviewer has updated a review request.
     */
    public void sendSampleReviewUpdatedEmail(Container c, User user, int surveyId, TissueRecord tissueRecord, BioTrustAuditViewFactory.Actions action, String comment)
    {
        if (isNotificationEnabled(c, NotificationType.approverReviewSubmitted))
        {
            SampleReviewerResponseEmailTemplate template = EmailTemplateService.get().getEmailTemplate(SampleReviewerResponseEmailTemplate.class);

            ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c.getProject(), "nwbt.OPEN_STUDIES_AND_REQUESTS");
            Survey survey = SurveyService.get().getSurvey(c, user, surveyId);

            template.setSubmittedBy(user);
            template.setSampleLink(url);
            template.setSampleType(tissueRecord.getRequestType());
            template.setReviewStatus(action.getLabel());
            template.setReviewComments(comment);

            sendRCNotificationEmail(c, user, survey, false, template);
        }
    }

    /**
     * Sent when an investigator has successfully created a new folder
     */
    public void sendInvestigatorAccountCreatedEmail(Container c, User user, User investigator)
    {
        if (isNotificationEnabled(c, NotificationType.investigatorFolderCreated))
        {
            InvestigatorFolderCreatedEmailTemplate template = EmailTemplateService.get().getEmailTemplate(InvestigatorFolderCreatedEmailTemplate.class);

            template.setSubmittedBy(user);
            template.setInvestigatorEmail(investigator.getEmail());
            template.setFolderName(c.getPath());

            LookAndFeelProperties lafProps = LookAndFeelProperties.getInstance(c);

            List<ValidEmail> rcEmails = getRCEmailAddresses(c.getProject(), user);
            if (!rcEmails.isEmpty())
            {
                String[] emailTo = new String[rcEmails.size()];
                int i=0;

                for (ValidEmail email : rcEmails)
                    emailTo[i++] = email.getEmailAddress();

                EmailMessage message = EmailService.get().createMessage(lafProps.getSystemEmailAddress(),
                        emailTo,
                        template.renderSubject(c),
                        template.renderBody(c));

                try
                {
                    EmailService.get().sendMessage(message, user, c);
                }
                catch(MessagingException | ConfigurationException e)
                {
                    _log.warn("Could not send email notifications.", e);
                }
            }
        }
    }

    /**
     * Returns an optionally configured alternate email address for the passed in principal
     */
    @Nullable
    public ValidEmail getAlternateEmailAddress(Container c, User user, User principal)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, "core");

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("userId"), principal.getUserId());
        TableSelector selector = new TableSelector(schema.getTable("Users"), Collections.singleton("alternateEmail"), filter, null);

        String email = selector.getObject(String.class);

        try {

            if (email != null)
                return new ValidEmail(email);
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            _log.error(e.getMessage());
        }
        return null;
    }

    /**
     * Returns the list of users with the PI role in the specified survey folder
     * @param surveyContainerId
     * @return
     */
    private List<String> getPIUsers(Container c, User user, String surveyContainerId)
    {
        // get the list of users in the PI role for the submitted record
        Container surveyContainer = ContainerManager.getForId(surveyContainerId);
        Role piRole = RoleManager.getRole(PrincipalInvestigatorRole.class);

        Set<User> piUsers = BioTrustContactsManager.get().getUsersWithRole(surveyContainer, piRole, false);
        List<String> emailCC = new ArrayList<>();

        for (User pi : piUsers)
        {
            emailCC.add(pi.getEmail());
            ValidEmail alternateEmail = getAlternateEmailAddress(c, user, pi);
            if (alternateEmail != null)
                emailCC.add(alternateEmail.getEmailAddress());
        }
        return emailCC;
    }

    /**
     * Sends an email to the RC email address.
     * @param notifyInvestigator if true then the PI will be cc'ed on the email
     * @param template the email template to use to render the message
     */
    private void sendRCNotificationEmail(Container c, User user, Survey survey, boolean notifyInvestigator, EmailTemplate template)
    {
        LookAndFeelProperties lafProps = LookAndFeelProperties.getInstance(c);

        List<ValidEmail> rcEmails = getRCEmailAddresses(c, user);
        if (!rcEmails.isEmpty())
        {
            String[] emailTo = new String[rcEmails.size()];
            int i=0;

            for (ValidEmail email : rcEmails)
                emailTo[i++] = email.getEmailAddress();

            // get the list of users in the PI role for the submitted record
            List<String> emailCC = Collections.emptyList();
            if (notifyInvestigator)
                emailCC = getPIUsers(c, user, survey.getContainerId());

            EmailMessage message = EmailService.get().createMessage(lafProps.getSystemEmailAddress(),
                    emailTo,
                    emailCC.toArray(new String[emailCC.size()]),
                    template.renderSubject(c),
                    template.renderBody(c));

            try
            {
                EmailService.get().sendMessage(message, user, c);
            }
            catch(MessagingException | ConfigurationException e)
            {
                _log.warn("Could not send email notifications.", e);
            }
        }
    }

    private static final boolean EMAIL_RC_GROUP = true;

    private List<ValidEmail> getRCEmailAddresses(Container c, User user)
    {
        List<ValidEmail> emails = new ArrayList<>();

        try {
            if (EMAIL_RC_GROUP)
            {
                Map<String, ModuleProperty> props = ModuleLoader.getInstance().getModule(BioTrustModule.class).getModuleProperties();
                ModuleProperty prop = props.get("RC email address");
                if (prop != null)
                {
                    String email = prop.getEffectiveValue(c);
                    if (email != null)
                    {
                        ValidEmail validEmail = new ValidEmail(email);
                        emails.add(validEmail);
                    }
                }
            }
            else
            {
                // get the list of users in the RC role
                Role rcRole = RoleManager.getRole(BioTrustRCRole.class);
                if (rcRole != null)
                {
                    for (User rcUser : BioTrustContactsManager.get().getUsersWithRole(c, rcRole, true))
                    {
                        emails.add(new ValidEmail(rcUser.getEmail()));

                        ValidEmail alternateEmail = getAlternateEmailAddress(c, user, rcUser);
                        if (alternateEmail != null)
                            emails.add(alternateEmail);
                    }
                }
            }
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            _log.warn(e.getMessage());
        }
        return emails;
    }

    public void sendApprovalReviewEmail(Container c, User user, Survey survey, String status, String reviewComment, boolean notifyInvestigator)
    {
        if (isNotificationEnabled(c, NotificationType.approverReviewRequested))
        {
            Container tissueRecordContainer = ContainerManager.getForId(survey.getContainerId());
            TissueRecord tissueRecord = BioTrustSampleManager.get().getTissueRecord(tissueRecordContainer, user, Integer.parseInt(survey.getResponsesPk()));
            if (tissueRecord != null)
            {
                TissueRecord.Types recordType = getTissueRecordType(tissueRecord);
                ActionURL url = new ActionURL(BioTrustController.ApproverReviewAction.class, c.getProject()).addParameter("status", status);

                // get the set of selected reviewers from the junction table
                Set<User> users = BioTrustSampleManager.get().getSampleReviewers(tissueRecordContainer, survey.getRowId(), status);
                url.addParameter("requestType", recordType.name());
                sendReviewEmail(c, user, survey, users, notifyInvestigator, url, recordType, status, reviewComment, ApprovalReviewEmailTemplate.class);
            }
        }
    }

    private TissueRecord.Types getTissueRecordType(TissueRecord record)
    {
        String requestType = record.getRequestType();
        return TissueRecord.Types.valueOf(requestType);
    }

    private void sendReviewEmail(Container c, User user, Survey survey, Set<User> recipients, boolean notifyInvestigator,
                                 ActionURL url, TissueRecord.Types recordType, String status, String comment, Class<? extends ReviewEmailTemplate> templateCls)
    {
        ReviewEmailTemplate template = EmailTemplateService.get().getEmailTemplate(templateCls);
        LookAndFeelProperties lafProps = LookAndFeelProperties.getInstance(c);

        if (!recipients.isEmpty())
        {
            List<String> emailTo = new ArrayList<>();
            int i=0;

            for (User rc : recipients)
            {
                emailTo.add(rc.getEmail());
                ValidEmail alternateEmail = getAlternateEmailAddress(c, user, rc);
                if (alternateEmail != null)
                    emailTo.add(alternateEmail.getEmailAddress());
            }

            // get the list of users in the PI role for the submitted record
            List<String> emailCC = Collections.emptyList();
            if (notifyInvestigator)
                emailCC = getPIUsers(c, user, survey.getContainerId());

            template.setSubmittedBy(user);
            template.setSampleLink(url);
            template.setSampleType(recordType.getLabel());
            template.setStatus(status.toLowerCase());
            template.setRcComments(comment);

            EmailMessage message = EmailService.get().createMessage(lafProps.getSystemEmailAddress(),
                    emailTo.toArray(new String[emailTo.size()]),
                    emailCC.toArray(new String[emailCC.size()]),
                    template.renderSubject(c),
                    template.renderBody(c));

            try
            {
                EmailService.get().sendMessage(message, user, c);
            }
            catch(MessagingException | ConfigurationException e)
            {
                _log.warn("Could not send email notifications.", e);
            }
        }
    }

    public boolean isNotificationEnabled(Container c, NotificationType type)
    {
        Map<String, String> props = PropertyManager.getProperties(c.getProject(), NWBT_EMAIL_NOTIFICATIONS);

        return props.get(type.name()) == null;
    }
}
