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
import org.labkey.api.laboratory.DetailsUrlWithoutLabelNavItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.laboratory.QueryTabbedReportItem;
import org.labkey.api.laboratory.SimpleSettingsItem;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.ldk.table.QueryCache;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.AbstractSequenceDataProvider;
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
public class SequenceProvider extends AbstractSequenceDataProvider
{
    public static final String NAME = "Sequence";
    private final Module _module;

    public SequenceProvider(Module m)
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
        //NOTE: we return the item regardless of whether the module is enabled, but the default visibility will be false without it
        //also, if that item is activated this module will be turned on
        List<NavItem> items = new ArrayList<>();
        items.add(new SequenceNavItem(this, LaboratoryService.NavItemCategory.data));
        return items;
    }

    @Override
    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    @Override
    public List<NavItem> getSettingsItems(Container c, User u)
    {
        if (!c.isRoot() && !ContainerManager.getSharedContainer().equals(c) && !c.getActiveModules().contains(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class)))
        {
            return Collections.emptyList();
        }

        String categoryName = "Sequence";
        List<NavItem> items = new ArrayList<>();
        if (ContainerManager.getSharedContainer().equals(c))
        {
            items.add(new DetailsUrlWithoutLabelNavItem(this, "DISCVR-Seq Admin", DetailsURL.fromString("/sequenceAnalysis/siteAdmin.view", ContainerManager.getRoot()), LaboratoryService.NavItemCategory.settings, "DISCVR-Seq"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Barcodes", categoryName, "Allowable Barcodes"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "DNA_Adapters", categoryName, "DNA Adapter Sequences"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Sequence_Applications", categoryName, "Sequence Applications"));
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "Sequence_Chemistries", categoryName, "Sequence Chemistries"));

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
            items.add(new SimpleSettingsItem(this, "sequenceanalysis", "library_types", categoryName, "Library Types"));

            items.add(new DetailsUrlWithoutLabelNavItem(this, "Find Orphan Sequence Files", DetailsURL.fromString("/sequenceAnalysis/findOrphanFiles.view", c), LaboratoryService.NavItemCategory.settings, categoryName));
            items.add(new DetailsUrlWithoutLabelNavItem(this, "Change Sequence Import Defaults", DetailsURL.fromString("/sequenceAnalysis/sequenceDefaults.view", c), LaboratoryService.NavItemCategory.settings, categoryName));
        }

        return items;
    }

    @Override
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

    @Override
    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        return new JSONObject();
    }

    @Override
    @NotNull
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

        NavItem nav = new SequenceNavItem(this, LaboratoryService.NavItemCategory.reports);
        if (nav.isVisible(c, u))
        {
            items.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Readsets"));
            items.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Analysis Runs"));
            items.add(new SequenceOutputsNavItem(this));
            items.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSIS_SETS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "File Sets"));
        }

        return items;
    }

    @Override
    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        List<NavItem> items = new ArrayList<>();

        NavItem nav = new SequenceNavItem(this, LaboratoryService.NavItemCategory.tabbedReports);
        if (nav.isVisible(c, u))
        {
            QueryCountNavItem item1 = new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Analysis Runs");
            item1.setFilter(new SimpleFilter(FieldKey.fromString("readset/subjectId"), subjectId));
            items.add(item1);

            QueryCountNavItem item2 = new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Readsets");
            item2.setFilter(new SimpleFilter(FieldKey.fromString("subjectId"), subjectId));
            items.add(item2);

            QueryCountNavItem item3 = new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_OUTPUTFILES, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Sequence Outputs");
            item3.setFilter(new SimpleFilter(FieldKey.fromString("readset/subjectId"), subjectId));
            items.add(item3);
        }

        return items;
    }

    @Override
    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        List<TabbedReportItem> items = new ArrayList<>();

        NavItem owner = getDataNavItems(c, u).get(0);
        String category = "Sequence Data";
        QueryCache cache = new QueryCache();

        TabbedReportItem readsets = new QueryTabbedReportItem(cache, this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, "Sequence Readsets", category);
        readsets.setOwnerKey(owner.getPropertyManagerKey());
        items.add(readsets);

        TabbedReportItem analyses = new QueryTabbedReportItem(cache, this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, "Sequence Analyses", category);
        analyses.setSubjectIdFieldKey(FieldKey.fromString("readset/subjectid"));
        analyses.setSampleDateFieldKey(FieldKey.fromString("readset/sampledate"));
        analyses.setAllProjectsFieldKey(FieldKey.fromString("readset/allProjectsPivot"));
        analyses.setOverlappingProjectsFieldKey(FieldKey.fromString("readset/overlappingProjectsPivot"));
        analyses.setOwnerKey(owner.getPropertyManagerKey());
        items.add(analyses);

        TabbedReportItem outputs = new QueryTabbedReportItem(cache, this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_OUTPUTFILES, "Sequence Outputs", category);
        outputs.setSubjectIdFieldKey(FieldKey.fromString("readset/subjectid"));
        outputs.setSampleDateFieldKey(FieldKey.fromString("readset/sampledate"));
        outputs.setAllProjectsFieldKey(FieldKey.fromString("readset/allProjectsPivot"));
        outputs.setOverlappingProjectsFieldKey(FieldKey.fromString("readset/overlappingProjectsPivot"));
        outputs.setOwnerKey(owner.getPropertyManagerKey());
        items.add(outputs);

        return items;
    }

    @Override
    public List<NavItem> getSequenceNavItems(Container c, User u, SequenceNavItemCategory category)
    {
        List<NavItem> ret = new ArrayList<>();

        if (category == SequenceNavItemCategory.summary)
        {
            ret.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, LaboratoryService.NavItemCategory.data, "Sequence", "Readsets"));
            //ret.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ALIGNMENTS, LaboratoryService.NavItemCategory.data, "Sequence", "Alignments"));
            ret.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, LaboratoryService.NavItemCategory.data, "Sequence", "Analysis Runs"));
            ret.add(new SequenceOutputsNavItem(this));
            //ret.add(new QueryCountNavItem(this, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSIS_SETS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "File Groups"));
        }
        else if (category == SequenceNavItemCategory.references)
        {
            ret.add(DetailsUrlWithoutLabelNavItem.createForQuery(this, u, c, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES, "Reference Sequences", LaboratoryService.NavItemCategory.data, "Sequence"));
            ret.add(DetailsUrlWithoutLabelNavItem.createForQuery(this, u, c, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARIES, "Reference Genomes", LaboratoryService.NavItemCategory.data, "Sequence"));
        }
        else if (category == SequenceNavItemCategory.misc)
        {
            ret.add(DetailsUrlWithoutLabelNavItem.createForQuery(this, u, c, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_INSTRUMENT_RUNS, "Instrument Runs", LaboratoryService.NavItemCategory.misc, "Sequence"));
        }

        return Collections.unmodifiableList(ret);
    }
}