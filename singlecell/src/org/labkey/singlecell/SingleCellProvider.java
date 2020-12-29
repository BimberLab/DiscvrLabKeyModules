package org.labkey.singlecell;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.DetailsUrlWithoutLabelNavItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.laboratory.QueryImportNavItem;
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
 * Created by bimber on 6/14/2016.
 */
public class SingleCellProvider extends AbstractSequenceDataProvider
{
    public static final String NAME = "Single Cell";
    private Module _module;

    public SingleCellProvider(Module module)
    {
        _module = module;
    }

    @Override
    public List<NavItem> getSequenceNavItems(Container c, User u, SequenceNavItemCategory category)
    {
        return Collections.emptyList();
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
    public List<NavItem> getMiscItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        if (c.getActiveModules().contains(ModuleLoader.getInstance().getModule(SingleCellModule.class)))
        {
            items.add(new DetailsUrlWithoutLabelNavItem(this, "Export 10x Library Information", DetailsURL.fromString("singlecell/libraryExport.view"), LaboratoryService.NavItemCategory.misc, NAME));
        }

        return items;
    }

    @Override
    public List<NavItem> getDataNavItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        QueryCache cache = new QueryCache();
        if (!c.getActiveModules().contains(getOwningModule()))
        {
            return Collections.emptyList();
        }

        SingleCellBulkImportNavItem item2 = new SingleCellBulkImportNavItem(this, "10x Import 1: Samples/cDNA", LaboratoryService.NavItemCategory.data, NAME, "singlecell/poolImport.view");
        item2.setQueryCache(cache);
        items.add(item2);

        SingleCellBulkImportNavItem item3 = new SingleCellBulkImportNavItem(this, "10x Import 2: Libraries/Readsets", LaboratoryService.NavItemCategory.data, NAME, "singlecell/cDNAImport.view");
        item3.setQueryCache(cache);
        items.add(item3);

        items.add(new QueryImportNavItem(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS, "Single Cell Libraries", LaboratoryService.NavItemCategory.data, NAME, cache){
            @Override
            public ActionURL getImportUrl(Container c, User u)
            {
                return null;
            }
        });

        return Collections.unmodifiableList(items);
    }

    @Override
    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    @Override
    public List<NavItem> getSettingsItems(Container c, User u)
    {
        return List.of(
                new SimpleSettingsItem(this, SingleCellSchema.NAME, "stim_types", NAME, "Peptides/Stims"),
                new SimpleSettingsItem(this, SingleCellSchema.NAME, "assay_types", NAME, "Single Cell Assay Types"),
                new SimpleSettingsItem(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_CITE_SEQ_ANTIBODIES, NAME, "CITE-seq Antibodies"),
                new SimpleSettingsItem(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_CITE_SEQ_PANELS, NAME, "CITE-seq Panels")
        );
    }

    @Override
    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        return null;
    }

    @Override
    public Set<ClientDependency> getClientDependencies()
    {
        return null;
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

        items.add(new QueryCountNavItem(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_SAMPLES, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(),  "Single Cell Samples"));
        items.add(new QueryCountNavItem(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Single Cell Sorts"));
        items.add(new QueryCountNavItem(this, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS, LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name(), "Single Cell cDNA Libraries"));

        return Collections.unmodifiableList(items);
    }

    @Override
    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        return Collections.emptyList();
    }

    @Override
    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        if (!c.getActiveModules().contains(getOwningModule()))
        {
            return Collections.emptyList();
        }

        List<TabbedReportItem> items = new ArrayList<>();

        NavItem owner = getDataNavItems(c, u).get(0);
        String category = NAME;
        QueryCache cache = new QueryCache();

        TabbedReportItem stims = new QueryTabbedReportItem(cache, this, SingleCellSchema.NAME, SingleCellSchema.TABLE_SAMPLES, "Single Cell Samples", category);
        stims.setOwnerKey(owner.getPropertyManagerKey());
        items.add(stims);

        TabbedReportItem sorts = new QueryTabbedReportItem(cache, this, SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS, "Single Cell Sorts", category);
        sorts.setSubjectIdFieldKey(FieldKey.fromString("sampleId/subjectId"));
        sorts.setSampleDateFieldKey(FieldKey.fromString("sampleId/date"));
        sorts.setAllProjectsFieldKey(FieldKey.fromString("sampleId/allProjectsPivot"));
        sorts.setOverlappingProjectsFieldKey(FieldKey.fromString("sampleId/overlappingProjectsPivot"));
        sorts.setOwnerKey(owner.getPropertyManagerKey());
        items.add(sorts);

        TabbedReportItem cdnas = new QueryTabbedReportItem(cache, this, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS, "Single Cell Libraries", category);

        cdnas.setSubjectIdFieldKey(FieldKey.fromString("sortId/sampleId/subjectId"));
        cdnas.setSampleDateFieldKey(FieldKey.fromString("sortId/sampleId/date"));
        cdnas.setAllProjectsFieldKey(FieldKey.fromString("sortId/sampleId/allProjectsPivot"));
        cdnas.setOverlappingProjectsFieldKey(FieldKey.fromString("sortId/sampleId/overlappingProjectsPivot"));
        cdnas.setOwnerKey(owner.getPropertyManagerKey());
        items.add(cdnas);

        return items;
    }
}
