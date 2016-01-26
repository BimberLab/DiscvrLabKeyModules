package org.labkey.laboratory;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.AbstractDataProvider;
import org.labkey.api.laboratory.AbstractUrlNavItem;
import org.labkey.api.laboratory.DetailsUrlWithLabelNavItem;
import org.labkey.api.laboratory.DetailsUrlWithoutLabelNavItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.laboratory.QueryImportNavItem;
import org.labkey.api.laboratory.QueryTabbedReportItem;
import org.labkey.api.laboratory.ReportItem;
import org.labkey.api.laboratory.StaticURLNavItem;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.ldk.table.QueryCache;
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
 * Date: 1/17/13
 * Time: 10:07 PM
 */
public class ExtraDataSourcesDataProvider extends AbstractDataProvider
{
    public static final String NAME = "ExtraDataSources";
    private Module _module;

    public ExtraDataSourcesDataProvider(Module m)
    {
        _module = m;
    }

    public String getName()
    {
        return NAME;
    }

    public ActionURL getInstructionsUrl(Container c, User u)
    {
        return null;
    }

    private List<NavItem> getItems(Container c, User u, LaboratoryService.NavItemCategory itemType)
    {
        QueryCache cache = new QueryCache();
        List<NavItem> items = new ArrayList<>();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();
            Set<AdditionalDataSource> sources = service.getAdditionalDataSources(c, u);
            for (AdditionalDataSource source : sources)
            {
                if (source.getItemType().equals(itemType))
                {
                    if (itemType.equals(LaboratoryService.NavItemCategory.misc))
                    {
                        DetailsUrlWithoutLabelNavItem item = DetailsUrlWithoutLabelNavItem.createForQuery(this, u, source.getTargetContainer(c), source.getSchemaName(), source.getQueryName(), source.getLabel(), itemType, source.getReportCategory());
                        if (source.getTargetContainer(null) != null)
                            item.setTargetContainer(source.getTargetContainer(null));

                        items.add(item);
                    }
                    else if (itemType.equals(LaboratoryService.NavItemCategory.reports))
                    {
                        ReportItem item = new ReportItem(this, source.getTargetContainer(null), source.getSchemaName(), source.getQueryName(), source.getReportCategory(), source.getLabel(), cache);
                        if (source.getTargetContainer(null) != null)
                            item.setTargetContainer(source.getTargetContainer(null));
                        if (source.getSubjectFieldKey() != null)
                            item.setSubjectFieldKey(source.getSubjectFieldKey());
                        if (source.getSampleDateFieldKey() != null)
                            item.setSampleDateFieldKey(source.getSampleDateFieldKey());

                        items.add(item);
                    }
                    else
                    {
                        items.add(new ExtraDataSourceImportNavItem(this, source));
                    }
                }
            }

            Set<URLDataSource> urlSources = service.getURLDataSources(c, u);
            for (URLDataSource urlSource : urlSources)
            {
                if (urlSource.getItemType().equals(itemType))
                {
                    items.add(new StaticURLNavItem(this, urlSource.getLabel(), null, urlSource.getURLString(c), itemType, null));
                }
            }
        }

        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getDataNavItems(Container c, User u)
    {
        return getItems(c, u, LaboratoryService.NavItemCategory.data);
    }

    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        return getItems(c, u, LaboratoryService.NavItemCategory.samples);
    }

    public List<NavItem> getMiscItems(Container c, User u)
    {
        return getItems(c, u, LaboratoryService.NavItemCategory.misc);
    }

    public List<NavItem> getSettingsItems(Container c, User u)
    {
        return getItems(c, u, LaboratoryService.NavItemCategory.settings);
    }

    public List<NavItem> getReportItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        List<NavItem> dataItems = getItems(c, u, LaboratoryService.NavItemCategory.data);
        for (NavItem owner : dataItems)
        {
            if (owner instanceof ExtraDataSourceImportNavItem)
            {
                ExtraDataSourceImportNavItem sq = (ExtraDataSourceImportNavItem)owner;
                ReportItem reportItem = new ReportItem(this, sq.getTargetContainer(null), sq.getSchema(), sq.getQuery(), owner.getReportCategory(), sq.getLabel(), sq.getQueryCache());
                reportItem.setOwnerKey(owner.getPropertyManagerKey());

                reportItem.setTargetContainer(sq.getTargetContainer(c));
                if (sq.getSource().getSubjectFieldKey() != null)
                    reportItem.setSubjectFieldKey(sq.getSource().getSubjectFieldKey());
                if (sq.getSource().getSampleDateFieldKey() != null)
                    reportItem.setSampleDateFieldKey(sq.getSource().getSampleDateFieldKey());

                items.add(reportItem);
            }
        }

        List<NavItem> reportItems = getItems(c, u, LaboratoryService.NavItemCategory.reports);
        if (reportItems != null)
            items.addAll(reportItems);

        return Collections.unmodifiableList(items);
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

        LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();
        Set<AdditionalDataSource> sources = service.getAdditionalDataSources(c, u);
        for (AdditionalDataSource source : sources)
        {
            TableInfo ti = source.getTableInfo(c, u);
            if (ti != null)
            {
                assert ti.getPublicSchemaName() != null;
                assert ti.getPublicName() != null;
                items.add(new QueryCountNavItem(this, ti.getPublicSchemaName(), ti.getPublicName(), source.getItemType(), source.getReportCategory(), source.getLabel()));
            }
        }

        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        List<NavItem> items = new ArrayList<NavItem>();

        LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();
        Set<AdditionalDataSource> sources = service.getAdditionalDataSources(c, u);
        for (AdditionalDataSource source : sources)
        {
            TableInfo ti = source.getTableInfo(c, u);
            if (ti != null)
            {
                ColumnInfo ci = getSubjectColumn(ti);
                if (ci != null)
                {
                    QueryCountNavItem item = new QueryCountNavItem(this, ti.getSchema().getName(), ti.getName(), source.getItemType(), source.getReportCategory(), source.getLabel());
                    item.setFilter(new SimpleFilter(ci.getFieldKey(), subjectId));
                    items.add(item);
                }
            }
        }

        return Collections.unmodifiableList(items);
    }

    @Override
    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        List<TabbedReportItem> items = new ArrayList<>();
        List<NavItem> reportItems = getReportItems(c, u);
        for (NavItem owner : reportItems)
        {
            if (owner instanceof ReportItem)
            {
                ReportItem sq = (ReportItem)owner;
                QueryCache cache = ((ReportItem) owner).getQueryCache();
                TabbedReportItem reportItem = new QueryTabbedReportItem(cache, this, sq.getSchema(), sq.getQuery(), sq.getLabel(), owner.getReportCategory());
                if (sq.getTargetContainer(c) != null)
                    reportItem.setTargetContainer(sq.getTargetContainer(c));
                if (sq.getSubjectFieldKey() != null)
                    reportItem.setSubjectIdFieldKey(FieldKey.fromString(sq.getSubjectFieldKey()));
                if (sq.getSampleDateFieldKey() != null)
                    reportItem.setSampleDateFieldKey(FieldKey.fromString(sq.getSampleDateFieldKey()));

                reportItem.setOwnerKey(owner.getPropertyManagerKey());
                items.add(reportItem);
            }
        }

//        List<NavItem> tabbedItems = getItems(c, u, LaboratoryService.NavItemCategory.tabbedReports);
//        if (tabbedItems != null)
//            items.addAll(tabbedItems);

        return Collections.unmodifiableList(items);
    }
}