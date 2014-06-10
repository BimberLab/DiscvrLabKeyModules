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
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.biotrust.security.BioTrustRCRole;
import org.labkey.biotrust.security.BioTrustSecurityManager;
import org.labkey.biotrust.security.FacultyRole;
import org.labkey.biotrust.view.BioTrustRequestorContactsWebpart;
import org.labkey.biotrust.view.BioTrustRequestorOverviewWebPart;

import java.util.Arrays;
import java.util.List;

/**
 * User: klum
 * Date: 1/11/13
 */
public class SpecimenRequestorFolderType extends MultiPortalFolderType
{
    public static final String NAME = "NW BioTrust Specimen Requestor";

    private static final List<FolderTab> PAGES = Arrays.asList(
            new BioTrustFolderTabs.SpecimenRequestorOverviewPage("Overview"),
            new BioTrustFolderTabs.StudyRegistrationsPage("My Studies"),
            new BioTrustFolderTabs.SampleRequestsPage("My Sample Requests"),
            new BioTrustFolderTabs.ContactsPage("Contacts")
        );

    private static final String SURVEY_MODULE = "Survey";

    public SpecimenRequestorFolderType(BioTrustModule module)
    {
        super(NAME,
                "A mini web-site where a researcher can manage study registrations, specimen requests, and lab contacts. One per requestor.",
                null,
                Arrays.asList(
                    Portal.getPortalPart(BioTrustRequestorOverviewWebPart.NAME).createWebPart(),
                    Portal.getPortalPart(BioTrustRequestorContactsWebpart.NAME).createWebPart()
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
    public void configureContainer(Container c, User user)
    {
        super.configureContainer(c, user);

        // ensure the rc group exists and give it the folder admin role
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
}
