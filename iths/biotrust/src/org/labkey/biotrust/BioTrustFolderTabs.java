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
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.biotrust.security.UpdateReviewPermission;
import org.labkey.biotrust.security.UpdateWorkflowPermission;
import org.labkey.biotrust.view.BioTrustClosedStudiesDashboardWebPart;
import org.labkey.biotrust.view.BioTrustAdminWebPart;
import org.labkey.biotrust.view.BioTrustApproverReviewDashboardWebPart;
import org.labkey.biotrust.view.BioTrustRequestorManageContactsWebPart;
import org.labkey.biotrust.view.BioTrustRequestorSampleRequestDashboardWebPart;
import org.labkey.biotrust.view.BioTrustRequestorStudyRegDashboardWebPart;
import org.labkey.biotrust.view.BioTrustResearchCoordDashboardWebPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: cnathe
 * Date: 2/18/13
 */
public class BioTrustFolderTabs
{
    public static class SpecimenRequestorOverviewPage extends FolderTab
    {
        public SpecimenRequestorOverviewPage(String caption)
        {
            super(caption);
        }

        @Override
        public ActionURL getURL(Container container, User user)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            return super.isSelectedPage(viewContext);
        }
    }

    public static class SampleRequestsPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.SAMPLE_REQUESTS";

        public SampleRequestsPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            return super.isSelectedPage(viewContext)
                ||"updateSampleRequest".equals(viewContext.getActionURL().getAction());
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(BioTrustRequestorSampleRequestDashboardWebPart.NAME).createWebPart());
            return parts;
        }
    }

    public static class StudyRegistrationsPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.STUDY_REGISTRATIONS";

        public StudyRegistrationsPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            return super.isSelectedPage(viewContext)
                ||"manageDocumentSet".equals(viewContext.getActionURL().getAction())
                ||"survey".equals(viewContext.getActionURL().getController());
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(BioTrustRequestorStudyRegDashboardWebPart.NAME).createWebPart());
            return parts;
        }
    }

    public static class ContactsPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.CONTACTS";

        public ContactsPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(BioTrustRequestorManageContactsWebPart.NAME).createWebPart());
            return parts;
        }
    }

    public static class ResearchCoordOverviewPage extends FolderTab
    {
        public ResearchCoordOverviewPage(String caption)
        {
            super(caption);
        }

        @Override
        public ActionURL getURL(Container container, User user)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            return super.isSelectedPage(viewContext);
        }

        @Override
        public boolean isVisible(Container container, User user)
        {
            return container.hasPermission(getName() + ".isVisible()", user, UpdateWorkflowPermission.class);
        }
    }

    public static class OpenRequestsPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.OPEN_STUDIES_AND_REQUESTS";

        public OpenRequestsPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(BioTrustResearchCoordDashboardWebPart.NAME).createWebPart());
            return parts;
        }

        @Override
        public boolean isVisible(Container container, User user)
        {
            return container.hasPermission(getName() + ".isVisible()", user, UpdateWorkflowPermission.class);
        }
    }

    public static class ApproverReviewPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.APPROVER_REVIEW";

        public ApproverReviewPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(BioTrustApproverReviewDashboardWebPart.NAME).createWebPart());
            return parts;
        }

        @Override
        public boolean isVisible(Container container, User user)
        {
            return !container.hasPermission(getName() + ".isVisible()", user, UpdateReviewPermission.class);
        }
    }

    public static class ClosedStudiesPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.CLOSED_STUDIES";

        public ClosedStudiesPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(BioTrustClosedStudiesDashboardWebPart.NAME).createWebPart());
            return parts;
        }

        @Override
        public boolean isVisible(Container container, User user)
        {
            return container.hasPermission(getName() + ".isVisible()", user, UpdateWorkflowPermission.class);
        }
    }

    public static class ExternalLinksPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.EXTERNAL";

        public ExternalLinksPage(String caption, int index)
        {
            super(PAGE_ID + "_" + index, caption);
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isVisible(Container container, User user)
        {
            return container.hasPermission(getName() + ".isVisible()", user, UpdateWorkflowPermission.class);
        }
    }

    public static class AdminManagePage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "nwbt.MANAGE";

        public AdminManagePage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(BioTrustRCFolderType.SURVEY_DESIGN_WEB_PART).createWebPart());
            parts.add(Portal.getPortalPart(BioTrustAdminWebPart.NAME).createWebPart());
            return parts;
        }

        @Override
        public boolean isVisible(Container container, User user)
        {
            return container.hasPermission(getName() + ".isVisible()", user, AdminPermission.class);
        }
    }
}
