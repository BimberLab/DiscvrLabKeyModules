package org.labkey.laboratory;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.AbstractDataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.laboratory.QueryTabbedReportItem;
import org.labkey.api.laboratory.ReportItem;
import org.labkey.api.laboratory.SimpleQueryNavItem;
import org.labkey.api.laboratory.SimpleUrlNavItem;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
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
        List<NavItem> items = new ArrayList<NavItem>();
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
                        ActionURL url = QueryService.get().urlFor(u, c, QueryAction.executeQuery, source.getSchemaName(), source.getQueryName());
                        items.add(new SimpleUrlNavItem(this, source.getLabel(), source.getLabel(), new DetailsURL(url), source.getCategory()));
                    }
                    else if (itemType.equals(LaboratoryService.NavItemCategory.reports))
                    {
                        items.add(new ReportItem(this, source.getSchemaName(), source.getQueryName(), source.getCategory()));
                    }
                    else
                    {
                        items.add(new SimpleQueryNavItem(this, source.getSchemaName(), source.getQueryName(), source.getCategory()));
                    }
                }
            }

            Set<URLDataSource> urlSources = service.getURLDataSources(c, u);
            for (URLDataSource urlSource : urlSources)
            {
                if (urlSource.getItemType().equals(itemType))
                {
                    items.add(new SimpleUrlNavItem(this, urlSource.getLabel(), urlSource.getLabel(), urlSource.getURLString(c), urlSource.getLabel()));
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
        List<NavItem> items = new ArrayList<NavItem>();
        List<NavItem> dataItems = getItems(c, u, LaboratoryService.NavItemCategory.data);
        for (NavItem owner : dataItems)
        {
            if (owner instanceof SimpleQueryNavItem)
            {
                SimpleQueryNavItem sq = (SimpleQueryNavItem)owner;
                ReportItem reportItem = new ReportItem(this, sq.getSchema(), sq.getQuery(), owner.getCategory(), sq.getLabel());
                reportItem.setOwnerKey(owner.getPropertyManagerKey());
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
                items.add(new QueryCountNavItem(this, ti.getPublicSchemaName(), ti.getPublicName(), source.getCategory(), source.getLabel()));
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
                    QueryCountNavItem item = new QueryCountNavItem(this, ti.getSchema().getName(), ti.getName(), source.getCategory(), source.getLabel());
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
        List<TabbedReportItem> items = new ArrayList<TabbedReportItem>();

        List<NavItem> reportItems = getItems(c, u, LaboratoryService.NavItemCategory.reports);
        for (NavItem owner : reportItems)
        {
            if (owner instanceof SimpleQueryNavItem)
            {
                SimpleQueryNavItem sq = (SimpleQueryNavItem)owner;
                TabbedReportItem reportItem = new QueryTabbedReportItem(this, sq.getSchema(), sq.getQuery(), sq.getLabel(), owner.getCategory());
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