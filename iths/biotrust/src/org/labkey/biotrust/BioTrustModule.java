/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.biotrust.audit.BioTrustAuditProvider;
import org.labkey.biotrust.audit.BioTrustAuditViewFactory;
import org.labkey.biotrust.email.ApprovalReviewEmailTemplate;
import org.labkey.biotrust.email.InvestigatorFolderCreatedEmailTemplate;
import org.labkey.biotrust.email.SampleRequestSubmittedEmailTemplate;
import org.labkey.biotrust.email.SampleReviewerResponseEmailTemplate;
import org.labkey.biotrust.email.StudyRegisteredEmailTemplate;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.RequestResponsesDomain;
import org.labkey.biotrust.query.contacts.ContactDomain;
import org.labkey.biotrust.query.samples.ParticipantEligibilityDomain;
import org.labkey.biotrust.query.samples.SampleRequestDomain;
import org.labkey.biotrust.query.samples.TissueRecordDomain;
import org.labkey.biotrust.query.study.StudyRegistrationDomain;
import org.labkey.biotrust.security.BillingContactRole;
import org.labkey.biotrust.security.BioTrustRCRole;
import org.labkey.biotrust.security.BioTrustSecurityManager;
import org.labkey.biotrust.security.BudgetApproverRole;
import org.labkey.biotrust.security.FacultyRole;
import org.labkey.biotrust.security.PrimaryStudyContactRole;
import org.labkey.biotrust.security.PrincipalInvestigatorRole;
import org.labkey.biotrust.security.SamplePickupRole;
import org.labkey.biotrust.security.StudyContactRole;
import org.labkey.biotrust.view.BioTrustAdminWebPart;
import org.labkey.biotrust.view.BioTrustApproverReviewDashboardWebPart;
import org.labkey.biotrust.view.BioTrustClosedStudiesDashboardWebPart;
import org.labkey.biotrust.view.BioTrustIRBInformationWebPart;
import org.labkey.biotrust.view.BioTrustPendingSampleRequestsWebPart;
import org.labkey.biotrust.view.BioTrustRequestorContactsWebpart;
import org.labkey.biotrust.view.BioTrustRequestorManageContactsWebPart;
import org.labkey.biotrust.view.BioTrustRequestorOverviewWebPart;
import org.labkey.biotrust.view.BioTrustRequestorSampleRequestDashboardWebPart;
import org.labkey.biotrust.view.BioTrustRequestorStudyRegDashboardWebPart;
import org.labkey.biotrust.view.BioTrustResearchCoordDashboardWebPart;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BioTrustModule extends DefaultModule
{
    public static final WebPartFactory _adminFactory = new BioTrustAdminWebPart();
    public static final WebPartFactory _rcDashboardFactory = new BioTrustResearchCoordDashboardWebPart();
    public static final WebPartFactory _rcPendingSampleRequestsFactory = new BioTrustPendingSampleRequestsWebPart();
    public static final WebPartFactory _rcIRBInformationFactory = new BioTrustIRBInformationWebPart();
    public static final WebPartFactory _approverDashboardFactory = new BioTrustApproverReviewDashboardWebPart();
    public static final WebPartFactory _rcClosedStudiesDashboardFactory = new BioTrustClosedStudiesDashboardWebPart();
    public static final WebPartFactory _requestorSRDashboardFactory = new BioTrustRequestorSampleRequestDashboardWebPart();
    public static final WebPartFactory _requestorStudyDashboardFactory = new BioTrustRequestorStudyRegDashboardWebPart();
    public static final WebPartFactory _requestorOverviewFactory = new BioTrustRequestorOverviewWebPart();
    public static final WebPartFactory _requestorContactsFactory = new BioTrustRequestorContactsWebpart();
    public static final WebPartFactory _requestorManageContactsFactory = new BioTrustRequestorManageContactsWebPart();
    public static final String NAME = "BioTrust";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 14.10;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(
            _adminFactory,
            _rcDashboardFactory,
            _rcPendingSampleRequestsFactory,
            _rcIRBInformationFactory,
            _approverDashboardFactory,
            _rcClosedStudiesDashboardFactory,
            _requestorSRDashboardFactory,
            _requestorStudyDashboardFactory,
            _requestorOverviewFactory,
            _requestorContactsFactory,
            _requestorManageContactsFactory
        );
    }

    @Override
    protected void init()
    {
        addController("biotrust", BioTrustController.class);

        DefaultSchema.registerProvider(BioTrustQuerySchema.NAME, new DefaultSchema.SchemaProvider(this)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // check that the biotrust module is enabled and the folder type is RC or Requestor
                return super.isAvailable(schema, module) && schema.getContainer().getFolderType().getName().equals(BioTrustRCFolderType.NAME) || schema.getContainer().getFolderType().getName().equals(SpecimenRequestorFolderType.NAME);
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new BioTrustQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        PropertyService.get().registerDomainKind(new RequestResponsesDomain());
        PropertyService.get().registerDomainKind(new SampleRequestDomain());
        PropertyService.get().registerDomainKind(new TissueRecordDomain());
        PropertyService.get().registerDomainKind(new ParticipantEligibilityDomain());
        PropertyService.get().registerDomainKind(new StudyRegistrationDomain());
        PropertyService.get().registerDomainKind(new ContactDomain());
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new BioTrustContainerListener());

        // folder types
        ModuleLoader.getInstance().registerFolderType(this, new SpecimenRequestorFolderType(this));
        ModuleLoader.getInstance().registerFolderType(this, new BioTrustRCFolderType(this));

        // security roles
        RoleManager.registerRole(new PrincipalInvestigatorRole());
        RoleManager.registerRole(new StudyContactRole());
        RoleManager.registerRole(new BioTrustRCRole());
        RoleManager.registerRole(new FacultyRole());
        RoleManager.registerRole(new SamplePickupRole());
        RoleManager.registerRole(new PrimaryStudyContactRole());
        RoleManager.registerRole(new BillingContactRole());
        RoleManager.registerRole(new BudgetApproverRole());

        // survey listener
        SurveyService.get().addSurveyListener(new BioTrustSurveyListener());

        // audit events
        AuditLogService.get().addAuditViewFactory(BioTrustAuditViewFactory.getInstance());
        AuditLogService.registerAuditType(new BioTrustAuditProvider());

        //add email templates
        EmailTemplateService ets = EmailTemplateService.get();
        ets.registerTemplate(SampleRequestSubmittedEmailTemplate.class);
        ets.registerTemplate(ApprovalReviewEmailTemplate.class);
        ets.registerTemplate(SampleReviewerResponseEmailTemplate.class);
        ets.registerTemplate(StudyRegisteredEmailTemplate.class);
        ets.registerTemplate(InvestigatorFolderCreatedEmailTemplate.class);
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(BioTrustQuerySchema.NAME);
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        //return PageFlowUtil.set(BioTrustSchema.getInstance().getSchema());
        return Collections.emptySet();
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<Class>(Arrays.asList(
                BioTrustManager.TestCase.class,
                BioTrustSecurityManager.TestCase.class,
                BioTrustSampleManager.TestCase.class
        ));
    }
}
