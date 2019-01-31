/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.laboratory;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.AbstractDataProvider;
import org.labkey.api.laboratory.DetailsUrlWithoutLabelNavItem;
import org.labkey.api.laboratory.JSTabbedReportItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.laboratory.QueryImportNavItem;
import org.labkey.api.laboratory.QueryTabbedReportItem;
import org.labkey.api.laboratory.ReportItem;
import org.labkey.api.laboratory.SimpleSettingsItem;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.ldk.table.QueryCache;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/7/12
 * Time: 10:01 AM
 */
public class LaboratoryDataProvider extends AbstractDataProvider
{
    public static final String NAME = "Laboratory";
    private Module _module;

    public LaboratoryDataProvider(Module m)
    {
        _module = m;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public ActionURL getInstructionsUrl(Container c, User u)
    {
        return null;
    }

    @Override
    public List<NavItem> getDataNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    @Override
    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        QueryCache cache = new QueryCache();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            items.add(new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "Samples", LaboratoryService.NavItemCategory.samples, "Samples", cache));
            items.add(new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "DNA_Oligos", LaboratoryService.NavItemCategory.samples, "Samples", cache));
            items.add(new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "Peptides", LaboratoryService.NavItemCategory.samples, "Samples", cache));
            items.add(new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "Antibodies", LaboratoryService.NavItemCategory.samples, "Samples", cache));
        }

        return Collections.unmodifiableList(items);
    }

    @Override
    public List<NavItem> getReportItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        QueryCache cache = new QueryCache();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            String reportCategory = "Samples"; //note, this is how they appear in the reports panel
            QueryImportNavItem owner1 = new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "Samples", LaboratoryService.NavItemCategory.samples, "Samples", cache);
            ReportItem item1 = new ReportItem(this, null, LaboratoryModule.SCHEMA_NAME, "Samples", reportCategory, "View All Samples", cache);
            item1.setOwnerKey(owner1.getPropertyManagerKey());
            items.add(item1);

            DetailsUrlWithoutLabelNavItem item2 = new DetailsUrlWithoutLabelNavItem(this, "Freezer Summary", DetailsURL.fromString("/laboratory/freezerSummary.view", c), LaboratoryService.NavItemCategory.reports, reportCategory);
            item2.setOwnerKey(owner1.getPropertyManagerKey());
            items.add(item2);

            items.add(new ReportItem(this, null, LaboratoryModule.SCHEMA_NAME, "samples_by_subjectid_and_type", reportCategory, "Samples By Subject Id And Type", cache));
            items.add(new ReportItem(this, null, LaboratoryModule.SCHEMA_NAME, "samples_by_subjectid_date_and_type", reportCategory, "Samples By Subject Id, Sample Date And Type", cache));

            QueryImportNavItem owner2 = new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "DNA_Oligos", LaboratoryService.NavItemCategory.samples, reportCategory, cache);
            ReportItem item3 = new ReportItem(this, null, LaboratoryModule.SCHEMA_NAME, "DNA_Oligos", reportCategory, "View All DNA Oligos", cache);
            item3.setOwnerKey(owner2.getPropertyManagerKey());
            items.add(item3);

            QueryImportNavItem owner3 = new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "Peptides", LaboratoryService.NavItemCategory.samples, reportCategory, cache);
            ReportItem item4 = new ReportItem(this, null, LaboratoryModule.SCHEMA_NAME, "Peptides", reportCategory, "View All Peptides", cache);
            item4.setOwnerKey(owner3.getPropertyManagerKey());
            items.add(item4);

            for (Study study : StudyService.get().getAllStudies(c, u))
            {
                items.add(new DetailsUrlWithoutLabelNavItem(this, study.getLabel(), DetailsURL.fromString("/study/begin.view", study.getContainer()), LaboratoryService.NavItemCategory.reports, "Studies"));
            }

//            FileContentService service = FileContentService.get();
//            for (AttachmentDirectory dir : service.getRegisteredDirectories(c))
//            {
//                items.add(new DetailsUrlWithoutLabelNavItem(this, DetailsURL.fromString("/filecontent/begin.view?fileSetName=" + dir.getName(), c), dir.getLabel(), "Filesets"));
//            }

            DetailsUrlWithoutLabelNavItem item5 = new DetailsUrlWithoutLabelNavItem(this, "Data Browser", DetailsURL.fromString("/laboratory/dataBrowser.view", c), LaboratoryService.NavItemCategory.reports, "General");
            items.add(item5);

            QueryImportNavItem owner4 = new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "Antibodies", LaboratoryService.NavItemCategory.samples, reportCategory, cache);
            ReportItem item6 = new ReportItem(this, null, LaboratoryModule.SCHEMA_NAME, "Antibodies", reportCategory, "View All Antibodies", cache);
            item6.setOwnerKey(owner4.getPropertyManagerKey());
            items.add(item6);

            DetailsUrlWithoutLabelNavItem owner5 = new DetailsUrlWithoutLabelNavItem(this, "Major Events", new DetailsURL(QueryService.get().urlFor(u, c, QueryAction.executeQuery, LaboratoryModule.SCHEMA_NAME, LaboratorySchema.TABLE_MAJOR_EVENTS)), LaboratoryService.NavItemCategory.misc, "Misc");
            ReportItem item7 = new ReportItem(this, null, LaboratoryModule.SCHEMA_NAME, LaboratorySchema.TABLE_MAJOR_EVENTS, reportCategory, "View All Major Events", cache);
            item7.setOwnerKey(owner5.getPropertyManagerKey());
            items.add(item7);
        }

        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getSettingsItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        String categoryName = "Samples";
        String general = "General Settings";
        String adminSettings = "Site Administration";

        if (ContainerManager.getSharedContainer().equals(c))
        {
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Cell_Type", categoryName, "Allowable Cell Types"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "DNA_Mol_Type", categoryName, "Allowable DNA Molecule Types"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Reference_Peptides", categoryName, "Reference Peptides"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Genders", categoryName, "Allowable Genders"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Geographic_Origins", categoryName, "Allowable Geographic Origins"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Sample_Additive", categoryName, "Allowable Sample Additives"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Species", categoryName, "Allowable Species"));

            if (u.hasSiteAdminPermission())
            {
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Synchronize Assay Fields", DetailsURL.fromString("/laboratory/synchronizeAssayFields.view", ContainerManager.getRoot()), LaboratoryService.NavItemCategory.settings, adminSettings));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Reset Tabs and Webparts", DetailsURL.fromString("/laboratory/resetLaboratoryFolders.view", ContainerManager.getRoot()), LaboratoryService.NavItemCategory.settings, adminSettings));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Initialize Workbooks", DetailsURL.fromString("/laboratory/initWorkbooks.view", ContainerManager.getRoot()), LaboratoryService.NavItemCategory.settings, adminSettings));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Initialize Autoincrementing Tables", DetailsURL.fromString("/laboratory/initContainerIncrementingTable.view", ContainerManager.getRoot()), LaboratoryService.NavItemCategory.settings, adminSettings));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Ensure Indexes Exist", DetailsURL.fromString("/laboratory/ensureIndexes.view", ContainerManager.getRoot()), LaboratoryService.NavItemCategory.settings, adminSettings));
            }
        }
        else
        {
            if (c.getActiveModules().contains(getOwningModule()))
            {
                items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Freezers", categoryName, "Manage Freezers"));

                items.add(new DetailsUrlWithoutLabelNavItem(this, "Control Item Visibility", DetailsURL.fromString("/laboratory/itemVisibility.view", c), LaboratoryService.NavItemCategory.settings, general));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Control Item Default Views", DetailsURL.fromString("/laboratory/itemDefaultViews.view", c), LaboratoryService.NavItemCategory.settings, general));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Customize Data Browser", DetailsURL.fromString("/laboratory/customizeDataBrowser.view", c), LaboratoryService.NavItemCategory.settings, general));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Manage Notifications", DetailsURL.fromString("/ldk/notificationAdmin.view", c), LaboratoryService.NavItemCategory.settings, general));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Set Assay Defaults", DetailsURL.fromString("/laboratory/assayDefaults.view", c), LaboratoryService.NavItemCategory.settings, general));
                items.add(new DetailsUrlWithoutLabelNavItem(this, "Populate Default Values", DetailsURL.fromString("/laboratory/populateInitialValues.view", c), LaboratoryService.NavItemCategory.settings, general));

                items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Sample_Type", "Samples", "Allowable Sample Types"));

                if (u.hasSiteAdminPermission())
                {
                    items.add(new DetailsUrlWithoutLabelNavItem(this, "Synchronize Assay Fields", DetailsURL.fromString("/laboratory/synchronizeAssayFields.view", c), LaboratoryService.NavItemCategory.settings, adminSettings));
                    items.add(new DetailsUrlWithoutLabelNavItem(this, "Reset Tabs and Webparts", DetailsURL.fromString("/laboratory/resetLaboratoryFolders.view", c), LaboratoryService.NavItemCategory.settings, adminSettings));
                    items.add(new DetailsUrlWithoutLabelNavItem(this, "Initialize Workbooks", DetailsURL.fromString("/laboratory/initWorkbooks.view", c), LaboratoryService.NavItemCategory.settings, adminSettings));
                }
            }
        }

        //these are always available
        if (c.getActiveModules().contains(getOwningModule()))
        {
            String name = "Manage " + (ContainerManager.getSharedContainer().equals(c) ? "Default " : "") + "Data and Demographics Sources";
            items.add(new DetailsUrlWithoutLabelNavItem(this, name, DetailsURL.fromString("/laboratory/manageDataSources.view", c), LaboratoryService.NavItemCategory.settings, general));
        }

        return Collections.unmodifiableList(items);
    }

    @Override
    public List<NavItem> getMiscItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            items.add(new DetailsUrlWithoutLabelNavItem(this, "Manage Subjects and Groups", DetailsURL.fromString("/laboratory/manageSubjects.view", c), LaboratoryService.NavItemCategory.misc, "Subjects and Projects"));
            items.add(new DetailsUrlWithoutLabelNavItem(this, "Major Events", new DetailsURL(QueryService.get().urlFor(u, c, QueryAction.executeQuery, LaboratoryModule.SCHEMA_NAME, LaboratorySchema.TABLE_MAJOR_EVENTS)), LaboratoryService.NavItemCategory.misc, LaboratoryService.NavItemCategory.misc.name()));
        }

        return Collections.unmodifiableList(items);
    }

    @Override
    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        return new JSONObject();
    }

    @Override @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }

    @Override
    public Module getOwningModule()
    {
        return _module;
    }

    @Override
    public List<SummaryNavItem> getSummary(Container c, User u)
    {
        List<SummaryNavItem> items = new ArrayList<>();

        for (NavItem nav : getSampleNavItems(c, u))
        {
            if (nav.isVisible(c, u))
            {
                if (nav.getName().equalsIgnoreCase("Samples"))
                {
                    QueryImportNavItem item = ((QueryImportNavItem)nav);
                    items.add(new SamplesCountNavItem(this, item.getSchema(), item.getQuery(), item.getItemType(), item.getReportCategory(), item.getLabel()));
                }
                else
                {
                    QueryImportNavItem item = ((QueryImportNavItem)nav);
                    items.add(new QueryCountNavItem(this, item.getSchema(), item.getQuery(), item.getItemType(), item.getReportCategory(), item.getLabel()));
                }
            }
        }

        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        List<NavItem> items = new ArrayList<>();

        for (NavItem nav : getSampleNavItems(c, u))
        {
            if (nav.isVisible(c, u) && nav instanceof QueryImportNavItem)
            {
                QueryImportNavItem item = ((QueryImportNavItem)nav);
                UserSchema us = item.getQueryCache().getUserSchema(c, u, item.getSchema());
                if (us != null)
                {
                    TableInfo ti = item.getQueryCache().getTableInfo(c, u, item.getSchema(), item.getQuery());
                    if (ti != null)
                    {
                        ColumnInfo ci = getSubjectColumn(ti);
                        if (ci != null)
                        {
                            QueryCountNavItem qc = new QueryCountNavItem(this, item.getSchema(), item.getQuery(), item.getItemType(), item.getReportCategory(), item.getLabel());
                            qc.setQueryCache(item.getQueryCache());
                            qc.setFilter(new SimpleFilter(ci.getFieldKey(), subjectId));
                            items.add(qc);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableList(items);
    }

    @Override
    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        List<TabbedReportItem> items = new ArrayList<>();
        QueryCache cache = new QueryCache();

        NavItem nav = new QueryImportNavItem(this, LaboratoryModule.SCHEMA_NAME, "Samples", LaboratoryService.NavItemCategory.samples, "Samples", cache);
        TabbedReportItem item = new QueryTabbedReportItem(cache, this, LaboratoryModule.SCHEMA_NAME, "Samples", "Samples", "Samples");
        item.setQueryCache(cache);
        item.setVisible(nav.isVisible(c, u));
        item.setOwnerKey(nav.getPropertyManagerKey());
        items.add(item);


        TabbedReportItem subSummary = new JSTabbedReportItem(this, "subjectSummary", "Subject Summary", "General", "subjectSummary");
        subSummary.setQueryCache(cache);
        items.add(subSummary);

        DetailsUrlWithoutLabelNavItem owner = new DetailsUrlWithoutLabelNavItem(this, "Major Events", new DetailsURL(QueryService.get().urlFor(u, c, QueryAction.executeQuery, LaboratoryModule.SCHEMA_NAME, LaboratorySchema.TABLE_MAJOR_EVENTS)), LaboratoryService.NavItemCategory.misc, LaboratoryService.NavItemCategory.misc.name());
        TabbedReportItem item2 = new QueryTabbedReportItem(cache, this, LaboratoryModule.SCHEMA_NAME, LaboratorySchema.TABLE_MAJOR_EVENTS, "Major Events", "General");
        item2.setQueryCache(cache);
        item2.setVisible(owner.isVisible(c, u));
        item2.setOwnerKey(owner.getPropertyManagerKey());
        items.add(item2);

        return Collections.unmodifiableList(items);
    }
}
