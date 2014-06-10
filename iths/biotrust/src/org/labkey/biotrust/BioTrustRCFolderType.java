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

import org.labkey.api.data.Container;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.security.*;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.biotrust.query.BioTrustDomainKind;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.contacts.ContactDomain;
import org.labkey.biotrust.query.samples.ParticipantEligibilityDomain;
import org.labkey.biotrust.query.samples.SampleRequestDomain;
import org.labkey.biotrust.query.samples.TissueRecordDomain;
import org.labkey.biotrust.query.study.StudyRegistrationDomain;
import org.labkey.biotrust.security.BioTrustRCRole;
import org.labkey.biotrust.security.BioTrustSecurityManager;
import org.labkey.biotrust.security.FacultyRole;
import org.labkey.biotrust.view.BioTrustIRBInformationWebPart;
import org.labkey.biotrust.view.BioTrustPendingSampleRequestsWebPart;

import java.util.Arrays;
import java.util.List;

/**
 * User: klum
 * Date: 1/13/13
 */
public class BioTrustRCFolderType extends MultiPortalFolderType
{
    public static final String NAME = "NW BioTrust Research Coordinator";

    private static final List<FolderTab> PAGES = Arrays.asList(
        new BioTrustFolderTabs.ResearchCoordOverviewPage("Overview"),
        new BioTrustFolderTabs.OpenRequestsPage("Open Studies & Requests"),
        new BioTrustFolderTabs.ApproverReviewPage("Approver Review"),
        new BioTrustFolderTabs.ClosedStudiesPage("Closed Studies"),
        //new BioTrustFolderTabs.ExternalLinksPage("Patient ID", 1),
        //new BioTrustFolderTabs.ExternalLinksPage("Patient Consent", 2),
        //new BioTrustFolderTabs.ExternalLinksPage("Sample Orders", 3),
        new BioTrustFolderTabs.AdminManagePage("Manage")
    );

    public static final String SURVEY_DESIGN_WEB_PART = "Survey Designs";
    public static final String SURVEY_MODULE = "Survey";

    public BioTrustRCFolderType(BioTrustModule module)
    {
        super(NAME,
                "The project root for NW BioTrust research coordinators to manage submitted specimen requests.",
                null,
                Arrays.asList(
                        Portal.getPortalPart(BioTrustPendingSampleRequestsWebPart.NAME).createWebPart(),
                        Portal.getPortalPart(BioTrustIRBInformationWebPart.NAME).createWebPart()
                ),
                getDefaultModuleSet(ModuleLoader.getInstance().getCoreModule(), getModule(SURVEY_MODULE), module),
                module);
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        return PAGES;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public void configureContainer(Container c, User user)
    {
        super.configureContainer(c, user);

        initializeSampleDomains(c, user);

        // ensure the rc group exists and give it the project admin role
        Group rcGroup = BioTrustSecurityManager.get().ensureResearchCoordinatorGroup(c);
        SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(c);
        MutableSecurityPolicy policy = new MutableSecurityPolicy(c, existingPolicy);

        policy.addRoleAssignment(rcGroup, BioTrustRCRole.class);

        Group arGroup = BioTrustSecurityManager.get().ensureApprovalReviewersGroup(c);
        policy.addRoleAssignment(arGroup, FacultyRole.class);

        Group apiGroup = BioTrustSecurityManager.get().ensureLabKeyAPIGroup(c);
        policy.addRoleAssignment(apiGroup, ReaderRole.class);

        SecurityPolicyManager.savePolicy(policy);
    }

    public static void initializeSampleDomains(Container c, User user)
    {
        // ensure required domains are created and initialized
        String studyDomainURI = StudyRegistrationDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME,
                StudyRegistrationDomain.NAMESPACE_PREFIX,
                c, user);
        ensureDomain(c, user, studyDomainURI, BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME);

        String samplesDomainURI = SampleRequestDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME,
                SampleRequestDomain.NAMESPACE_PREFIX,
                c, user);
        ensureDomain(c, user, samplesDomainURI, BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME);

        String tissueDomainURI = TissueRecordDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME,
                TissueRecordDomain.NAMESPACE_PREFIX,
                c, user);
        ensureDomain(c, user, tissueDomainURI, BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

        String eligibilityDomainURI = ParticipantEligibilityDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_TABLE_NAME,
                ParticipantEligibilityDomain.NAMESPACE_PREFIX,
                c, user);
        ensureDomain(c, user, eligibilityDomainURI, BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_TABLE_NAME);

        String contactDomainURI = ContactDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                BioTrustQuerySchema.CONTACT_TABLE_NAME,
                ContactDomain.NAMESPACE_PREFIX,
                c, user);
        ensureDomain(c, user, contactDomainURI, BioTrustQuerySchema.CONTACT_TABLE_NAME);
    }

    public static void deleteSampleDomains(Container c, User user)
    {
        try {
            String studiesDomainURI = StudyRegistrationDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                    BioTrustQuerySchema.STUDY_REGISTRATION_TABLE_NAME,
                    StudyRegistrationDomain.NAMESPACE_PREFIX,
                    c, user);
            Domain domain = PropertyService.get().getDomain(c, studiesDomainURI);
            if (domain != null)
                domain.delete(user);

            String samplesDomainURI = SampleRequestDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                    BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME,
                    SampleRequestDomain.NAMESPACE_PREFIX,
                    c, user);
            domain = PropertyService.get().getDomain(c, samplesDomainURI);
            if (domain != null)
                domain.delete(user);

            String tissueDomainURI = TissueRecordDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                    BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME,
                    TissueRecordDomain.NAMESPACE_PREFIX,
                    c, user);
            domain = PropertyService.get().getDomain(c, tissueDomainURI);
            if (domain != null)
                domain.delete(user);

            String eligibilityDomainURI = ParticipantEligibilityDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                    BioTrustQuerySchema.PARTICIPANT_ELIGIBILITY_TABLE_NAME,
                    ParticipantEligibilityDomain.NAMESPACE_PREFIX,
                    c, user);
            domain = PropertyService.get().getDomain(c, eligibilityDomainURI);
            if (domain != null)
                domain.delete(user);

            String contactDomainURI = ContactDomain.getDomainURI(BioTrustSchema.getInstance().getSchema().getName(),
                    BioTrustQuerySchema.CONTACT_TABLE_NAME,
                    ContactDomain.NAMESPACE_PREFIX,
                    c, user);
            domain = PropertyService.get().getDomain(c, contactDomainURI);
            if (domain != null)
                domain.delete(user);

        }
        catch (DomainNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Domain ensureDomain(Container c, User user, String domainURI, String domainName)
    {
        Domain domain = PropertyService.get().getDomain(c, domainURI);
        if (domain == null)
        {
            try {

                domain = PropertyService.get().createDomain(c, domainURI, domainName);
                domain.save(user);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        if (domain.getDomainKind() instanceof BioTrustDomainKind)
            ((BioTrustDomainKind)domain.getDomainKind()).ensureDomainProperties(domain, user);

        return domain;
    }
}
