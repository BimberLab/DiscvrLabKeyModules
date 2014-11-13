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
package org.labkey.sequenceanalysis;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.laboratory.AbstractDataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.laboratory.QueryTabbedReportItem;
import org.labkey.api.laboratory.SimpleSettingsItem;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/6/12
 * Time: 1:08 PM
 */
public class SequenceDataProvider extends AbstractDataProvider
{
    public static final String NAME = "Sequence";
    private Module _module;

    public SequenceDataProvider(Module m)
    {
        _module = m;
    }

    public String getName()
    {
        return NAME;
    }

    public ActionURL getInstructionsUrl(Container c, User u)
    {
        return new ActionURL(SequenceAnalysisModule.CONTROLLER_NAME, "instructions", c);
    }

    public List<NavItem> getDataNavItems(Container c, User u)
    {
        //NOTE: we return the item regardless of whether the module is enabled, but the default visibility will be false without it
        //also, if that item is activated this module will be turned on
        List<NavItem> items = new ArrayList<>();
        items.add(new SequenceNavItem(this, LaboratoryService.NavItemCategory.data));
        return items;
    }

    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    public List<NavItem> getSettingsItems(Container c, User u)
    {
        String categoryName = "Sequence";
        List<NavItem> items = new ArrayList<>();
        if (ContainerManager.getSharedContainer().equals(c))
        {
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Barcodes", categoryName, "Allowable Barcodes"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "DNA_Adapters", categoryName, "DNA Adapter Sequences"));

            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Ref_NT_Category", categoryName, "DNA Categories"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "DNA_Loci", categoryName, "DNA Loci"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Sequence_Platforms", categoryName, "Sequence Platforms"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Instruments", categoryName, "Instruments"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Quality_Metrics_Types", categoryName, "Quality Metric Types"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Haplotypes", categoryName, "Haplotype Definitions"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Haplotype_sequences", categoryName, "Haplotype Sequences"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Haplotype_Types", categoryName, "Haplotype Types"));

            String refCategoryName = "Reference Sequences";
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Ref_NT_Sequences", refCategoryName, "Reference NT Sequences"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Ref_AA_Sequences", refCategoryName, "Reference AA Sequences"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Ref_NT_Features", refCategoryName, "Reference NT Features"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Ref_AA_Features", refCategoryName, "Reference AA Features"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Drug_Resistance", refCategoryName, "Resistance Mutations"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Virus_Strains", refCategoryName, "Virus Strains"));

            categoryName = "Illumina";
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "illumina_applications", categoryName, "Illumina Applications"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "illumina_genome_folders", categoryName, "Illumina Genome Folders"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "illumina_sample_kits", categoryName, "Illumina Sample Kits"));
        }
        else
        {
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "readset_status", categoryName, "Readset Status Values"));
        }

        return items;
    }

    public List<NavItem> getReportItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            NavItem owner = getDataNavItems(c, u).get(0);
            SequenceNavItem item = new SequenceNavItem(this, "Browse Sequence Data", LaboratoryService.NavItemCategory.reports, "Sequence");
            item.setOwnerKey(owner.getPropertyManagerKey());
            items.add(item);
        }

        return items;
    }

    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        return new JSONObject();
    }

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }

    public Module getOwningModule()
    {
        return _module;
    }

    public List<SummaryNavItem> getSummary(Container c, User u)
    {
        List<SummaryNavItem> items = new ArrayList<>();

        NavItem nav = new SequenceNavItem(this, LaboratoryService.NavItemCategory.reports);
        if (nav.isVisible(c, u))
        {
            items.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Analyses"));
            items.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Readsets"));
            items.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_OUTPUTFILES, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Outputs"));
        }

        return items;
    }

    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        List<NavItem> items = new ArrayList<>();

        NavItem nav = new SequenceNavItem(this, LaboratoryService.NavItemCategory.tabbedReports);
        if (nav.isVisible(c, u))
        {
            QueryCountNavItem item1 = new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Analyses");
            item1.setFilter(new SimpleFilter(FieldKey.fromString("readset/subjectId"), subjectId));
            items.add(item1);

            QueryCountNavItem item2 = new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Readsets");
            item2.setFilter(new SimpleFilter(FieldKey.fromString("subjectId"), subjectId));
            items.add(item2);
        }

        return items;
    }

    @Override
    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        List<TabbedReportItem> items = new ArrayList<>();

        NavItem owner = getDataNavItems(c, u).get(0);
        String category = "Sequence Data";

        TabbedReportItem readsets = new QueryTabbedReportItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, "Sequence Readsets", category);
        readsets.setOwnerKey(owner.getPropertyManagerKey());
        items.add(readsets);

        TabbedReportItem analyses = new QueryTabbedReportItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, "Sequence Analyses", category);
        analyses.setSubjectIdFieldKey(FieldKey.fromString("readset/subjectid"));
        analyses.setSampleDateFieldKey(FieldKey.fromString("readset/sampledate"));
        analyses.setOwnerKey(owner.getPropertyManagerKey());
        items.add(analyses);

        return items;
    }
}